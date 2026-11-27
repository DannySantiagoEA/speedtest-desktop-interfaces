package com.speedtest.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Tarea #23 (HU-05 / RF-VIS-01): Display numérico digital estilizado de lectura
 * rápida para telemetría en tiempo real (Ping/RTT en ms y Jitter en ms).
 *
 * Características: - Tipografía digital de alto contraste (Consolas Bold) sobre
 * chasis oscuro. - Codificación cromática dinámica (verde, amarillo, rojo)
 * según umbrales SLA. - Indicador LED de estado, barra segmentada proporcional
 * e interpolación suave en el EDT con auto-detención en reposo (0% CPU
 * inactivo).
 */
public class DisplayDigitalTelemetria extends JPanel {

    public enum NivelEstado {
        EN_ESPERA("EN ESPERA", new Color(100, 116, 139), new Color(148, 163, 184)),
        OPTIMO("ÓPTIMO", new Color(16, 185, 129), new Color(52, 211, 153)),
        MODERADO("MODERADO", new Color(245, 158, 11), new Color(251, 191, 36)),
        CRITICO("CRÍTICO", new Color(220, 38, 38), new Color(248, 113, 113));

        private final String etiqueta;
        private final Color colorBase;
        private final Color colorBrillo;

        NivelEstado(String etiqueta, Color colorBase, Color colorBrillo) {
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

    // Paleta de chasis de instrumento (unificada con TacometroVelocidad)
    private static final Color COLOR_FONDO = new Color(15, 23, 42);
    private static final Color COLOR_BORDE = new Color(30, 41, 59);
    private static final Color COLOR_PANTALLA_INTERNA = new Color(9, 14, 26);
    private static final Color COLOR_TEXTO_TITULO = new Color(148, 163, 184);
    private static final Color COLOR_TEXTO_DETALLE = new Color(148, 163, 184);
    private static final Color COLOR_SEGMENTO_APAGADO = new Color(30, 41, 59);

    // Parámetros de suavizado (~60 FPS)
    private static final int INTERVALO_ANIMACION_MS = 16;
    private static final double FACTOR_SUAVIZADO = 0.25;
    private static final double EPSILON = 0.015;
    private static final int TOTAL_SEGMENTOS_BARRA = 14;

    // Configuración fija de la magnitud mostrada
    private final String titulo;
    private final String unidad;
    private final double umbralVerdeMax;
    private final double umbralAmarilloMax;
    private final double escalaMaximaBarra;

    // Estado dinámico
    private double valorObjetivo = 0.0;
    private double valorMostrado = 0.0;
    private boolean enEspera = true;
    private boolean sinRespuesta = false;
    private NivelEstado nivelActual = NivelEstado.EN_ESPERA;
    private String detalleSecundario;

    private final Timer timerAnimacion;

    /**
     * @param titulo Encabezado del display (ej. "PING / LATENCIA (RTT)").
     * @param unidad Unidad física (ej. "ms").
     * @param umbralVerdeMax Límite superior para zona VERDE (ej. 40.0 ms).
     * @param umbralAmarilloMax Límite superior para zona AMARILLA (ej. 150.0
     * ms).
     * @param escalaMaximaBarra Valor de referencia para llenar al 100% la barra
     * segmentada.
     */
    public DisplayDigitalTelemetria(String titulo, String unidad,
            double umbralVerdeMax, double umbralAmarilloMax, double escalaMaximaBarra) {
        this.titulo = titulo;
        this.unidad = unidad;
        this.umbralVerdeMax = umbralVerdeMax;
        this.umbralAmarilloMax = umbralAmarilloMax;
        this.escalaMaximaBarra = Math.max(1.0, escalaMaximaBarra);
        this.detalleSecundario = "Esperando telemetría (Δt = 200 ms)";

        setOpaque(false);
        setDoubleBuffered(true);
        setPreferredSize(new Dimension(240, 102));
        setMinimumSize(new Dimension(190, 88));

        this.timerAnimacion = new Timer(INTERVALO_ANIMACION_MS, e -> avanzarInterpolacion());
        this.timerAnimacion.setCoalesce(true);
    }

    // =========================================================================
    // API PÚBLICA MVC
    // =========================================================================
    /**
     * Actualiza el valor numérico del display con transición suave y aplica la
     * codificación cromática (verde, amarillo, rojo) según los umbrales SLA.
     */
    public void actualizarLectura(double nuevoValor, String nuevoDetalle) {
        final double valorSeguro = (Double.isNaN(nuevoValor) || Double.isInfinite(nuevoValor) || nuevoValor < 0.0)
                ? 0.0 : nuevoValor;

        ejecutarEnEDT(() -> {
            this.enEspera = false;
            this.sinRespuesta = false;
            this.valorObjetivo = valorSeguro;
            this.nivelActual = evaluarNivel(valorSeguro);
            if (nuevoDetalle != null) {
                this.detalleSecundario = nuevoDetalle;
            }
            iniciarAnimacionSiNecesario();
            repaint();
        });
    }

    /**
     * Marca el display en condición crítica de pérdida total / sin respuesta
     * (evita mostrar "0.00 ms" en verde cuando un host no responde).
     */
    public void marcarSinRespuesta(String detalleError) {
        ejecutarEnEDT(() -> {
            this.enEspera = false;
            this.sinRespuesta = true;
            this.valorObjetivo = escalaMaximaBarra;
            this.valorMostrado = escalaMaximaBarra;
            this.nivelActual = NivelEstado.CRITICO;
            this.detalleSecundario = (detalleError != null) ? detalleError : "Sin respuesta del nodo";
            detenerAnimacion();
            repaint();
        });
    }

    /**
     * Reinicia el display al estado neutro EN ESPERA.
     */
    public void reiniciar(String textoEspera) {
        ejecutarEnEDT(() -> {
            this.enEspera = true;
            this.sinRespuesta = false;
            this.valorObjetivo = 0.0;
            this.valorMostrado = 0.0;
            this.nivelActual = NivelEstado.EN_ESPERA;
            this.detalleSecundario = (textoEspera != null) ? textoEspera : "Esperando telemetría (Δt = 200 ms)";
            detenerAnimacion();
            repaint();
        });
    }

    public void detenerAnimacion() {
        if (timerAnimacion.isRunning()) {
            timerAnimacion.stop();
        }
    }

    public NivelEstado getNivelActual() {
        return nivelActual;
    }

    public double getValorObjetivo() {
        return valorObjetivo;
    }

    // =========================================================================
    // INTERPOLACIÓN Y UTILIDADES EDT
    // =========================================================================
    private NivelEstado evaluarNivel(double valor) {
        if (valor <= umbralVerdeMax) {
            return NivelEstado.OPTIMO;
        }
        if (valor <= umbralAmarilloMax) {
            return NivelEstado.MODERADO;
        }
        return NivelEstado.CRITICO;
    }

    private void iniciarAnimacionSiNecesario() {
        if (!timerAnimacion.isRunning()) {
            timerAnimacion.start();
        }
    }

    private void avanzarInterpolacion() {
        double delta = valorObjetivo - valorMostrado;
        if (Math.abs(delta) < EPSILON) {
            valorMostrado = valorObjetivo;
            timerAnimacion.stop();
        } else {
            valorMostrado += delta * FACTOR_SUAVIZADO;
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
    // RENDERIZADO VECTORIAL CON GRAPHICS2D
    // =========================================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth();
            int h = getHeight();
            if (w <= 20 || h <= 20) {
                return;
            }

            // 1. Chasis exterior con borde reactivo al estado cromático
            RoundRectangle2D chasis = new RoundRectangle2D.Double(1.5, 1.5, w - 3.0, h - 3.0, 16.0, 16.0);
            g2.setColor(COLOR_FONDO);
            g2.fill(chasis);

            Color colorBorde = enEspera
                    ? COLOR_BORDE
                    : new Color(nivelActual.getColorBase().getRed(),
                            nivelActual.getColorBase().getGreen(),
                            nivelActual.getColorBase().getBlue(), 170);
            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(colorBorde);
            g2.draw(chasis);

            // 2. Cabecera superior: título + LED luminoso + píldora de estado
            int padX = 12;
            int yCabecera = 18;

            Font fuenteTitulo = new Font("Segoe UI", Font.BOLD, Math.max(9, Math.min(11, h / 9)));
            g2.setFont(fuenteTitulo);
            g2.setColor(COLOR_TEXTO_TITULO);
            g2.drawString(titulo, padX, yCabecera);

            // Píldora de estado a la derecha
            Font fuenteEstado = new Font("Segoe UI", Font.BOLD, 9);
            g2.setFont(fuenteEstado);
            FontMetrics fmEst = g2.getFontMetrics();
            String textoEstado = nivelActual.getEtiqueta();

            double pillW = fmEst.stringWidth(textoEstado) + 22.0;
            double pillH = 16.0;
            double pillX = w - padX - pillW;
            double pillY = 6.5;

            g2.setColor(new Color(
                    nivelActual.getColorBase().getRed(),
                    nivelActual.getColorBase().getGreen(),
                    nivelActual.getColorBase().getBlue(), 40));
            RoundRectangle2D pill = new RoundRectangle2D.Double(pillX, pillY, pillW, pillH, pillH, pillH);
            g2.fill(pill);
            g2.setStroke(new BasicStroke(1.0f));
            g2.setColor(nivelActual.getColorBrillo());
            g2.draw(pill);

            // Punto LED dentro de la píldora
            double ledD = 6.0;
            double ledX = pillX + 6.0;
            double ledY = pillY + (pillH - ledD) / 2.0;
            g2.setColor(nivelActual.getColorBrillo());
            g2.fill(new Ellipse2D.Double(ledX, ledY, ledD, ledD));

            g2.drawString(textoEstado, (float) (ledX + ledD + 4.0),
                    (float) (pillY + ((pillH - fmEst.getHeight()) / 2.0) + fmEst.getAscent()));

            // 3. Ventana digital interna de alto contraste
            double screenX = padX;
            double screenY = 26.0;
            double screenW = w - (padX * 2.0);
            double screenH = Math.max(30.0, h - 55.0);

            RoundRectangle2D pantalla = new RoundRectangle2D.Double(screenX, screenY, screenW, screenH, 10.0, 10.0);
            g2.setColor(COLOR_PANTALLA_INTERNA);
            g2.fill(pantalla);
            g2.setStroke(new BasicStroke(1.0f));
            g2.setColor(new Color(30, 41, 59));
            g2.draw(pantalla);

            // Lectura numérica digital estilizada
            String textoValor;
            if (enEspera) {
                textoValor = "--.--";
            } else if (sinRespuesta) {
                textoValor = "TIMEOUT";
            } else {
                textoValor = String.format(Locale.US, "%.2f", valorMostrado);
            }

            int tamDigitos = Math.max(18, Math.min(30, (int) Math.round(screenH * 0.68)));
            Font fuenteDigitos = new Font("Consolas", Font.BOLD, tamDigitos);
            Font fuenteUnidad = new Font("Segoe UI", Font.BOLD, Math.max(11, tamDigitos / 2));

            g2.setFont(fuenteDigitos);
            FontMetrics fmDig = g2.getFontMetrics();
            g2.setFont(fuenteUnidad);
            FontMetrics fmUni = g2.getFontMetrics();

            String textoUnidad = sinRespuesta ? "" : (" " + unidad);
            int anchoTotal = fmDig.stringWidth(textoValor) + fmUni.stringWidth(textoUnidad);
            float xInicio = (float) (screenX + (screenW - anchoTotal) / 2.0);
            float yBase = (float) (screenY + (screenH + fmDig.getAscent() - fmDig.getDescent()) / 2.0);

            Color colorDigitos = enEspera ? new Color(100, 116, 139) : nivelActual.getColorBrillo();

            // Halo sutil tipo display LED
            if (!enEspera) {
                g2.setFont(fuenteDigitos);
                g2.setColor(new Color(colorDigitos.getRed(), colorDigitos.getGreen(), colorDigitos.getBlue(), 45));
                g2.drawString(textoValor, xInicio + 1.0f, yBase + 1.0f);
            }

            g2.setFont(fuenteDigitos);
            g2.setColor(colorDigitos);
            g2.drawString(textoValor, xInicio, yBase);

            if (!textoUnidad.isEmpty()) {
                g2.setFont(fuenteUnidad);
                g2.setColor(COLOR_TEXTO_TITULO);
                g2.drawString(textoUnidad, xInicio + fmDig.stringWidth(textoValor), yBase);
            }

            // 4. Barra segmentada proporcional tipo VU-Meter
            double barraY = screenY + screenH + 5.0;
            double barraH = 5.0;
            double espacio = 3.0;
            double segW = (screenW - (espacio * (TOTAL_SEGMENTOS_BARRA - 1))) / TOTAL_SEGMENTOS_BARRA;
            double proporcion = enEspera ? 0.0 : Math.min(1.0, valorMostrado / escalaMaximaBarra);
            int segmentosActivos = enEspera ? 0 : Math.max(1, (int) Math.round(proporcion * TOTAL_SEGMENTOS_BARRA));

            for (int i = 0; i < TOTAL_SEGMENTOS_BARRA; i++) {
                double sx = screenX + i * (segW + espacio);
                RoundRectangle2D seg = new RoundRectangle2D.Double(sx, barraY, segW, barraH, 2.5, 2.5);
                if (i < segmentosActivos) {
                    g2.setColor(nivelActual.getColorBrillo());
                } else {
                    g2.setColor(COLOR_SEGMENTO_APAGADO);
                }
                g2.fill(seg);
            }

            // 5. Línea inferior de detalle secundario (Mín/Máx o último |ΔRTT|)
            Font fuenteDet = new Font("Segoe UI", Font.PLAIN, 10);
            g2.setFont(fuenteDet);
            FontMetrics fmDet = g2.getFontMetrics();
            float yDet = (float) (barraY + barraH + fmDet.getAscent() + 3.0);
            if (yDet < h - 3) {
                g2.setColor(COLOR_TEXTO_DETALLE);
                float xDet = (float) ((w - fmDet.stringWidth(detalleSecundario)) / 2.0);
                g2.drawString(detalleSecundario, Math.max(padX, xDet), yDet);
            }

        } finally {
            g2.dispose();
        }
    }
}
