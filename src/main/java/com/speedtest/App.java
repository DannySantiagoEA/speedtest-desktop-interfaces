package com.speedtest;

import com.speedtest.controller.ControladorLogin;
import com.speedtest.db.UsuarioDAO;
import com.speedtest.view.VentanaLogin;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Punto de entrada principal de SpeedTest Desktop. Inicializa el Look & Feel
 * nativo y lanza el módulo de autenticación (HU-01) en el EDT.
 */
public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            VentanaLogin vistaLogin = new VentanaLogin();
            UsuarioDAO usuarioDAO = new UsuarioDAO();
            new ControladorLogin(vistaLogin, usuarioDAO);

            vistaLogin.setVisible(true);
        });
    }
}
