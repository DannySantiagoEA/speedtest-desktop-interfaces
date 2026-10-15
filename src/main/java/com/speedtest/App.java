package com.speedtest;

import com.speedtest.db.ConexionBD;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            System.out.println("==================================================");
            System.out.println("  SpeedTest Desktop - Verificación de Conexión BD ");
            System.out.println("==================================================");

            if (ConexionBD.probarConexion()) {
                System.out.println("[OK] ¡Conexión establecida exitosamente con MySQL!");
            } else {
                System.err.println("[FALLO] No se pudo conectar a la base de datos.");
                System.err.println("        Verifica que MySQL esté iniciado en XAMPP y que exista 'speedtest_db'.");
            }
        });
    }
}
