package com.speedtest.view;

import com.speedtest.network.ClasificadorQoS.CategoriaQoS;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Tarea #23 (HU-05 / RF-VIS-01): Indicador semicircular vectorial (180°) de
 * Calidad de Servicio (QoS) en porcentaje (0% - 100%) con codificación
 * cromática (verde, amarillo, rojo) e interpolación suave en el EDT.
 */
public class IndicadorSemicircularQoS extends JPanel {

    public enum EstadoVisualQoS {
        EN_ESPERA("EN ESPERA", new Color(100, 116, 139), new Color(148, 163, 184)),
        EXCELENTE("EXCELENTE", new Color(5, 150, 105), new Color(52, 211, 153)),
        BUENA("BUENA", new Color(22, 163, 74), new Color(74, 222, 128)),
        DEGRADADA("DEGRADADA", new Color(217, 119, 6), new Color(251, 191, 36)),
        CRITICA("CRÍTICA", new Color(220, 38, 38), new Color(248, 113, 113)),
        CANCELADO("CANCELADO", new Color(217, 119, 6), new Color(251, 191, 36)),
        ERROR("ERROR RED", new Color(220, 38, 38), new Color(248, 113, 113));

        private final String etiqueta;
        private final Color colorBase;
        private final Color colorBrillo;

        EstadoVisualQoS(String etiqueta, Color colorBase, Color colorBrillo) {
            this.etiqueta = etiqueta;
            this.colorBase = colorBase;
            this.colorBrillo = colorBrillo;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        public Color getColorBase() {
            return colorBase;
        }

        public Color getColorBrillo() {
            return colorBrillo;
        }
    }

    // Geometría del semicírculo: 180° (izquierda = 0%) hasta 0° (derecha = 100%)
    private static final double ANGULO_INICIO = 180.0;
    private static final double BARRIDO_TOTAL = 180.0;

    // Bandas fijas de referencia cromática SLA (0-44% Rojo, 45-69% Amarillo, 70-100% Verde)
    private static final Color COLOR_ZONA_ROJA = new Color(239, 68, 68, 170);
    private static final Color COLOR_ZONA_AMARILLA = new Color(245, 158, 11, 180);
    private static final Color COLOR_ZONA_VERDE = new Color(16, 185, 129, 180);

    // Paleta de chasis (unificada con TacometroVelocidad y DisplayDigitalTelemetria)
    private static final Color COLOR_TARJETA_FONDO = new Color(15, 23, 42);
    private static final Color COLOR_TARJETA_BORDE = new Color(30, 41, 59);
    private static final Color COLOR_PISTA_ARCO = new Color(30, 41, 59);
    private static final Color COLOR_MARCA = new Color(148, 163, 184);
    private static final Color COLOR_TEXTO_VALOR = new Color(248, 250, 252);
    private static final Color COLOR_TEXTO_SECUNDARIO = new Color(148, 163, 184);
    private static final Color COLOR_AGUJA = new Color(248, 250, 252);
    private static final Color COLOR_AGUJA_SOMBRA = new Color(0, 0, 0, 90);
    private static final Color COLOR_PIVOTE_EXT = new Color(51, 65, 85);

    // Suavizado ~60 FPS
    private static final int INTERVALO_ANIMACION_MS = 16;
    private static final double FACTOR_SUAVIZADO = 0.16;
    private static final double EPSILON_PORCENTAJE = 0.08;

    // Estado dinámico
    private double porcentajeObjetivo = 0.0;
    private double porcentajeMostrado = 0.0;
    private boolean enEspera = true;
    private EstadoVisualQoS estadoVisual = EstadoVisualQoS.EN_ESPERA;
    private String subtituloSla = "SLA: Pendiente de evaluación";

    private final Timer timerAnimacion;

    public IndicadorSemicircularQoS() {
        setOpaque(false);
        setDoubleBuffered(true);
        setPreferredSize(new Dimension(280, 212));
        setMinimumSize(new Dimension(215, 185));

        this.timerAnimacion = new Timer(INTERVALO_ANIMACION_MS, e -> avanzarInterpolacion());
        this.timerAnimacion.setCoalesce(true);
    }

    // =========================================================================
    // API PÚBLICA MVC
    // =========================================================================
    /**
     * Actualiza el porcentaje de calidad de enlace (0 - 100%) y la categoría
     * oficial proveniente de ClasificadorQoS.
     */
    public void actualizarQoS(double porcentaje, CategoriaQoS categoria, String subtitulo) {
        final double pctSeguro = Math.max(0.0, Math.min(100.0,
                (Double.isNaN(porcentaje) || Double.isInfinite(porcentaje)) ? 0.0 : porcentaje));
        final EstadoVisualQoS nuevoEstado = mapearCategoria(categoria, pctSeguro);

        ejecutarEnEDT(() -> {
            this.enEspera = false;
            this.porcentajeObjetivo = pctSeguro;
            this.estadoVisual = nuevoEstado;
            if (subtitulo != null) {
                this.subtituloSla = subtitulo;
            }
            iniciarAnimacionSiNecesario();
            repaint();
        });
    }

    /**
     * Actualiza el porcentaje con un estado visual explícito (ej. CANCELADO o
     * ERROR).
     */
    public void setEstadoEspecial(EstadoVisualQoS estado, double porcentaje, String subtitulo) {
        final double pctSeguro = Math.max(0.0, Math.min(100.0, porcentaje));
        final EstadoVisualQoS estadoSeguro = (estado != null) ? estado : EstadoVisualQoS.EN_ESPERA;

        ejecutarEnEDT(() -> {
            this.enEspera = (estadoSeguro == EstadoVisualQoS.EN_ESPERA);
            this.porcentajeObjetivo = pctSeguro;
            this.estadoVisual = estadoSeguro;
            if (subtitulo != null) {
                this.subtituloSla = subtitulo;
            }
            iniciarAnimacionSiNecesario();
            repaint();
        });
    }

    /**
     * Reinicia el semicírculo suavemente a 0% en modo EN ESPERA.
     */
    public void reiniciar() {
        ejecutarEnEDT(() -> {
            this.enEspera = true;
            this.porcentajeObjetivo = 0.0;
            this.estadoVisual = EstadoVisualQoS.EN_ESPERA;
            this.subtituloSla = "SLA: Evaluando enlace...";
            iniciarAnimacionSiNecesario();
            repaint();
        });
    }

    public void detenerAnimacion() {
        if (timerAnimacion.isRunning()) {
            timerAnimacion.stop();
        }
    }

    public double getPorcentajeObjetivo() {
        return porcentajeObjetivo;
    }

    public EstadoVisualQoS getEstadoVisual() {
        return estadoVisual;
    }

    // =========================================================================
    // MOTOR DE INTERPOLACIÓN Y MAPEO SLA
    // =========================================================================
    private EstadoVisualQoS mapearCategoria(CategoriaQoS categoria, double pct) {
        if (categoria != null) {
            switch (categoria) {
                case EXCELENTE:
                    return EstadoVisualQoS.EXCELENTE;
                case BUENA:
                    return EstadoVisualQoS.BUENA;
                case DEGRADADA:
                    return EstadoVisualQoS.DEGRADADA;
                case CRITICA:
                default:
                    return EstadoVisualQoS.CRITICA;
            }
        }
        if (pct >= 85.0) {
            return EstadoVisualQoS.EXCELENTE;
        }
        if (pct >= 70.0) {
            return EstadoVisualQoS.BUENA;
        }
        if (pct >= 45.0) {
            return EstadoVisualQoS.DEGRADADA;
        }
        return EstadoVisualQoS.CRITICA;
    }

    private void iniciarAnimacionSiNecesario() {
        if (!timerAnimacion.isRunning()) {
            timerAnimacion.start();
        }
    }

    private void avanzarInterpolacion() {
        double delta = porcentajeObjetivo - porcentajeMostrado;
        if (Math.abs(delta) < EPSILON_PORCENTAJE) {
            porcentajeMostrado = porcentajeObjetivo;
            timerAnimacion.stop();
        } else {
            porcentajeMostrado += delta * FACTOR_SUAVIZADO;
        }
        repaint();
    }

    private void ejecutarEnEDT(Runnable accion) {
        if (SwingUtilities.isEventDispatchThread()) {
            accion.run();
        } else {
            SwingUtilities.invokeLater(accion);
        }
    }

    @Override
    public void removeNotify() {
        detenerAnimacion();
        super.removeNotify();
    }

    // =========================================================================
    // RENDERIZADO VECTORIAL SEMICIRCULAR (GRAPHICS2D)
    // =========================================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            if (w <= 20 || h <= 20) {
                return;
            }

            // 1. Fondo tipo tarjeta de instrumento
            RoundRectangle2D tarjeta = new RoundRectangle2D.Double(1.5, 1.5, w - 3.0, h - 3.0, 18.0, 18.0);
            g2.setColor(COLOR_TARJETA_FONDO);
            g2.fill(tarjeta);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(COLOR_TARJETA_BORDE);
            g2.draw(tarjeta);

            // 2. Título superior del instrumento
            Font fuenteTitulo = new Font("Segoe UI", Font.BOLD, 10);
            g2.setFont(fuenteTitulo);
            FontMetrics fmTit = g2.getFontMetrics();
            String titulo = "CALIDAD DE SERVICIO (QoS)";
            g2.setColor(COLOR_TEXTO_SECUNDARIO);
            g2.drawString(titulo, (float) ((w - fmTit.stringWidth(titulo)) / 2.0), 17.5f);

            // 3. Geometría proporcional del semicírculo (sin solapamiento con el bloque inferior)
            double cx = w / 2.0;
            double cy = h * 0.56;
            double radio = Math.min(w * 0.36, h * 0.355);
            float grosorArco = Math.max(9f, (float) (radio * 0.16));

            // 4. Banda exterior de referencia cromática (Rojo 0-45%, Amarillo 45-70%, Verde 70-100%)
            dibujarBandaReferenciaCromatica(g2, cx, cy, radio + (grosorArco * 0.72));

            // 5. Pista base y arco activo animado
            dibujarArcoPrincipal(g2, cx, cy, radio, grosorArco);

            // 6. Marcas de escala (0%, 25%, 50%, 75%, 100%)
            dibujarEscalaSemicircular(g2, cx, cy, radio, grosorArco);

            // 7. Aguja y pivote central
            dibujarAgujaYPivote(g2, cx, cy, radio);

            // 8. Lectura porcentual, insignia cromática y pie SLA
            dibujarLecturaYEstado(g2, cx, cy, radio, h);

        } finally {
            g2.dispose();
        }
    }

    private void dibujarBandaReferenciaCromatica(Graphics2D g2, double cx, double cy, double rBanda) {
        double x = cx - rBanda;
        double y = cy - rBanda;
        double d = rBanda * 2.0;

        g2.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));

        // Tramo Rojo: 0% a 45% (180° -> 99°, barrido -81°)
        g2.setColor(COLOR_ZONA_ROJA);
        g2.draw(new Arc2D.Double(x, y, d, d, 180.0, -80.0, Arc2D.OPEN));

        // Tramo Amarillo: 45% a 70% (98° -> 54°, barrido -44°)
        g2.setColor(COLOR_ZONA_AMARILLA);
        g2.draw(new Arc2D.Double(x, y, d, d, 98.5, -43.5, Arc2D.OPEN));

        // Tramo Verde: 70% a 100% (53.5° -> 0°, barrido -53.5°)
        g2.setColor(COLOR_ZONA_VERDE);
        g2.draw(new Arc2D.Double(x, y, d, d, 53.5, -53.5, Arc2D.OPEN));
    }

    private void dibujarArcoPrincipal(Graphics2D g2, double cx, double cy, double radio, float grosor) {
        double x = cx - radio;
        double y = cy - radio;
        double d = radio * 2.0;

        // Pista inactiva completa (180°)
        g2.setStroke(new BasicStroke(grosor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COLOR_PISTA_ARCO);
        g2.draw(new Arc2D.Double(x, y, d, d, ANGULO_INICIO, -BARRIDO_TOTAL, Arc2D.OPEN));

        // Arco activo proporcional al porcentaje interpolado
        if (!enEspera && porcentajeMostrado > 0.2) {
            double barridoActivo = -BARRIDO_TOTAL * (porcentajeMostrado / 100.0);
            GradientPaint grad = new GradientPaint(
                    (float) (cx - radio), (float) cy, estadoVisual.getColorBase(),
                    (float) (cx + radio), (float) (cy - radio), estadoVisual.getColorBrillo());
            g2.setPaint(grad);
            g2.setStroke(new BasicStroke(grosor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Arc2D.Double(x, y, d, d, ANGULO_INICIO, barridoActivo, Arc2D.OPEN));
        }
    }

    private void dibujarEscalaSemicircular(Graphics2D g2, double cx, double cy, double radio, float grosor) {
        int[] marcas = {0, 25, 50, 75, 100};
        double rInt = radio - (grosor / 2.0) - 9.0;
        double rExt = radio - (grosor / 2.0) - 3.0;
        double rTxt = rInt - 11.0;

        Font fuenteEscala = new Font("Segoe UI", Font.BOLD, Math.max(8, (int) Math.round(radio * 0.125)));
        g2.setFont(fuenteEscala);
        FontMetrics fm = g2.getFontMetrics();

        for (int pct : marcas) {
            double angRad = Math.toRadians(ANGULO_INICIO - (BARRIDO_TOTAL * pct / 100.0));
            double cos = Math.cos(angRad);
            double sin = Math.sin(angRad);

            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(COLOR_MARCA);
            g2.draw(new Line2D.Double(
                    cx + rInt * cos, cy - rInt * sin,
                    cx + rExt * cos, cy - rExt * sin));

            String etiqueta = String.valueOf(pct);
            double tx = cx + rTxt * cos - (fm.stringWidth(etiqueta) / 2.0);
            double ty = cy - rTxt * sin + (fm.getAscent() / 2.0) - 1.0;
            g2.setColor(COLOR_TEXTO_SECUNDARIO);
            g2.drawString(etiqueta, (float) tx, (float) ty);
        }
    }

    private void dibujarAgujaYPivote(Graphics2D g2, double cx, double cy, double radio) {
        double norm = enEspera ? 0.0 : (porcentajeMostrado / 100.0);
        double angRad = Math.toRadians(ANGULO_INICIO - (BARRIDO_TOTAL * norm));
        double cos = Math.cos(angRad);
        double sin = Math.sin(angRad);

        double largoPunta = radio * 0.78;
        double largoCola = radio * 0.14;
        double semiAncho = Math.max(2.6, radio * 0.045);

        double perpX = -sin;
        double perpY = -cos;

        Path2D sombra = crearAguja(cx + 1.8, cy + 2.0, cos, sin, perpX, perpY, largoPunta, largoCola, semiAncho);
        g2.setColor(COLOR_AGUJA_SOMBRA);
        g2.fill(sombra);

        Path2D aguja = crearAguja(cx, cy, cos, sin, perpX, perpY, largoPunta, largoCola, semiAncho);
        g2.setColor(COLOR_AGUJA);
        g2.fill(aguja);
        g2.setStroke(new BasicStroke(0.9f));
        g2.setColor(estadoVisual.getColorBrillo());
        g2.draw(aguja);

        double rExt = Math.max(6.5, radio * 0.10);
        double rInt = rExt * 0.45;
        g2.setColor(COLOR_PIVOTE_EXT);
        g2.fill(new Ellipse2D.Double(cx - rExt, cy - rExt, rExt * 2.0, rExt * 2.0));
        g2.setStroke(new BasicStroke(1.6f));
        g2.setColor(estadoVisual.getColorBrillo());
        g2.draw(new Ellipse2D.Double(cx - rExt, cy - rExt, rExt * 2.0, rExt * 2.0));
        g2.setColor(COLOR_AGUJA);
        g2.fill(new Ellipse2D.Double(cx - rInt, cy - rInt, rInt * 2.0, rInt * 2.0));
    }

    private Path2D crearAguja(double cx, double cy, double cos, double sin,
            double perpX, double perpY, double punta, double cola, double semiAncho) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx + perpX * semiAncho, cy + perpY * semiAncho);
        p.lineTo(cx + punta * cos, cy - punta * sin);
        p.lineTo(cx - perpX * semiAncho, cy - perpY * semiAncho);
        p.lineTo(cx - cola * cos, cy + cola * sin);
        p.closePath();
        return p;
    }

    private void dibujarLecturaYEstado(Graphics2D g2, double cx, double cy, double radio, int altoPanel) {
        // Lectura porcentual grande debajo del pivote (cero colisión con la aguja)
        int tamPct = Math.max(18, (int) Math.round(radio * 0.31));
        Font fuentePct = new Font("Consolas", Font.BOLD, tamPct);
        g2.setFont(fuentePct);
        FontMetrics fmPct = g2.getFontMetrics();

        String textoPct = enEspera ? "--.-%" : String.format(Locale.US, "%.1f%%", porcentajeMostrado);
        double yPct = cy + Math.max(24.0, radio * 0.38);

        g2.setColor(enEspera ? COLOR_TEXTO_SECUNDARIO : COLOR_TEXTO_VALOR);
        g2.drawString(textoPct, (float) (cx - fmPct.stringWidth(textoPct) / 2.0), (float) yPct);

        // Insignia tipo píldora con la categoría cromática
        Font fuenteBadge = new Font("Segoe UI", Font.BOLD, Math.max(9, (int) Math.round(radio * 0.135)));
        g2.setFont(fuenteBadge);
        FontMetrics fmBadge = g2.getFontMetrics();
        String textoBadge = estadoVisual.getEtiqueta();

        double pillW = fmBadge.stringWidth(textoBadge) + 22.0;
        double pillH = fmBadge.getHeight() + 5.0;
        double pillX = cx - (pillW / 2.0);
        double pillY = yPct + 6.0;

        g2.setColor(new Color(
                estadoVisual.getColorBase().getRed(),
                estadoVisual.getColorBase().getGreen(),
                estadoVisual.getColorBase().getBlue(), 45));
        RoundRectangle2D pill = new RoundRectangle2D.Double(pillX, pillY, pillW, pillH, pillH, pillH);
        g2.fill(pill);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(estadoVisual.getColorBrillo());
        g2.draw(pill);

        double yTxtBadge = pillY + ((pillH - fmBadge.getHeight()) / 2.0) + fmBadge.getAscent();
        g2.drawString(textoBadge, (float) (cx - fmBadge.stringWidth(textoBadge) / 2.0), (float) yTxtBadge);

        // Pie de cumplimiento SLA
        Font fuentePie = new Font("Segoe UI", Font.PLAIN, 10);
        g2.setFont(fuentePie);
        FontMetrics fmPie = g2.getFontMetrics();
        double yPie = pillY + pillH + fmPie.getAscent() + 4.0;
        if (yPie < altoPanel - 4) {
            g2.setColor(COLOR_TEXTO_SECUNDARIO);
            g2.drawString(subtituloSla, (float) (cx - fmPie.stringWidth(subtituloSla) / 2.0), (float) yPie);
        }
    }

    // =========================================================================
    // PRUEBA VISUAL AISLADA DE TAREA #23 (Shift + F6 en NetBeans)
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame marco = new JFrame("QA Visual Tarea #23 - Displays Digitales y Semicírculo QoS");
            marco.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            marco.setLayout(new BorderLayout(10, 10));
            marco.getContentPane().setBackground(new Color(241, 245, 249));

            DisplayDigitalTelemetria dispPing = new DisplayDigitalTelemetria(
                    "PING / LATENCIA (RTT)", "ms", 40.0, 150.0, 200.0);
            DisplayDigitalTelemetria dispJitter = new DisplayDigitalTelemetria(
                    "FLUCTUACIÓN (JITTER)", "ms", 10.0, 50.0, 60.0);
            IndicadorSemicircularQoS semiQoS = new IndicadorSemicircularQoS();

            JPanel colDisplays = new JPanel(new GridLayout(2, 1, 0, 10));
            colDisplays.setOpaque(false);
            colDisplays.add(dispPing);
            colDisplays.add(dispJitter);

            JPanel centro = new JPanel(new GridLayout(1, 2, 12, 0));
            centro.setOpaque(false);
            centro.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
            centro.add(colDisplays);
            centro.add(semiQoS);
            marco.add(centro, BorderLayout.CENTER);

            JButton btnVerde = crearBotonQA("► Óptimo (Verde)", new Color(16, 185, 129));
            JButton btnAmarillo = crearBotonQA("► Degradado (Amarillo)", new Color(217, 119, 6));
            JButton btnRojo = crearBotonQA("► Crítico (Rojo)", new Color(220, 38, 38));
            JButton btnReset = crearBotonQA("■ Reiniciar", new Color(71, 85, 105));

            btnVerde.addActionListener(e -> {
                dispPing.actualizarLectura(14.80, "Mín: 11.20 ms | Máx: 19.40 ms");
                dispJitter.actualizarLectura(2.35, "Último |ΔRTT|: 1.80 ms");
                semiQoS.actualizarQoS(96.4, CategoriaQoS.EXCELENTE, "SLA: CUMPLE (Enlace óptimo)");
            });
            btnAmarillo.addActionListener(e -> {
                dispPing.actualizarLectura(108.50, "Mín: 74.10 ms | Máx: 142.30 ms");
                dispJitter.actualizarLectura(29.40, "Último |ΔRTT|: 34.20 ms");
                semiQoS.actualizarQoS(58.2, CategoriaQoS.DEGRADADA, "SLA: INCUMPLE (Congestión)");
            });
            btnRojo.addActionListener(e -> {
                dispPing.actualizarLectura(245.90, "Mín: 189.00 ms | Máx: 312.50 ms");
                dispJitter.actualizarLectura(62.10, "Último |ΔRTT|: 78.40 ms");
                semiQoS.actualizarQoS(21.5, CategoriaQoS.CRITICA, "SLA: INCUMPLE (Enlace crítico)");
            });
            btnReset.addActionListener(e -> {
                dispPing.reiniciar("Esperando telemetría (Δt = 200 ms)");
                dispJitter.reiniciar("Esperando telemetría (Δt = 200 ms)");
                semiQoS.reiniciar();
            });

            JPanel barra = new JPanel();
            barra.setOpaque(false);
            barra.add(btnVerde);
            barra.add(btnAmarillo);
            barra.add(btnRojo);
            barra.add(btnReset);
            marco.add(barra, BorderLayout.SOUTH);

            marco.setSize(680, 315);
            marco.setLocationRelativeTo(null);
            marco.setVisible(true);
        });
    }

    private static JButton crearBotonQA(String texto, Color fondo) {
        JButton b = new JButton(texto);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBackground(fondo);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        return b;
    }
}
