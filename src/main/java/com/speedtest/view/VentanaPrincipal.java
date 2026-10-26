package com.speedtest.view;

import com.speedtest.model.Usuario;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Ventana principal del sistema SpeedTest Desktop. Aloja la barra de sesión
 * (HU-01), el panel selector de servidor (HU-02) y el motor concurrente de
 * monitoreo QoS (HU-03).
 */
public class VentanaPrincipal extends JFrame {

    private final Usuario operadorActivo;
    private PanelSeleccionServidor panelSeleccionServidor;
    private PanelMonitoreoQoS panelMonitoreoQoS;

    public VentanaPrincipal(Usuario operadorActivo) {
        this.operadorActivo = operadorActivo;
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setTitle("SpeedTest Desktop - Panel de Control y Telemetría QoS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1060, 680));
        getContentPane().setLayout(new BorderLayout());

        // 1. Barra superior institucional con datos de sesión (HU-01)
        JPanel barraSuperior = new JPanel(new BorderLayout());
        barraSuperior.setBackground(new Color(24, 43, 73));
        barraSuperior.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        JLabel lblSistema = new JLabel("SPEEDTEST DESKTOP | Monitoreo de Enlace e Instrumentación QoS");
        lblSistema.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblSistema.setForeground(Color.WHITE);

        JLabel lblOperador = new JLabel(
                "Operador Activo: " + operadorActivo.getNombreOperador()
                + " [" + operadorActivo.getRol() + "] | BD: Conectada (MySQL)"
        );
        lblOperador.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblOperador.setForeground(new Color(85, 239, 196));

        barraSuperior.add(lblSistema, BorderLayout.WEST);
        barraSuperior.add(lblOperador, BorderLayout.EAST);

        // 2. Contenedor principal de trabajo
        JPanel contenedorTrabajo = new JPanel(new BorderLayout());
        contenedorTrabajo.setBackground(new Color(245, 247, 250));

        // 2.1 Panel superior de Selección de Servidor (HU-02 / RF-GUI-02)
        panelSeleccionServidor = new PanelSeleccionServidor();
        contenedorTrabajo.add(panelSeleccionServidor, BorderLayout.NORTH);

        // 2.2 Panel central de Monitoreo Concurrente QoS (HU-03 / RF-ADQ-01, 04, 05)
        panelMonitoreoQoS = new PanelMonitoreoQoS();
        contenedorTrabajo.add(panelMonitoreoQoS, BorderLayout.CENTER);

        getContentPane().add(barraSuperior, BorderLayout.NORTH);
        getContentPane().add(contenedorTrabajo, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    public Usuario getOperadorActivo() {
        return operadorActivo;
    }

    public PanelSeleccionServidor getPanelSeleccionServidor() {
        return panelSeleccionServidor;
    }

    public PanelMonitoreoQoS getPanelMonitoreoQoS() {
        return panelMonitoreoQoS;
    }
}
