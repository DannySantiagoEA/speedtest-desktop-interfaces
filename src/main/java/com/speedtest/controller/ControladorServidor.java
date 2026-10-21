package com.speedtest.controller;

import com.speedtest.network.PingTester;
import com.speedtest.network.PingTester.ResultadoPing;
import com.speedtest.view.PanelSeleccionServidor;
import java.util.concurrent.ExecutionException;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * Tarea #9 (HU-02 / RF-GUI-02, RF-ADQ-01): Controlador para la selección de
 * servidor y ejecución del sondeo exploratorio de conectividad (Ping / RTT) en
 * segundo plano. Garantiza que la resolución DNS y el handshake TCP/ICMP nunca
 * bloqueen el EDT.
 */
public class ControladorServidor {

    private final PanelSeleccionServidor panelVista;
    private final PingTester pingTester;

    public ControladorServidor(PanelSeleccionServidor panelVista, PingTester pingTester) {
        this.panelVista = panelVista;
        this.pingTester = pingTester;
        enlazarEventos();
    }

    private void enlazarEventos() {
        this.panelVista.addProbarConectividadListener(e -> iniciarSondeoPreliminar());
    }

    /**
     * Valida los parámetros de red ingresados y lanza el sondeo en un hilo
     * SwingWorker.
     */
    public void iniciarSondeoPreliminar() {
        String host = panelVista.getHostConfigurado();
        String puertoTexto = panelVista.getPuertoTexto();

        // 1. Validación defensiva de Host vacío
        if (host.isEmpty()) {
            panelVista.mostrarResultadoPingFallido("Host o IP vacío");
            JOptionPane.showMessageDialog(
                    panelVista,
                    "Por favor, especifique un nombre de dominio o dirección IP de destino.",
                    "Host Requerido",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // 2. Validación defensiva de Puerto numérico en rango TCP (1 - 65535)
        final int puerto;
        try {
            puerto = Integer.parseInt(puertoTexto);
            if (puerto < 1 || puerto > 65535) {
                throw new NumberFormatException("Fuera de rango");
            }
        } catch (NumberFormatException ex) {
            panelVista.mostrarResultadoPingFallido("Puerto inválido");
            JOptionPane.showMessageDialog(
                    panelVista,
                    "El puerto debe ser un número entero válido entre 1 y 65535 (ej. 80, 443, 53).",
                    "Puerto TCP Inválido",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // 3. Preparar indicadores visuales antes de iniciar el hilo secundario
        panelVista.setEstadoSondeoEnCurso(true);

        // 4. Ejecución asíncrona fuera del Event Dispatch Thread (EDT)
        SwingWorker<ResultadoPing, Void> worker = new SwingWorker<>() {
            @Override
            protected ResultadoPing doInBackground() {
                // Se ejecuta en un hilo Worker de fondo: NO congela la ventana Swing
                return pingTester.ejecutarSondeo(host, puerto);
            }

            @Override
            protected void done() {
                // Regresa automáticamente al EDT para refrescar los JLabels de forma segura
                panelVista.setEstadoSondeoEnCurso(false);
                try {
                    ResultadoPing resultado = get();
                    System.out.println(resultado);

                    if (resultado.isExito()) {
                        panelVista.mostrarResultadoPingExitoso(
                                resultado.getIpResuelta(),
                                resultado.getRttMs()
                        );
                    } else {
                        panelVista.mostrarResultadoPingFallido(resultado.getDetalleDiagnostico());
                        JOptionPane.showMessageDialog(
                                panelVista,
                                "No se pudo establecer conectividad con el nodo seleccionado:\n\n"
                                + "• Host / IP: " + resultado.getHostConsultado() + ":" + resultado.getPuerto() + "\n"
                                + "• IP Resuelta: " + resultado.getIpResuelta() + "\n"
                                + "• Diagnóstico: " + resultado.getDetalleDiagnostico(),
                                "Servidor Inalcanzable / Timeout",
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    panelVista.mostrarResultadoPingFallido("Sondeo interrumpido");
                } catch (ExecutionException ex) {
                    panelVista.mostrarResultadoPingFallido("Error interno de red");
                }
            }
        };

        worker.execute();
    }
}
