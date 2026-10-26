package com.speedtest.controller;

import com.speedtest.db.UsuarioDAO;
import com.speedtest.model.Usuario;
import com.speedtest.network.PingTester;
import com.speedtest.view.VentanaLogin;
import com.speedtest.view.VentanaPrincipal;
import java.awt.Color;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * Controlador de eventos de autenticación (HU-01) y enlace de módulos de red
 * (HU-02, HU-03). Gestiona la validación de campos y ejecuta la consulta JDBC
 * en segundo plano mediante SwingWorker para no bloquear el Event Dispatch
 * Thread (EDT).
 */
public class ControladorLogin {

    private final VentanaLogin vistaLogin;
    private final UsuarioDAO usuarioDAO;

    public ControladorLogin(VentanaLogin vistaLogin, UsuarioDAO usuarioDAO) {
        this.vistaLogin = vistaLogin;
        this.usuarioDAO = usuarioDAO;
        enlazarEventos();
    }

    private void enlazarEventos() {
        this.vistaLogin.addAutenticacionListener(e -> procesarIntentoLogin());
    }

    private void procesarIntentoLogin() {
        String username = vistaLogin.getUsername();
        String password = vistaLogin.getPassword();

        // 1. Validación defensiva de campos vacíos en el EDT
        if (username.isEmpty() || password.isEmpty()) {
            vistaLogin.mostrarEstado("Advertencia: Complete usuario y contraseña.", new Color(214, 48, 49));
            JOptionPane.showMessageDialog(
                    vistaLogin,
                    "Por favor, ingrese tanto el nombre de usuario como la contraseña.",
                    "Campos Requeridos",
                    JOptionPane.WARNING_MESSAGE
            );
            if (username.isEmpty()) {
                vistaLogin.enfocarUsername();
            } else {
                vistaLogin.limpiarPassword();
            }
            return;
        }

        // 2. Preparar interfaz antes de lanzar la consulta en segundo plano
        vistaLogin.setControlesHabilitados(false);
        vistaLogin.mostrarEstado("Verificando credenciales en MySQL...", new Color(9, 132, 227));

        // 3. Ejecución asíncrona fuera del Event Dispatch Thread (EDT)
        SwingWorker<Usuario, Void> worker = new SwingWorker<>() {
            @Override
            protected Usuario doInBackground() throws SQLException {
                return usuarioDAO.autenticar(username, password);
            }

            @Override
            protected void done() {
                vistaLogin.setControlesHabilitados(true);
                try {
                    Usuario usuarioAutenticado = get();

                    if (usuarioAutenticado != null) {
                        vistaLogin.mostrarEstado("¡Acceso concedido! Cargando sistema...", new Color(0, 184, 148));
                        System.out.println("[AUTH OK] Operador autenticado: " + usuarioAutenticado);

                        // Cerrar ventana de Login y abrir la Ventana Principal
                        vistaLogin.dispose();
                        VentanaPrincipal ventanaPrincipal = new VentanaPrincipal(usuarioAutenticado);

                        // Enlazar controlador del panel de Selección de Servidor y Ping Preliminar (HU-02)
                        PingTester pingTester = new PingTester();
                        new ControladorServidor(ventanaPrincipal.getPanelSeleccionServidor(), pingTester);

                        // Enlazar controlador del Motor Concurrente de Latencia, Jitter y Packet Loss (HU-03)
                        new ControladorQoS(
                                ventanaPrincipal.getPanelSeleccionServidor(),
                                ventanaPrincipal.getPanelMonitoreoQoS()
                        );

                        ventanaPrincipal.setVisible(true);
                    } else {
                        vistaLogin.mostrarEstado("Credenciales inválidas. Intente nuevamente.", new Color(214, 48, 49));
                        vistaLogin.limpiarPassword();
                        JOptionPane.showMessageDialog(
                                vistaLogin,
                                "El usuario o la contraseña ingresados son incorrectos.\n"
                                + "Verifique sus credenciales e intente de nuevo.",
                                "Error de Autenticación",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause();
                    vistaLogin.mostrarEstado("Error de conexión con el servidor MySQL.", new Color(214, 48, 49));
                    System.err.println("[ERROR BD] Fallo durante la autenticación: " + causa.getMessage());

                    JOptionPane.showMessageDialog(
                            vistaLogin,
                            "No fue posible establecer conexión con la base de datos MySQL (speedtest_db).\n"
                            + "Verifique que el servicio MySQL en XAMPP se encuentre activo (Start).\n\n"
                            + "Detalle técnico: " + causa.getMessage(),
                            "Servicio de Base de Datos No Disponible",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };

        worker.execute();
    }
}
