package com.speedtest.view;

import com.speedtest.model.ServidorDestino;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

/**
 * Tarea #7 (HU-02 / RF-GUI-02): Panel interactivo de selección de servidor de
 * destino. Integra JComboBox con catálogo predeterminado, JTextFields editables
 * para Host/Puerto e indicadores preliminares de ubicación, IP resuelta y Ping
 * exploratorio.
 */
public class PanelSeleccionServidor extends JPanel {

    private JComboBox<ServidorDestino> cmbCatalogoServidores;
    private JTextField txtHost;
    private JTextField txtPuerto;
    private JLabel lblUbicacionValor;
    private JLabel lblTipoEnlaceValor;
    private JLabel lblIpResueltaValor;
    private JLabel lblPingPreliminarValor;
    private JLabel lblEstadoConectividad;
    private JButton btnProbarConectividad;

    public PanelSeleccionServidor() {
        inicializarComponentes();
        cargarCatalogoInicial();
    }

    private void inicializarComponentes() {
        setLayout(new BorderLayout(12, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(15, 18, 10, 18),
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(180, 195, 210), 1),
                        " Configuración de Nodo de Destino y Diagnóstico Preliminar (RF-GUI-02) ",
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13),
                        new Color(24, 43, 73)
                )
        ));

        // =====================================================================
        // 1. FILA SUPERIOR: Selector JComboBox y Campos Editables Host / Puerto
        // =====================================================================
        JPanel panelControles = new JPanel(new GridBagLayout());
        panelControles.setBackground(Color.WHITE);
        panelControles.setBorder(BorderFactory.createEmptyBorder(10, 15, 6, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Etiqueta y Combo de Catálogo
        JLabel lblCatalogo = new JLabel("Servidor de Referencia:");
        lblCatalogo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panelControles.add(lblCatalogo, gbc);

        cmbCatalogoServidores = new JComboBox<>();
        cmbCatalogoServidores.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cmbCatalogoServidores.setPreferredSize(new Dimension(290, 32));
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.45;
        panelControles.add(cmbCatalogoServidores, gbc);

        // Etiqueta y Campo Host / IP
        JLabel lblHost = new JLabel("Host / IP:");
        lblHost.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panelControles.add(lblHost, gbc);

        txtHost = new JTextField(15);
        txtHost.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtHost.setPreferredSize(new Dimension(165, 32));
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 0.35;
        panelControles.add(txtHost, gbc);

        // Etiqueta y Campo Puerto TCP
        JLabel lblPuerto = new JLabel("Puerto:");
        lblPuerto.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panelControles.add(lblPuerto, gbc);

        txtPuerto = new JTextField(5);
        txtPuerto.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtPuerto.setPreferredSize(new Dimension(65, 32));
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.weightx = 0.10;
        panelControles.add(txtPuerto, gbc);

        // Botón de Ping Exploratorio
        btnProbarConectividad = new JButton("Probar Conectividad");
        btnProbarConectividad.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnProbarConectividad.setBackground(new Color(0, 102, 204));
        btnProbarConectividad.setForeground(Color.WHITE);
        btnProbarConectividad.setContentAreaFilled(false);
        btnProbarConectividad.setOpaque(true);
        btnProbarConectividad.setFocusPainted(false);
        btnProbarConectividad.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnProbarConectividad.setPreferredSize(new Dimension(165, 32));
        gbc.gridx = 6;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panelControles.add(btnProbarConectividad, gbc);

        // =====================================================================
        // 2. FILA INFERIOR: Metadatos del Servidor y Resultado del Ping Previo
        // =====================================================================
        JPanel panelInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 6));
        panelInfo.setBackground(new Color(245, 248, 252));
        panelInfo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 228, 235)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        lblUbicacionValor = crearEtiquetaValor("Anycast Global");
        lblTipoEnlaceValor = crearEtiquetaValor("WAN / Internet");
        lblIpResueltaValor = crearEtiquetaValor("Pendiente de sondeo");
        lblPingPreliminarValor = crearEtiquetaValor("-- ms");
        lblPingPreliminarValor.setFont(new Font("Segoe UI", Font.BOLD, 13));

        lblEstadoConectividad = new JLabel("● Sin verificar");
        lblEstadoConectividad.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEstadoConectividad.setForeground(new Color(120, 130, 140));

        panelInfo.add(crearParInfo("Ubicación:", lblUbicacionValor));
        panelInfo.add(crearParInfo("Tipo Enlace:", lblTipoEnlaceValor));
        panelInfo.add(crearParInfo("IP Resuelta:", lblIpResueltaValor));
        panelInfo.add(crearParInfo("Latencia Base (RTT):", lblPingPreliminarValor));
        panelInfo.add(lblEstadoConectividad);

        add(panelControles, BorderLayout.CENTER);
        add(panelInfo, BorderLayout.SOUTH);

        // Escuchador interno para actualizar campos cuando el operador cambie de servidor en el JComboBox
        cmbCatalogoServidores.addActionListener(e -> actualizarCamposDesdeCombo());
    }

    private JLabel crearEtiquetaValor(String textoInicial) {
        JLabel lbl = new JLabel(textoInicial);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(45, 52, 54));
        return lbl;
    }

    private JPanel crearParInfo(String titulo, JLabel lblValor) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel lblTit = new JLabel(titulo);
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTit.setForeground(new Color(90, 102, 115));
        p.add(lblTit);
        p.add(lblValor);
        return p;
    }

    private void cargarCatalogoInicial() {
        List<ServidorDestino> catalogo = ServidorDestino.obtenerCatalogoPredeterminado();
        cmbCatalogoServidores.removeAllItems();
        for (ServidorDestino srv : catalogo) {
            cmbCatalogoServidores.addItem(srv);
        }
        if (!catalogo.isEmpty()) {
            cmbCatalogoServidores.setSelectedIndex(0);
            actualizarCamposDesdeCombo();
        }
    }

    private void actualizarCamposDesdeCombo() {
        ServidorDestino seleccionado = (ServidorDestino) cmbCatalogoServidores.getSelectedItem();
        if (seleccionado == null) {
            return;
        }

        txtHost.setText(seleccionado.getHost());
        txtPuerto.setText(String.valueOf(seleccionado.getPuerto()));
        lblUbicacionValor.setText(seleccionado.getUbicacion());
        lblTipoEnlaceValor.setText(seleccionado.getTipoEnlace());

        // Habilitar edición manual únicamente si el nodo es de tipo Personalizado/Manual
        boolean esEditable = seleccionado.isEditable();
        txtHost.setEditable(esEditable);
        txtPuerto.setEditable(esEditable);
        txtHost.setBackground(esEditable ? Color.WHITE : new Color(238, 242, 246));
        txtPuerto.setBackground(esEditable ? Color.WHITE : new Color(238, 242, 246));

        // Reiniciar indicadores de diagnóstico al cambiar de servidor
        lblIpResueltaValor.setText("Pendiente de sondeo");
        lblPingPreliminarValor.setText("-- ms");
        lblPingPreliminarValor.setForeground(new Color(45, 52, 54));
        lblEstadoConectividad.setText("● Sin verificar");
        lblEstadoConectividad.setForeground(new Color(120, 130, 140));

        if (esEditable) {
            txtHost.requestFocusInWindow();
            txtHost.selectAll();
        }
    }

    // =========================================================================
    // API PÚBLICA DESACOPLADA PARA EL CONTROLADOR (HU-02)
    // =========================================================================
    public ServidorDestino getServidorSeleccionado() {
        return (ServidorDestino) cmbCatalogoServidores.getSelectedItem();
    }

    public String getHostConfigurado() {
        return txtHost.getText().trim();
    }

    public String getPuertoTexto() {
        return txtPuerto.getText().trim();
    }

    public void addProbarConectividadListener(ActionListener listener) {
        btnProbarConectividad.addActionListener(listener);
        txtHost.addActionListener(listener);
        txtPuerto.addActionListener(listener);
    }

    public void setEstadoSondeoEnCurso(boolean enCurso) {
        btnProbarConectividad.setEnabled(!enCurso);
        cmbCatalogoServidores.setEnabled(!enCurso);
        if (enCurso) {
            lblEstadoConectividad.setText("● Sondeando nodo...");
            lblEstadoConectividad.setForeground(new Color(9, 132, 227));
            lblPingPreliminarValor.setText("Calculando...");
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    public void mostrarResultadoPingExitoso(String ipResuelta, long rttMs) {
        lblIpResueltaValor.setText(ipResuelta);
        lblPingPreliminarValor.setText(rttMs + " ms");

        if (rttMs < 80) {
            lblPingPreliminarValor.setForeground(new Color(0, 150, 100));
            lblEstadoConectividad.setText("● Conectividad Óptima");
            lblEstadoConectividad.setForeground(new Color(0, 150, 100));
        } else if (rttMs < 180) {
            lblPingPreliminarValor.setForeground(new Color(211, 84, 0));
            lblEstadoConectividad.setText("● Conectividad Media");
            lblEstadoConectividad.setForeground(new Color(211, 84, 0));
        } else {
            lblPingPreliminarValor.setForeground(new Color(192, 57, 43));
            lblEstadoConectividad.setText("● Latencia Alta");
            lblEstadoConectividad.setForeground(new Color(192, 57, 43));
        }
    }

    public void mostrarResultadoPingFallido(String detalleError) {
        lblIpResueltaValor.setText("No resuelto / Inalcanzable");
        lblPingPreliminarValor.setText("Timeout");
        lblPingPreliminarValor.setForeground(new Color(214, 48, 49));
        lblEstadoConectividad.setText("● " + detalleError);
        lblEstadoConectividad.setForeground(new Color(214, 48, 49));
    }
}
