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
 * Ventana principal del sistema SpeedTest Desktop. Recibe la sesión autenticada
 * del operador activo (HU-01) y alojará los módulos de HU-02 a HU-08.
 */
public class VentanaPrincipal extends JFrame {

    private final Usuario operadorActivo;

    public VentanaPrincipal(Usuario operadorActivo) {
        this.operadorActivo = operadorActivo;
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setTitle("SpeedTest Desktop - Panel de Control y Telemetría QoS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 600));
        getContentPane().setLayout(new BorderLayout());

        // Barra superior con información de la sesión autenticada
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

        // Panel central (se poblará en HU-02 con selección de nodos y diagnóstico)
        JPanel panelCentral = new JPanel(new BorderLayout());
        panelCentral.setBackground(new Color(245, 247, 250));
        JLabel lblBienvenida = new JLabel(
                "<html><div style='text-align:center;'>"
                + "<h2>Sesión iniciada correctamente</h2>"
                + "<p>Bienvenido(a), <b>" + operadorActivo.getNombreOperador() + "</b>.</p>"
                + "<p>Conexión JDBC verificada con <code>speedtest_db</code>. Listo para configurar servidor de destino (HU-02).</p>"
                + "</div></html>",
                SwingConstants.CENTER
        );
        lblBienvenida.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panelCentral.add(lblBienvenida, BorderLayout.CENTER);

        getContentPane().add(barraSuperior, BorderLayout.NORTH);
        getContentPane().add(panelCentral, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    public Usuario getOperadorActivo() {
        return operadorActivo;
    }
}
