/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.speedtest;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Punto de entrada principal para SpeedTest Desktop. Inicializa el Look & Feel
 * del sistema y arranca la interfaz dentro del Event Dispatch Thread (EDT).
 */
public class App {

    public static void main(String[] args) {
        // Establecer el aspecto visual nativo del sistema operativo
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // Ejecución segura en el hilo de despacho de eventos
        SwingUtilities.invokeLater(() -> {
            System.out.println("==================================================");
            System.out.println("  SpeedTest Desktop inicializado correctamente.   ");
            System.out.println("==================================================");
            // Aquí se instanciará VentanaLogin y el controlador en el Sprint 1
        });
    }
}
