package com.speedtest.view;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.network.ClasificadorQoS.CategoriaQoS;
import com.speedtest.network.ClasificadorQoS.ResultadoDiagnosticoQoS;
import com.speedtest.network.MedidorQoS.ResumenQoS;
import com.speedtest.network.MotorDescargaWorker.ResumenDescarga;
import com.speedtest.network.MotorSubidaWorker.ResumenSubida;
import com.speedtest.view.IndicadorSemicircularQoS.EstadoVisualQoS;
import com.speedtest.view.TacometroVelocidad.ModoFase;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

/**
 * Vista interactiva de supervisión de Telemetría de Red, Instrumentación
 * Virtual y Calidad de Servicio (HU-03, HU-04 y HU-05 / Tareas #22 y #23).
 * Integra el rack de instrumentación en vivo (Tacómetro Mbps + Displays
 * digitales de Ping y Jitter + Indicador Semicircular QoS), las tarjetas
 * semafóricas y la bitácora de serie temporal a 200 ms.
 */
public class PanelMonitoreoQoS extends JPanel {

    // Paleta de colores semafóricos e institucionales
    private static final Color COLOR_FONDO_PANEL = new Color(245, 247, 250);
    private static final Color COLOR_TARJETA_FONDO = Color.WHITE;
    private static final Color COLOR_BORDE_NEUTRO = new Color(210, 216, 224);
    private static final Color COLOR_TEXTO_TITULO = new Color(44, 62, 80);
    private static final Color COLOR_TEXTO_SUB = new Color(108, 117, 125);

    private static final Color COLOR_OK_VERDE = new Color(25, 135, 84);
    private static final Color COLOR_BUENA_AZUL = new Color(13, 110, 253);
    private static final Color COLOR_ALERTA_NARANJA = new Color(230, 126, 34);
    private static final Color COLOR_CRITICO_ROJO = new Color(211, 47, 47);
    private static final Color COLOR_QA_SLATE = new Color(44, 62, 80);
    private static final Color COLOR_BOTON_INACTIVO_BG = new Color(222, 226, 230);
    private static final Color COLOR_BOTON_INACTIVO_FG = new Color(130, 138, 145);

    private static final Color COLOR_CONSOLA_BG = new Color(20, 26, 35);
    private static final Color COLOR_CONSOLA_FG = new Color(166, 226, 46);

    // Controles operativos superiores
    private JSpinner spinnerPaquetesPing;
    private JComboBox<String> comboDuracionThroughput;
    private JButton btnIniciarEnsayoCompleto;
    private JButton btnCancelarEnsayo;
    private JButton btnAutodiagnosticoQA;
    private JProgressBar barraProgresoGlobal;
    private JLabel lblFaseActiva;

    // Rack de Instrumentación Virtual (HU-05 / Tareas #22 y #23 / RF-VIS-01)
    private TacometroVelocidad tacometroVelocidad;
    private DisplayDigitalTelemetria displayPingDigital;
    private DisplayDigitalTelemetria displayJitterDigital;
    private IndicadorSemicircularQoS indicadorSemicircularQoS;

    // 6 Tarjetas de telemetría en tiempo real (2 filas x 3 columnas)
    private JPanel cardDescarga;
    private JLabel lblValorDescarga;
    private JLabel lblDetalleDescarga;

    private JPanel cardSubida;
    private JLabel lblValorSubida;
    private JLabel lblDetalleSubida;

    private JPanel cardLatencia;
    private JLabel lblValorLatencia;
    private JLabel lblDetalleLatencia;

    private JPanel cardJitter;
    private JLabel lblValorJitter;
    private JLabel lblDetalleJitter;

    private JPanel cardPerdida;
    private JLabel lblValorPerdida;
    private JLabel lblDetallePerdida;

    private JPanel cardCalificacionQoS;
    private JLabel lblValorCalificacionQoS;
    private JLabel lblDetalleCalificacionQoS;

    // Consola de serie temporal a 200 ms
    private JTextArea txtBitacoraTelemetria;

    public PanelMonitoreoQoS() {
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setLayout(new BorderLayout(8, 8));
        setBackground(COLOR_FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        new LineBorder(new Color(180, 190, 205), 1, true),
                        " Tablero de Instrumentación Virtual y Adquisición Concurrente (Δt = 200 ms — HU-03 / HU-04 / HU-05) ",
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13),
                        COLOR_TEXTO_TITULO
                ),
                new EmptyBorder(6, 10, 8, 10)
        ));

        // 1. PANEL SUPERIOR: Controles de ejecución y barra de progreso multifase
        JPanel panelSuperior = new JPanel(new BorderLayout(8, 5));
        panelSuperior.setOpaque(false);

        JPanel barraControles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        barraControles.setOpaque(false);

        JLabel lblPkts = new JLabel("Muestras Ping:");
        lblPkts.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        spinnerPaquetesPing = new JSpinner(new SpinnerNumberModel(15, 5, 100, 5));
        spinnerPaquetesPing.setPreferredSize(new Dimension(62, 28));
        spinnerPaquetesPing.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JLabel lblDuracion = new JLabel("Duración Down/Up:");
        lblDuracion.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboDuracionThroughput = new JComboBox<>(new String[]{
            "4 seg (20 muestras / fase)",
            "6 seg (30 muestras / fase - Estándar)",
            "10 seg (50 muestras / fase - Estrés)"
        });
        comboDuracionThroughput.setSelectedIndex(1);
        comboDuracionThroughput.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboDuracionThroughput.setPreferredSize(new Dimension(235, 28));

        // Botones estilizados para conservar su color sólido en Windows 11 Look & Feel
        btnIniciarEnsayoCompleto = new JButton("► Iniciar SpeedTest Completo");
        estilizarBotonSolido(btnIniciarEnsayoCompleto, COLOR_OK_VERDE, new Dimension(215, 32), 12);

        btnCancelarEnsayo = new JButton("■ Detener / Cancelar");
        estilizarBotonSolido(btnCancelarEnsayo, COLOR_CRITICO_ROJO, new Dimension(165, 32), 12);

        btnAutodiagnosticoQA = new JButton("[QA] Verificar Fórmulas (#20)");
        estilizarBotonSolido(btnAutodiagnosticoQA, COLOR_QA_SLATE, new Dimension(205, 32), 11);

        // Estado inicial: listo para iniciar (Cancelar deshabilitado en gris suave)
        configurarEstadoEjecucion(false);

        barraControles.add(lblPkts);
        barraControles.add(spinnerPaquetesPing);
        barraControles.add(lblDuracion);
        barraControles.add(comboDuracionThroughput);
        barraControles.add(btnIniciarEnsayoCompleto);
        barraControles.add(btnCancelarEnsayo);
        barraControles.add(btnAutodiagnosticoQA);

        JPanel panelProgreso = new JPanel(new BorderLayout(10, 0));
        panelProgreso.setOpaque(false);

        lblFaseActiva = new JLabel("Fase actual: EN ESPERA (Seleccione servidor y pulse Iniciar SpeedTest)");
        lblFaseActiva.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblFaseActiva.setForeground(COLOR_TEXTO_TITULO);

        barraProgresoGlobal = new JProgressBar(0, 100);
        barraProgresoGlobal.setValue(0);
        barraProgresoGlobal.setStringPainted(true);
        barraProgresoGlobal.setString("0% - Listo para iniciar");
        barraProgresoGlobal.setFont(new Font("Segoe UI", Font.BOLD, 11));
        barraProgresoGlobal.setPreferredSize(new Dimension(300, 22));
        barraProgresoGlobal.setForeground(COLOR_BUENA_AZUL);

        panelProgreso.add(lblFaseActiva, BorderLayout.WEST);
        panelProgreso.add(barraProgresoGlobal, BorderLayout.CENTER);

        panelSuperior.add(barraControles, BorderLayout.NORTH);
        panelSuperior.add(panelProgreso, BorderLayout.SOUTH);

        // 2. RACK DE INSTRUMENTACIÓN VIRTUAL (Tareas #22 y #23)
        //    Columna 1: Tacómetro Mbps | Columna 2: Displays Digitales Ping + Jitter | Columna 3: Semicírculo QoS
        JPanel panelInstrumentacion = new JPanel(new GridBagLayout());
        panelInstrumentacion.setOpaque(false);
        panelInstrumentacion.setPreferredSize(new Dimension(0, 212));

        tacometroVelocidad = new TacometroVelocidad();
        tacometroVelocidad.setPreferredSize(new Dimension(280, 210));
        tacometroVelocidad.setMinimumSize(new Dimension(225, 185));

        displayPingDigital = new DisplayDigitalTelemetria(
                "PING / LATENCIA (RTT)", "ms", 40.0, 150.0, 200.0);
        displayJitterDigital = new DisplayDigitalTelemetria(
                "FLUCTUACIÓN (JITTER)", "ms", 10.0, 50.0, 60.0);

        JPanel panelDisplaysDigitales = new JPanel(new GridLayout(2, 1, 0, 8));
        panelDisplaysDigitales.setOpaque(false);
        panelDisplaysDigitales.add(displayPingDigital);
        panelDisplaysDigitales.add(displayJitterDigital);

        indicadorSemicircularQoS = new IndicadorSemicircularQoS();
        indicadorSemicircularQoS.setPreferredSize(new Dimension(280, 210));
        indicadorSemicircularQoS.setMinimumSize(new Dimension(220, 185));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        gbc.gridx = 0;
        gbc.weightx = 0.34;
        gbc.insets = new Insets(0, 0, 0, 10);
        panelInstrumentacion.add(tacometroVelocidad, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.32;
        gbc.insets = new Insets(0, 0, 0, 10);
        panelInstrumentacion.add(panelDisplaysDigitales, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.34;
        gbc.insets = new Insets(0, 0, 0, 0);
        panelInstrumentacion.add(indicadorSemicircularQoS, gbc);

        // 3. PANEL DE 6 TARJETAS SEMAFÓRICAS CONSOLIDADAS
        JPanel panelTarjetas = new JPanel(new GridLayout(2, 3, 8, 6));
        panelTarjetas.setOpaque(false);

        lblValorDescarga = crearLabelValorPrincipal("0.00 Mbps");
        lblDetalleDescarga = crearLabelDetalle("Pico: 0.00 Mbps | Recibido: 0.00 MB");
        cardDescarga = construirTarjeta("DESCARGA SOSTENIDA (DOWNLOAD)", lblValorDescarga, lblDetalleDescarga);

        lblValorSubida = crearLabelValorPrincipal("0.00 Mbps");
        lblDetalleSubida = crearLabelDetalle("Pico: 0.00 Mbps | Enviado: 0.00 MB");
        cardSubida = construirTarjeta("SUBIDA SOSTENIDA (UPLOAD)", lblValorSubida, lblDetalleSubida);

        lblValorCalificacionQoS = crearLabelValorPrincipal("SIN EVALUAR");
        lblDetalleCalificacionQoS = crearLabelDetalle("Índice QoS: -- / 100 | SLA: Pendiente");
        cardCalificacionQoS = construirTarjeta("CLASIFICACIÓN GLOBAL QoS (SLA)", lblValorCalificacionQoS, lblDetalleCalificacionQoS);

        lblValorLatencia = crearLabelValorPrincipal("0.00 ms");
        lblDetalleLatencia = crearLabelDetalle("Mín: 0.00 ms | Máx: 0.00 ms");
        cardLatencia = construirTarjeta("LATENCIA MEDIA (PING / RTT)", lblValorLatencia, lblDetalleLatencia);

        lblValorJitter = crearLabelValorPrincipal("0.00 ms");
        lblDetalleJitter = crearLabelDetalle("Variación |RTT_i - RTT_{i-1}|: 0.00 ms");
        cardJitter = construirTarjeta("FLUCTUACIÓN DE FASE (JITTER)", lblValorJitter, lblDetalleJitter);

        lblValorPerdida = crearLabelValorPrincipal("0.0 %");
        lblDetallePerdida = crearLabelDetalle("Env: 0 | Rec: 0 | Perd: 0");
        cardPerdida = construirTarjeta("PÉRDIDA DE PAQUETES (LOSS)", lblValorPerdida, lblDetallePerdida);

        panelTarjetas.add(cardDescarga);
        panelTarjetas.add(cardSubida);
        panelTarjetas.add(cardCalificacionQoS);
        panelTarjetas.add(cardLatencia);
        panelTarjetas.add(cardJitter);
        panelTarjetas.add(cardPerdida);

        JPanel bloqueVisualSuperior = new JPanel(new BorderLayout(0, 8));
        bloqueVisualSuperior.setOpaque(false);
        bloqueVisualSuperior.add(panelInstrumentacion, BorderLayout.NORTH);
        bloqueVisualSuperior.add(panelTarjetas, BorderLayout.CENTER);

        // 4. PANEL INFERIOR: Bitácora en vivo de serie temporal (200 ms)
        JPanel panelBitacora = new JPanel(new BorderLayout(4, 4));
        panelBitacora.setOpaque(false);

        JLabel lblTituloBitacora = new JLabel("Bitácora de Serie Temporal en Vivo (Muestreo Dinámico Δt = 200 ms — Fases PING, DOWN, UP y Veredicto SLA):");
        lblTituloBitacora.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTituloBitacora.setForeground(COLOR_TEXTO_TITULO);

        txtBitacoraTelemetria = new JTextArea(5, 80);
        txtBitacoraTelemetria.setEditable(false);
        txtBitacoraTelemetria.setBackground(COLOR_CONSOLA_BG);
        txtBitacoraTelemetria.setForeground(COLOR_CONSOLA_FG);
        txtBitacoraTelemetria.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtBitacoraTelemetria.setText(
                "========================================================================================\n"
                + " SPEEDTEST DESKTOP - MOTOR CONCURRENTE DE TELEMETRÍA (Δt = 200 ms / 5 Hz)\n"
                + " Presione [► Iniciar SpeedTest Completo] para evaluar Ping, Descarga, Subida y SLA,\n"
                + " o pulse [[QA] Verificar Fórmulas (#20)] para ejecutar el autodiagnóstico matemático.\n"
                + "========================================================================================\n"
        );

        JScrollPane scrollBitacora = new JScrollPane(txtBitacoraTelemetria);
        scrollBitacora.setBorder(new LineBorder(new Color(60, 70, 85), 1));
        scrollBitacora.setPreferredSize(new Dimension(0, 105));

        panelBitacora.add(lblTituloBitacora, BorderLayout.NORTH);
        panelBitacora.add(scrollBitacora, BorderLayout.CENTER);

        JPanel contenedorCentroSur = new JPanel(new BorderLayout(8, 8));
        contenedorCentroSur.setOpaque(false);
        contenedorCentroSur.add(bloqueVisualSuperior, BorderLayout.NORTH);
        contenedorCentroSur.add(panelBitacora, BorderLayout.CENTER);

        add(panelSuperior, BorderLayout.NORTH);
        add(contenedorCentroSur, BorderLayout.CENTER);
    }

    /**
     * Configura un JButton para que en Windows 11 pinte su color de fondo
     * sólido sin ser sobreescrito por el degradado claro del Look & Feel
     * nativo.
     */
    private void estilizarBotonSolido(JButton boton, Color colorFondo, Dimension dimension, int tamanoFuente) {
        boton.setFont(new Font("Segoe UI", Font.BOLD, tamanoFuente));
        boton.setBackground(colorFondo);
        boton.setForeground(Color.WHITE);
        boton.setFocusPainted(false);
        boton.setContentAreaFilled(false);
        boton.setOpaque(true);
        boton.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(colorFondo.darker(), 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));
        boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        boton.setPreferredSize(dimension);
    }

    private void aplicarAparienciaEstadoBoton(JButton boton, boolean habilitado, Color colorActivo) {
        boton.setEnabled(habilitado);
        if (habilitado) {
            boton.setBackground(colorActivo);
            boton.setForeground(Color.WHITE);
            boton.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(colorActivo.darker(), 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));
        } else {
            boton.setBackground(COLOR_BOTON_INACTIVO_BG);
            boton.setForeground(COLOR_BOTON_INACTIVO_FG);
            boton.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(COLOR_BORDE_NEUTRO, 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));
        }
    }

    private JLabel crearLabelValorPrincipal(String textoInicial) {
        JLabel lbl = new JLabel(textoInicial, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lbl.setForeground(COLOR_TEXTO_TITULO);
        return lbl;
    }

    private JLabel crearLabelDetalle(String textoInicial) {
        JLabel lbl = new JLabel(textoInicial, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lbl.setForeground(COLOR_TEXTO_SUB);
        return lbl;
    }

    private JPanel construirTarjeta(String titulo, JLabel lblValor, JLabel lblDetalle) {
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBackground(COLOR_TARJETA_FONDO);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE_NEUTRO, 2, true),
                new EmptyBorder(5, 8, 5, 8)
        ));

        JLabel lblTit = new JLabel(titulo, SwingConstants.CENTER);
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTit.setForeground(COLOR_TEXTO_SUB);

        card.add(lblTit, BorderLayout.NORTH);
        card.add(lblValor, BorderLayout.CENTER);
        card.add(lblDetalle, BorderLayout.SOUTH);
        return card;
    }

    // =========================================================================
    // MÉTODOS DE ENLACE DE EVENTOS Y ACCESO A INSTRUMENTOS
    // =========================================================================
    public void addIniciarSondeoListener(ActionListener listener) {
        btnIniciarEnsayoCompleto.addActionListener(listener);
    }

    public void addCancelarSondeoListener(ActionListener listener) {
        btnCancelarEnsayo.addActionListener(listener);
    }

    public void addAutodiagnosticoQAListener(ActionListener listener) {
        btnAutodiagnosticoQA.addActionListener(listener);
    }

    public int getCantidadPaquetesConfigurada() {
        return (Integer) spinnerPaquetesPing.getValue();
    }

    public int getDuracionThroughputMs() {
        int idx = comboDuracionThroughput.getSelectedIndex();
        if (idx == 0) {
            return 4000;
        }
        if (idx == 2) {
            return 10000;
        }
        return 6000;
    }

    public TacometroVelocidad getTacometroVelocidad() {
        return tacometroVelocidad;
    }

    public DisplayDigitalTelemetria getDisplayPingDigital() {
        return displayPingDigital;
    }

    public DisplayDigitalTelemetria getDisplayJitterDigital() {
        return displayJitterDigital;
    }

    public IndicadorSemicircularQoS getIndicadorSemicircularQoS() {
        return indicadorSemicircularQoS;
    }

    public void configurarEstadoEjecucion(boolean enCurso) {
        spinnerPaquetesPing.setEnabled(!enCurso);
        comboDuracionThroughput.setEnabled(!enCurso);

        aplicarAparienciaEstadoBoton(btnIniciarEnsayoCompleto, !enCurso, COLOR_OK_VERDE);
        aplicarAparienciaEstadoBoton(btnAutodiagnosticoQA, !enCurso, COLOR_QA_SLATE);
        aplicarAparienciaEstadoBoton(btnCancelarEnsayo, enCurso, COLOR_CRITICO_ROJO);
    }

    // =========================================================================
    // MÉTODOS DE ACTUALIZACIÓN VISUAL EN EL EDT (FASES PING, DOWN, UP Y QoS)
    // =========================================================================
    public void prepararNuevoEnsayo(String host, int puerto, int totalPaquetesPing, int duracionFaseMs) {
        // Reiniciar los 3 instrumentos virtuales
        tacometroVelocidad.reiniciar();
        tacometroVelocidad.setModoFase(ModoFase.PING, "Sondeo RTT (" + host + ")");
        displayPingDigital.reiniciar("Sondeando " + host + ":" + puerto + "...");
        displayJitterDigital.reiniciar("Calculando |RTT_i - RTT_{i-1}|...");
        indicadorSemicircularQoS.reiniciar();

        lblValorDescarga.setText("0.00 Mbps");
        lblValorDescarga.setForeground(COLOR_TEXTO_TITULO);
        lblDetalleDescarga.setText("Pico: 0.00 Mbps | Recibido: 0.00 MB");
        actualizarBordeTarjeta(cardDescarga, COLOR_BORDE_NEUTRO);

        lblValorSubida.setText("0.00 Mbps");
        lblValorSubida.setForeground(COLOR_TEXTO_TITULO);
        lblDetalleSubida.setText("Pico: 0.00 Mbps | Enviado: 0.00 MB");
        actualizarBordeTarjeta(cardSubida, COLOR_BORDE_NEUTRO);

        lblValorLatencia.setText("0.00 ms");
        lblValorLatencia.setForeground(COLOR_TEXTO_TITULO);
        lblDetalleLatencia.setText("Mín: 0.00 ms | Máx: 0.00 ms");
        actualizarBordeTarjeta(cardLatencia, COLOR_BORDE_NEUTRO);

        lblValorJitter.setText("0.00 ms");
        lblValorJitter.setForeground(COLOR_TEXTO_TITULO);
        lblDetalleJitter.setText("Variación |RTT_i - RTT_{i-1}|: 0.00 ms");
        actualizarBordeTarjeta(cardJitter, COLOR_BORDE_NEUTRO);

        lblValorPerdida.setText("0.0 %");
        lblValorPerdida.setForeground(COLOR_TEXTO_TITULO);
        lblDetallePerdida.setText("Env: 0 | Rec: 0 | Perd: 0");
        actualizarBordeTarjeta(cardPerdida, COLOR_BORDE_NEUTRO);

        lblValorCalificacionQoS.setText("MIDIENDO...");
        lblValorCalificacionQoS.setForeground(COLOR_BUENA_AZUL);
        lblDetalleCalificacionQoS.setText("Fase 1/3: Sondeando calidad de canal...");
        actualizarBordeTarjeta(cardCalificacionQoS, COLOR_BUENA_AZUL);

        barraProgresoGlobal.setValue(0);
        barraProgresoGlobal.setForeground(COLOR_BUENA_AZUL);
        barraProgresoGlobal.setString("0% - Iniciando Fase 1/3 (Ping / RTT)");
        lblFaseActiva.setText(String.format("Fase 1/3 [PING]: Sondeando %s:%d (%d muestras a 200 ms)...",
                host, puerto, totalPaquetesPing));

        txtBitacoraTelemetria.setText("");
        agregarLineaBitacora(String.format(
                "=== INICIANDO ENSAYO INTEGRAL SPEEDTEST DESKTOP [Destino: %s:%d] ===", host, puerto));
        agregarLineaBitacora(String.format(
                "Configuración -> Muestras Ping: %d | Duración por fase Throughput: %.1f s | Δt = 200 ms",
                totalPaquetesPing, duracionFaseMs / 1000.0));
        agregarLineaBitacora("----------------------------------------------------------------------------------------");
        agregarLineaBitacora(">>> FASE 1/3: SONDEO DE LATENCIA (RTT), JITTER Y PÉRDIDA DE PAQUETES...");
    }

    /**
     * Actualiza en tiempo real los displays digitales de Latencia y Jitter, las
     * tarjetas semafóricas y el subtítulo del tacómetro (Fase 1: PING).
     */
    public void actualizarMuestraEnVivo(MuestraTelemetria muestra, ResumenQoS resumen,
            int porcentajeFase, int totalPaquetes) {
        int progresoGlobal = Math.min(33, Math.round(porcentajeFase * 0.33f));
        barraProgresoGlobal.setValue(progresoGlobal);
        barraProgresoGlobal.setString(String.format("%d%% - Fase 1/3 (Ping %d/%d)",
                progresoGlobal, resumen.getPaquetesEnviados(), totalPaquetes));

        boolean sinRespuestaTotal = (resumen.getPaquetesRecibidos() == 0 && resumen.getPaquetesPerdidos() > 0);

        // 1. Displays digitales dinámicos de Ping y Jitter (Tarea #23)
        if (sinRespuestaTotal) {
            displayPingDigital.marcarSinRespuesta(String.format(Locale.US,
                    "Pérdida: %.1f%% (%d/%d perdidos)",
                    resumen.getPorcentajePerdida(), resumen.getPaquetesPerdidos(), resumen.getPaquetesEnviados()));
            displayJitterDigital.marcarSinRespuesta("Sin ecos válidos para calcular |ΔRTT|");
        } else {
            displayPingDigital.actualizarLectura(
                    resumen.getRttPromedioMs(),
                    String.format(Locale.US, "Mín: %.2f ms | Máx: %.2f ms | Loss: %.1f%%",
                            resumen.getRttMinimoMs(), resumen.getRttMaximoMs(), resumen.getPorcentajePerdida())
            );
            displayJitterDigital.actualizarLectura(
                    resumen.getJitterAcumuladoMs(),
                    String.format(Locale.US, "Último |ΔRTT|: %.2f ms | Rec: %d/%d",
                            resumen.getJitterInstantaneoMs(), resumen.getPaquetesRecibidos(), resumen.getPaquetesEnviados())
            );
        }

        // 2. Estado del tacómetro durante sondeo RTT
        tacometroVelocidad.setModoFase(
                ModoFase.PING,
                String.format(Locale.US, "RTT: %.1f ms | Loss: %.1f%%",
                        resumen.getRttPromedioMs(), resumen.getPorcentajePerdida())
        );

        // 3. Tarjetas de Latencia, Jitter y Packet Loss
        lblValorLatencia.setText(String.format("%.2f ms", resumen.getRttPromedioMs()));
        lblDetalleLatencia.setText(String.format("Mín: %.2f ms | Máx: %.2f ms",
                resumen.getRttMinimoMs(), resumen.getRttMaximoMs()));
        Color colorRtt = sinRespuestaTotal
                ? COLOR_CRITICO_ROJO
                : obtenerColorInverso(resumen.getRttPromedioMs(), 40.0, 80.0, 150.0);
        lblValorLatencia.setForeground(colorRtt);
        actualizarBordeTarjeta(cardLatencia, colorRtt);

        lblValorJitter.setText(String.format("%.2f ms", resumen.getJitterAcumuladoMs()));
        lblDetalleJitter.setText(String.format("Último |ΔRTT|: %.2f ms", resumen.getJitterInstantaneoMs()));
        Color colorJitter = sinRespuestaTotal
                ? COLOR_CRITICO_ROJO
                : obtenerColorInverso(resumen.getJitterAcumuladoMs(), 10.0, 25.0, 50.0);
        lblValorJitter.setForeground(colorJitter);
        actualizarBordeTarjeta(cardJitter, colorJitter);

        lblValorPerdida.setText(String.format("%.1f %%", resumen.getPorcentajePerdida()));
        lblDetallePerdida.setText(String.format("Env: %d | Rec: %d | Perd: %d",
                resumen.getPaquetesEnviados(), resumen.getPaquetesRecibidos(), resumen.getPaquetesPerdidos()));
        Color colorLoss = obtenerColorInverso(resumen.getPorcentajePerdida(), 0.5, 2.0, 5.0);
        lblValorPerdida.setForeground(colorLoss);
        actualizarBordeTarjeta(cardPerdida, colorLoss);

        agregarLineaBitacora("  " + muestra.toString());
    }

    /**
     * Tarea #23 (RF-VIS-01): Actualiza dinámicamente el indicador semicircular
     * de QoS en tiempo real conforme avanzan las fases PING, DOWN y UP.
     */
    public void actualizarIndicadorQoSEnVivo(ResultadoDiagnosticoQoS diagnosticoParcial, String etiquetaFase) {
        if (diagnosticoParcial == null) {
            return;
        }
        String subtitulo = (etiquetaFase != null)
                ? etiquetaFase + (diagnosticoParcial.isCumpleSla() ? " | SLA OK" : " | Alerta SLA")
                : "Evaluando calidad en vivo...";
        indicadorSemicircularQoS.actualizarQoS(
                diagnosticoParcial.getIndiceGlobalQoS(),
                diagnosticoParcial.getCategoria(),
                subtitulo
        );
    }

    /**
     * Anuncia el inicio de la Fase 2 (Descarga a 200 ms).
     */
    public void anunciarInicioFaseDescarga(String host, int puerto) {
        tacometroVelocidad.reiniciarPico();
        tacometroVelocidad.setVelocidadObjetivo(0.0);
        tacometroVelocidad.setModoFase(ModoFase.DESCARGA, "Midiendo Download (200 ms)...");

        lblFaseActiva.setText(String.format("Fase 2/3 [DOWNLOAD]: Recibiendo flujo binario desde %s:%d (Δt = 200 ms)...",
                host, puerto));
        lblDetalleCalificacionQoS.setText("Fase 2/3: Midiendo Throughput de Descarga...");
        barraProgresoGlobal.setValue(34);
        barraProgresoGlobal.setString("34% - Iniciando Fase 2/3 (Descarga)");
        agregarLineaBitacora("----------------------------------------------------------------------------------------");
        agregarLineaBitacora(">>> FASE 2/3: MUESTREO DINÁMICO DE TASA DE DESCARGA (Velocidad = Bytes*8 / Δt*10^6)...");
    }

    /**
     * Actualiza en tiempo real el tacómetro (velocidad instantánea en Mbps) y
     * la tarjeta de Descarga (Fase 2: DOWN).
     */
    public void actualizarMuestraDescargaEnVivo(MuestraTelemetria muestra, ResumenDescarga resumen, int porcentajeFase) {
        int progresoGlobal = 33 + Math.min(33, Math.round(porcentajeFase * 0.33f));
        barraProgresoGlobal.setValue(progresoGlobal);
        barraProgresoGlobal.setString(String.format("%d%% - Fase 2/3 (Descarga: %.2f Mbps)",
                progresoGlobal, muestra.getVelocidadInstMbps()));

        tacometroVelocidad.setVelocidadObjetivo(muestra.getVelocidadInstMbps());

        lblValorDescarga.setText(String.format("%.2f Mbps", resumen.getVelocidadPromedioMbps()));
        double megabytes = resumen.getBytesTotalesRecibidos() / (1024.0 * 1024.0);
        lblDetalleDescarga.setText(String.format("Inst: %.2f Mbps | Pico: %.2f Mbps (%.2f MB)",
                muestra.getVelocidadInstMbps(), resumen.getVelocidadMaximaMbps(), megabytes));

        Color colorDown = obtenerColorDirecto(resumen.getVelocidadPromedioMbps(), 50.0, 20.0, 5.0);
        lblValorDescarga.setForeground(colorDown);
        actualizarBordeTarjeta(cardDescarga, colorDown);

        agregarLineaBitacora("  " + muestra.toString());
    }

    /**
     * Anuncia el inicio de la Fase 3 (Subida a 200 ms).
     */
    public void anunciarInicioFaseSubida(String host, int puerto) {
        tacometroVelocidad.reiniciarPico();
        tacometroVelocidad.setVelocidadObjetivo(0.0);
        tacometroVelocidad.setModoFase(ModoFase.SUBIDA, "Midiendo Upload (200 ms)...");

        lblFaseActiva.setText(String.format("Fase 3/3 [UPLOAD]: Transmitiendo bloques en memoria hacia %s:%d (Δt = 200 ms)...",
                host, puerto));
        lblDetalleCalificacionQoS.setText("Fase 3/3: Midiendo Throughput de Subida...");
        barraProgresoGlobal.setValue(67);
        barraProgresoGlobal.setString("67% - Iniciando Fase 3/3 (Subida)");
        agregarLineaBitacora("----------------------------------------------------------------------------------------");
        agregarLineaBitacora(">>> FASE 3/3: TRANSFERENCIA DE BLOQUES EN MEMORIA PARA TASA DE SUBIDA (Δt = 200 ms)...");
    }

    /**
     * Actualiza en tiempo real el tacómetro (velocidad instantánea en Mbps) y
     * la tarjeta de Subida (Fase 3: UP).
     */
    public void actualizarMuestraSubidaEnVivo(MuestraTelemetria muestra, ResumenSubida resumen, int porcentajeFase) {
        int progresoGlobal = 66 + Math.min(34, Math.round(porcentajeFase * 0.34f));
        barraProgresoGlobal.setValue(progresoGlobal);
        barraProgresoGlobal.setString(String.format("%d%% - Fase 3/3 (Subida: %.2f Mbps)",
                progresoGlobal, muestra.getVelocidadInstMbps()));

        tacometroVelocidad.setVelocidadObjetivo(muestra.getVelocidadInstMbps());

        lblValorSubida.setText(String.format("%.2f Mbps", resumen.getVelocidadPromedioMbps()));
        double megabytes = resumen.getBytesTotalesEnviados() / (1024.0 * 1024.0);
        lblDetalleSubida.setText(String.format("Inst: %.2f Mbps | Pico: %.2f Mbps (%.2f MB)",
                muestra.getVelocidadInstMbps(), resumen.getVelocidadMaximaMbps(), megabytes));

        Color colorUp = obtenerColorDirecto(resumen.getVelocidadPromedioMbps(), 20.0, 10.0, 2.0);
        lblValorSubida.setForeground(colorUp);
        actualizarBordeTarjeta(cardSubida, colorUp);

        agregarLineaBitacora("  " + muestra.toString());
    }

    /**
     * Presenta el dictamen consolidado de la prueba completa (Ping + Down + Up
     * + Clasificación SLA) sincronizando el tacómetro, los displays digitales,
     * el indicador semicircular QoS y las 6 tarjetas.
     */
    public void mostrarResultadoConsolidadoFinal(ResumenQoS resPing,
            ResumenDescarga resDown,
            ResumenSubida resUp,
            ResultadoDiagnosticoQoS diagnosticoQoS,
            boolean cancelado) {
        if (cancelado) {
            tacometroVelocidad.reiniciarPico();
            tacometroVelocidad.setVelocidadObjetivo(0.0);
            tacometroVelocidad.setModoFase(ModoFase.CANCELADO, "Aborto seguro (RF-ADQ-05)");

            indicadorSemicircularQoS.setEstadoEspecial(
                    EstadoVisualQoS.CANCELADO, 0.0, "Ensayo abortado (RF-ADQ-05)");

            lblFaseActiva.setText("Fase actual: ENSAYO CANCELADO POR EL OPERADOR (Sockets cerrados de forma segura)");
            barraProgresoGlobal.setForeground(COLOR_ALERTA_NARANJA);
            barraProgresoGlobal.setString("ABORTADO MANUALMENTE (RF-ADQ-05)");

            lblValorCalificacionQoS.setText("CANCELADO");
            lblValorCalificacionQoS.setForeground(COLOR_ALERTA_NARANJA);
            lblDetalleCalificacionQoS.setText("Aborto seguro ejecutado sin bloqueo del EDT");
            actualizarBordeTarjeta(cardCalificacionQoS, COLOR_ALERTA_NARANJA);

            agregarLineaBitacora("----------------------------------------------------------------------------------------");
            agregarLineaBitacora("[RF-ADQ-05] ENSAYO INTERRUMPIDO MANUALMENTE. Descriptores TCP/HTTP cerrados con éxito.");
            return;
        }

        double downProm = (resDown != null) ? resDown.getVelocidadPromedioMbps() : 0.0;
        double upProm = (resUp != null) ? resUp.getVelocidadPromedioMbps() : 0.0;

        // Refrescar valores consolidados exactos de las tarjetas Down/Up para coincidir al centésimo con la BD
        if (resDown != null) {
            double mbDown = resDown.getBytesTotalesRecibidos() / (1024.0 * 1024.0);
            lblValorDescarga.setText(String.format("%.2f Mbps", downProm));
            lblDetalleDescarga.setText(String.format("Pico: %.2f Mbps | Recibido: %.2f MB",
                    resDown.getVelocidadMaximaMbps(), mbDown));
            Color colorDown = obtenerColorDirecto(downProm, 50.0, 20.0, 5.0);
            lblValorDescarga.setForeground(colorDown);
            actualizarBordeTarjeta(cardDescarga, colorDown);
        }
        if (resUp != null) {
            double mbUp = resUp.getBytesTotalesEnviados() / (1024.0 * 1024.0);
            lblValorSubida.setText(String.format("%.2f Mbps", upProm));
            lblDetalleSubida.setText(String.format("Pico: %.2f Mbps | Enviado: %.2f MB",
                    resUp.getVelocidadMaximaMbps(), mbUp));
            Color colorUp = obtenerColorDirecto(upProm, 20.0, 10.0, 2.0);
            lblValorSubida.setForeground(colorUp);
            actualizarBordeTarjeta(cardSubida, colorUp);
        }

        if (downProm <= 0.0 && upProm <= 0.0) {
            tacometroVelocidad.reiniciarPico();
            tacometroVelocidad.setVelocidadObjetivo(0.0);
            tacometroVelocidad.setModoFase(ModoFase.ERROR, "Sin respuesta del destino");
        } else {
            tacometroVelocidad.reiniciarPico();
            tacometroVelocidad.setVelocidadObjetivo(downProm);
            tacometroVelocidad.setModoFase(
                    ModoFase.COMPLETADO,
                    String.format(Locale.US, "Down %.1f | Up %.1f Mbps", downProm, upProm)
            );
        }

        // Fijar veredicto oficial final en el Indicador Semicircular QoS (Tarea #23)
        indicadorSemicircularQoS.actualizarQoS(
                diagnosticoQoS.getIndiceGlobalQoS(),
                diagnosticoQoS.getCategoria(),
                "SLA: " + (diagnosticoQoS.isCumpleSla() ? "CUMPLE" : "INCUMPLE")
        );

        barraProgresoGlobal.setValue(100);
        Color colorCategoria = obtenerColorPorCategoria(diagnosticoQoS.getCategoria());
        barraProgresoGlobal.setForeground(colorCategoria);
        barraProgresoGlobal.setString(String.format("100%% - COMPLETADO | Calificación QoS: %s (%.1f/100)",
                diagnosticoQoS.getCalificacionTexto().toUpperCase(), diagnosticoQoS.getIndiceGlobalQoS()));

        lblFaseActiva.setText(String.format("Ensayo Finalizado: %s | SLA: %s | %s",
                diagnosticoQoS.getCalificacionTexto().toUpperCase(),
                diagnosticoQoS.isCumpleSla() ? "CUMPLE" : "INCUMPLE",
                diagnosticoQoS.getDiagnosticoTecnico()));

        lblValorCalificacionQoS.setText(diagnosticoQoS.getCalificacionTexto().toUpperCase());
        lblValorCalificacionQoS.setForeground(colorCategoria);
        lblDetalleCalificacionQoS.setText(String.format("Índice: %.1f / 100 | SLA: %s",
                diagnosticoQoS.getIndiceGlobalQoS(),
                diagnosticoQoS.isCumpleSla() ? "CUMPLE" : "INCUMPLE"));
        actualizarBordeTarjeta(cardCalificacionQoS, colorCategoria);

        agregarLineaBitacora("========================================================================================");
        agregarLineaBitacora(" RESUMEN CONSOLIDADO DEL ENSAYO (HU-03 + HU-04):");
        if (resPing != null) {
            agregarLineaBitacora("  • CALIDAD DE CANAL : " + resPing.toString());
        }
        if (resDown != null) {
            agregarLineaBitacora("  • TASA DE DESCARGA : " + resDown.toString());
        }
        if (resUp != null) {
            agregarLineaBitacora("  • TASA DE SUBIDA   : " + resUp.toString());
        }
        agregarLineaBitacora("  • VEREDICTO QoS SLA: " + diagnosticoQoS.toString());
        agregarLineaBitacora("========================================================================================");
    }

    /**
     * Refleja un error crítico de resolución DNS o red en los tres instrumentos
     * virtuales, la barra de progreso y la bitácora.
     */
    public void mostrarErrorRed(String mensajeError) {
        tacometroVelocidad.reiniciarPico();
        tacometroVelocidad.setVelocidadObjetivo(0.0);
        tacometroVelocidad.setModoFase(ModoFase.ERROR, "Fallo de conexión / DNS");

        displayPingDigital.marcarSinRespuesta("Error DNS / Red");
        displayJitterDigital.marcarSinRespuesta("Sin muestras válidas");
        indicadorSemicircularQoS.setEstadoEspecial(
                EstadoVisualQoS.ERROR, 0.0, "Fallo de conexión / DNS");

        lblFaseActiva.setText("Error de Red: " + mensajeError);
        barraProgresoGlobal.setForeground(COLOR_CRITICO_ROJO);
        barraProgresoGlobal.setString("ERROR DE RED");

        lblValorCalificacionQoS.setText("ERROR");
        lblValorCalificacionQoS.setForeground(COLOR_CRITICO_ROJO);
        lblDetalleCalificacionQoS.setText("No fue posible completar el sondeo");
        actualizarBordeTarjeta(cardCalificacionQoS, COLOR_CRITICO_ROJO);

        agregarLineaBitacora("  [ERROR DE RED] " + mensajeError);
    }

    public void agregarLineaBitacora(String linea) {
        txtBitacoraTelemetria.append(linea + "\n");
        txtBitacoraTelemetria.setCaretPosition(txtBitacoraTelemetria.getDocument().getLength());
    }

    public void limpiarBitacora() {
        txtBitacoraTelemetria.setText("");
    }

    private void actualizarBordeTarjeta(JPanel tarjeta, Color colorBorde) {
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(colorBorde, 2, true),
                new EmptyBorder(5, 8, 5, 8)
        ));
    }

    private Color obtenerColorDirecto(double valor, double exc, double bueno, double deg) {
        if (valor >= exc) {
            return COLOR_OK_VERDE;
        }
        if (valor >= bueno) {
            return COLOR_BUENA_AZUL;
        }
        if (valor >= deg) {
            return COLOR_ALERTA_NARANJA;
        }
        return COLOR_CRITICO_ROJO;
    }

    private Color obtenerColorInverso(double valor, double exc, double bueno, double deg) {
        if (valor <= exc) {
            return COLOR_OK_VERDE;
        }
        if (valor <= bueno) {
            return COLOR_BUENA_AZUL;
        }
        if (valor <= deg) {
            return COLOR_ALERTA_NARANJA;
        }
        return COLOR_CRITICO_ROJO;
    }

    private Color obtenerColorPorCategoria(CategoriaQoS cat) {
        if (cat == null) {
            return COLOR_BORDE_NEUTRO;
        }
        switch (cat) {
            case EXCELENTE:
                return COLOR_OK_VERDE;
            case BUENA:
                return COLOR_BUENA_AZUL;
            case DEGRADADA:
                return COLOR_ALERTA_NARANJA;
            case CRITICA:
            default:
                return COLOR_CRITICO_ROJO;
        }
    }
}
