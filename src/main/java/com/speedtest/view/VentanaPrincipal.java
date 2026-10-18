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
import javax.swing.SwingConstants;

/**
 * Ventana principal del sistema SpeedTest Desktop. Aloja la barra de sesión
 * (HU-01), el panel selector de servidor (HU-02) y el área central de
 * instrumentación y gráficas (HU-03 a HU-08).
 */
public class VentanaPrincipal extends JFrame {

    private final Usuario operadorActivo;
    private PanelSeleccionServidor panelSeleccionServidor;

    public VentanaPrincipal(Usuario operadorActivo) {
        this.operadorActivo = operadorActivo;
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setTitle("SpeedTest Desktop - Panel de Control y Telemetría QoS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1020, 650));
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

        // 2.1 Panel superior de Selección de Servidor (Tarea #7 - HU-02 / RF-GUI-02)
        panelSeleccionServidor = new PanelSeleccionServidor();
        contenedorTrabajo.add(panelSeleccionServidor, BorderLayout.NORTH);

        // 2.2 Área central (Reservada para Tacómetro y Gráficas JFreeChart en Sprints 2 y 3)
        JPanel panelDashboardCentral = new JPanel(new BorderLayout());
        panelDashboardCentral.setBackground(new Color(245, 247, 250));
        panelDashboardCentral.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel lblPlaceholder = new JLabel(
                "<html><div style='text-align:center; color:#576574;'>"
                + "<h3>Panel de Selección de Servidor Activo (HU-02)</h3>"
                + "<p>Seleccione un nodo del catálogo superior o elija <b>Personalizado / Manual</b> para editar el Host/IP y Puerto.</p>"
                + "</div></html>",
                SwingConstants.CENTER
        );
        lblPlaceholder.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panelDashboardCentral.add(lblPlaceholder, BorderLayout.CENTER);

        contenedorTrabajo.add(panelDashboardCentral, BorderLayout.CENTER);

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
}
