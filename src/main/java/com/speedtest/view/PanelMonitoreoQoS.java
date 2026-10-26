package com.speedtest.view;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.network.MedidorQoS.ResumenQoS;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

/**
 * Tarea #14 (HU-03 / RF-ADQ-01, RF-ADQ-04, RF-ADQ-05): Panel interactivo para
 * iniciar y cancelar de forma segura ráfagas concurrentes de Latencia (RTT),
 * Jitter y Pérdida de Paquetes sin bloquear el Event Dispatch Thread (EDT).
 */
public class PanelMonitoreoQoS extends JPanel {

    private JSpinner spnCantidadPaquetes;
    private JButton btnIniciarSondeo;
    private JButton btnCancelarSondeo;
    private JProgressBar barraProgreso;

    private JLabel lblRttPromedioValor;
    private JLabel lblRttMinMaxValor;
    private JLabel lblJitterValor;
    private JLabel lblJitterInstValor;
    private JLabel lblPerdidaValor;
    private JLabel lblConteoPaquetesValor;
    private JLabel lblEstadoEnsayoValor;

    private JTextArea txtBitacoraMuestras;

    public PanelMonitoreoQoS() {
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setLayout(new BorderLayout(12, 12));
        setBackground(new Color(245, 247, 250));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 18, 15, 18),
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(180, 195, 210), 1),
                        " Motor Concurrente de Adquisición de Latencia, Jitter y Pérdida de Paquetes (HU-03) ",
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13),
                        new Color(24, 43, 73)
                )
        ));

        // =====================================================================
        // 1. BARRA SUPERIOR DE CONTROLES (Iniciar / Cancelar / Progreso)
        // =====================================================================
        JPanel panelControles = new JPanel(new BorderLayout(15, 8));
        panelControles.setBackground(Color.WHITE);
        panelControles.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 228, 235), 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        JPanel panelBotonesIzq = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panelBotonesIzq.setOpaque(false);

        JLabel lblRafaga = new JLabel("Muestras por ráfaga (c/200 ms):");
        lblRafaga.setFont(new Font("Segoe UI", Font.BOLD, 12));
        spnCantidadPaquetes = new JSpinner(new SpinnerNumberModel(25, 5, 200, 5));
        spnCantidadPaquetes.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        spnCantidadPaquetes.setPreferredSize(new Dimension(70, 32));

        btnIniciarSondeo = new JButton("▶ Iniciar Sondeo QoS");
        btnIniciarSondeo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnIniciarSondeo.setBackground(new Color(0, 150, 100));
        btnIniciarSondeo.setForeground(Color.WHITE);
        btnIniciarSondeo.setContentAreaFilled(false);
        btnIniciarSondeo.setOpaque(true);
        btnIniciarSondeo.setFocusPainted(false);
        btnIniciarSondeo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnIniciarSondeo.setPreferredSize(new Dimension(175, 34));

        btnCancelarSondeo = new JButton("■ Detener / Cancelar");
        btnCancelarSondeo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnCancelarSondeo.setBackground(new Color(214, 48, 49));
        btnCancelarSondeo.setForeground(Color.WHITE);
        btnCancelarSondeo.setContentAreaFilled(false);
        btnCancelarSondeo.setOpaque(true);
        btnCancelarSondeo.setFocusPainted(false);
        btnCancelarSondeo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelarSondeo.setPreferredSize(new Dimension(175, 34));
        btnCancelarSondeo.setEnabled(false);

        panelBotonesIzq.add(lblRafaga);
        panelBotonesIzq.add(spnCantidadPaquetes);
        panelBotonesIzq.add(btnIniciarSondeo);
        panelBotonesIzq.add(btnCancelarSondeo);

        barraProgreso = new JProgressBar(0, 100);
        barraProgreso.setValue(0);
        barraProgreso.setStringPainted(true);
        barraProgreso.setString("Listo para iniciar muestreo (5 Hz / 200 ms)");
        barraProgreso.setFont(new Font("Segoe UI", Font.BOLD, 12));
        barraProgreso.setPreferredSize(new Dimension(260, 28));

        panelControles.add(panelBotonesIzq, BorderLayout.WEST);
        panelControles.add(barraProgreso, BorderLayout.CENTER);

        // =====================================================================
        // 2. PANEL CENTRAL: 4 TARJETAS DE INSTRUMENTACIÓN EN TIEMPO REAL
        // =====================================================================
        JPanel panelTarjetas = new JPanel(new GridLayout(1, 4, 12, 0));
        panelTarjetas.setOpaque(false);

        lblRttPromedioValor = crearLabelPrincipal("-- ms", new Color(9, 132, 227));
        lblRttMinMaxValor = crearLabelSecundario("Mín: -- ms | Máx: -- ms");
        panelTarjetas.add(crearTarjetaMetrica("LATENCIA MEDIA (PING / RTT)", lblRttPromedioValor, lblRttMinMaxValor));

        lblJitterValor = crearLabelPrincipal("-- ms", new Color(108, 92, 231));
        lblJitterInstValor = crearLabelSecundario("Instantáneo |ΔRTT|: -- ms");
        panelTarjetas.add(crearTarjetaMetrica("FLUCTUACIÓN DE FASE (JITTER)", lblJitterValor, lblJitterInstValor));

        lblPerdidaValor = crearLabelPrincipal("0.0 %", new Color(0, 150, 100));
        lblConteoPaquetesValor = crearLabelSecundario("Env: 0 | Rec: 0 | Perd: 0");
        panelTarjetas.add(crearTarjetaMetrica("PÉRDIDA DE PAQUETES (LOSS)", lblPerdidaValor, lblConteoPaquetesValor));

        lblEstadoEnsayoValor = crearLabelPrincipal("EN ESPERA", new Color(99, 110, 114));
        lblEstadoEnsayoValor.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JLabel lblSubEstado = crearLabelSecundario("Hilo Worker Desacoplado (EDT Libre)");
        panelTarjetas.add(crearTarjetaMetrica("ESTADO DE LA SESIÓN QoS", lblEstadoEnsayoValor, lblSubEstado));

        // =====================================================================
        // 3. PANEL INFERIOR: BITÁCORA EN VIVO DE MUESTRAS (CADA 200 MS)
        // =====================================================================
        JPanel panelBitacora = new JPanel(new BorderLayout(0, 4));
        panelBitacora.setOpaque(false);

        JLabel lblTituloBitacora = new JLabel(" Telemetría de Muestras en Tiempo Real (Secuencia RTT_i y Desviación |RTT_i - RTT_{i-1}|):");
        lblTituloBitacora.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTituloBitacora.setForeground(new Color(45, 52, 54));

        txtBitacoraMuestras = new JTextArea(9, 60);
        txtBitacoraMuestras.setEditable(false);
        txtBitacoraMuestras.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtBitacoraMuestras.setBackground(new Color(30, 39, 46));
        txtBitacoraMuestras.setForeground(new Color(210, 218, 226));
        txtBitacoraMuestras.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        txtBitacoraMuestras.setText(">>> Sistema listo. Seleccione un servidor arriba y pulse 'Iniciar Sondeo QoS'.\n");

        JScrollPane scrollBitacora = new JScrollPane(txtBitacoraMuestras);
        scrollBitacora.setBorder(BorderFactory.createLineBorder(new Color(180, 195, 210), 1));

        panelBitacora.add(lblTituloBitacora, BorderLayout.NORTH);
        panelBitacora.add(scrollBitacora, BorderLayout.CENTER);

        JPanel contenedorCentroSur = new JPanel(new BorderLayout(0, 12));
        contenedorCentroSur.setOpaque(false);
        contenedorCentroSur.add(panelTarjetas, BorderLayout.NORTH);
        contenedorCentroSur.add(panelBitacora, BorderLayout.CENTER);

        add(panelControles, BorderLayout.NORTH);
        add(contenedorCentroSur, BorderLayout.CENTER);
    }

    private JLabel crearLabelPrincipal(String texto, Color color) {
        JLabel lbl = new JLabel(texto, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lbl.setForeground(color);
        return lbl;
    }

    private JLabel crearLabelSecundario(String texto) {
        JLabel lbl = new JLabel(texto, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(99, 110, 114));
        return lbl;
    }

    private JPanel crearTarjetaMetrica(String titulo, JLabel lblValorPrincipal, JLabel lblSubvalor) {
        JPanel tarjeta = new JPanel(new BorderLayout(0, 6));
        tarjeta.setBackground(Color.WHITE);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 224, 232), 1),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)
        ));

        JLabel lblTit = new JLabel(titulo, SwingConstants.CENTER);
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTit.setForeground(new Color(90, 102, 115));

        tarjeta.add(lblTit, BorderLayout.NORTH);
        tarjeta.add(lblValorPrincipal, BorderLayout.CENTER);
        tarjeta.add(lblSubvalor, BorderLayout.SOUTH);
        return tarjeta;
    }

    // =========================================================================
    // API PÚBLICA PARA EL CONTROLADOR DE ADQUISICIÓN QoS (HU-03)
    // =========================================================================
    public int getCantidadPaquetesConfigurada() {
        return (Integer) spnCantidadPaquetes.getValue();
    }

    public void addIniciarSondeoListener(ActionListener listener) {
        btnIniciarSondeo.addActionListener(listener);
    }

    public void addCancelarSondeoListener(ActionListener listener) {
        btnCancelarSondeo.addActionListener(listener);
    }

    public void configurarEstadoEjecucion(boolean ejecutando) {
        btnIniciarSondeo.setEnabled(!ejecutando);
        spnCantidadPaquetes.setEnabled(!ejecutando);
        btnCancelarSondeo.setEnabled(ejecutando);
        btnCancelarSondeo.setBackground(ejecutando ? new Color(214, 48, 49) : new Color(180, 190, 200));
        btnIniciarSondeo.setBackground(!ejecutando ? new Color(0, 150, 100) : new Color(180, 190, 200));
    }

    public void prepararNuevoEnsayo(String host, int puerto, int totalPaquetes) {
        barraProgreso.setValue(0);
        barraProgreso.setString("Iniciando ráfaga (0 / " + totalPaquetes + ")...");
        lblRttPromedioValor.setText("-- ms");
        lblRttMinMaxValor.setText("Mín: -- ms | Máx: -- ms");
        lblJitterValor.setText("-- ms");
        lblJitterInstValor.setText("Instantáneo |ΔRTT|: -- ms");
        lblPerdidaValor.setText("0.0 %");
        lblPerdidaValor.setForeground(new Color(0, 150, 100));
        lblConteoPaquetesValor.setText("Env: 0 | Rec: 0 | Perd: 0");
        lblEstadoEnsayoValor.setText("MUESTREANDO...");
        lblEstadoEnsayoValor.setForeground(new Color(9, 132, 227));

        txtBitacoraMuestras.setText(String.format(
                "=== INICIO SONDEO CONCURRENTE QoS -> Destino: %s:%d (%d paquetes c/200 ms) ===%n",
                host, puerto, totalPaquetes
        ));
    }

    public void actualizarMuestraEnVivo(MuestraTelemetria muestra, ResumenQoS resumen, int porcentaje, int totalPaquetes) {
        barraProgreso.setValue(porcentaje);
        barraProgreso.setString(String.format("Muestreando paquete %d de %d (%d%%)",
                resumen.getPaquetesEnviados(), totalPaquetes, porcentaje));

        lblRttPromedioValor.setText(String.format("%.2f ms", resumen.getRttPromedioMs()));
        lblRttMinMaxValor.setText(String.format("Mín: %.2f ms | Máx: %.2f ms",
                resumen.getRttMinimoMs(), resumen.getRttMaximoMs()));

        lblJitterValor.setText(String.format("%.2f ms", resumen.getJitterAcumuladoMs()));
        lblJitterInstValor.setText(String.format("Instantáneo |ΔRTT|: %.2f ms", resumen.getJitterInstantaneoMs()));

        lblPerdidaValor.setText(String.format("%.1f %%", resumen.getPorcentajePerdida()));
        if (resumen.getPorcentajePerdida() == 0.0) {
            lblPerdidaValor.setForeground(new Color(0, 150, 100));
        } else if (resumen.getPorcentajePerdida() < 10.0) {
            lblPerdidaValor.setForeground(new Color(211, 84, 0));
        } else {
            lblPerdidaValor.setForeground(new Color(214, 48, 49));
        }

        lblConteoPaquetesValor.setText(String.format("Env: %d | Rec: %d | Perd: %d",
                resumen.getPaquetesEnviados(), resumen.getPaquetesRecibidos(), resumen.getPaquetesPerdidos()));

        txtBitacoraMuestras.append("  " + muestra.toString() + "\n");
        txtBitacoraMuestras.setCaretPosition(txtBitacoraMuestras.getDocument().getLength());
    }

    public void mostrarCierreEnsayo(ResumenQoS resumenFinal, boolean cancelado) {
        if (cancelado) {
            lblEstadoEnsayoValor.setText("CANCELADO");
            lblEstadoEnsayoValor.setForeground(new Color(211, 84, 0));
            barraProgreso.setString("Ensayo abortado manualmente por el operador (" + resumenFinal.getPaquetesEnviados() + " muestras)");
            txtBitacoraMuestras.append("================================================================================\n");
            txtBitacoraMuestras.append(">>> [ABORTO CONTROLADO RF-ADQ-05] Sockets cerrados e hilo secundario interrumpido con éxito.\n");
            txtBitacoraMuestras.append(">>> Resumen parcial consolidado: " + resumenFinal.toString() + "\n");
        } else {
            lblEstadoEnsayoValor.setText("FINALIZADO OK");
            lblEstadoEnsayoValor.setForeground(new Color(0, 150, 100));
            barraProgreso.setValue(100);
            barraProgreso.setString("Ráfaga completada exitosamente (100%)");
            txtBitacoraMuestras.append("================================================================================\n");
            txtBitacoraMuestras.append(">>> [ENSAYO COMPLETADO] Resumen Final QoS: " + resumenFinal.toString() + "\n");
        }
        txtBitacoraMuestras.setCaretPosition(txtBitacoraMuestras.getDocument().getLength());
    }
}
