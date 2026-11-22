package com.speedtest.view;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Tarea #22 (HU-05 / RF-VIS-01): Componente virtual tipo tacómetro (dial/gauge)
 * renderizado vectorialmente con Java2D (Graphics2D) para mostrar la velocidad
 * instantánea en Mbps.
 *
 * Características: - Arco de instrumento de 240° (210° a -30°) con escala
 * segmentada 0-1000 Mbps. - Interpolación exponencial suave a ~60 FPS mediante
 * javax.swing.Timer en el EDT (puentea las muestras de red de 5 Hz / 200 ms sin
 * saltos ni parpadeos). - Codificación cromática por fase operativa (ESPERA,
 * PING, DESCARGA, SUBIDA, etc.). - Auto-detención del Timer en reposo (0%
 * consumo de CPU cuando la aguja converge).
 */
public class TacometroVelocidad extends JPanel {

    /**
     * Fases operativas soportadas por el instrumento para ajustar el color del
     * arco activo y la insignia de estado.
     */
    public enum ModoFase {
        ESPERA("EN ESPERA", new Color(100, 116, 139), new Color(148, 163, 184)),
        PING("SONDEO PING", new Color(245, 158, 11), new Color(251, 191, 36)),
        DESCARGA("DESCARGA", new Color(2, 132, 199), new Color(56, 189, 248)),
        SUBIDA("SUBIDA", new Color(124, 58, 237), new Color(167, 139, 250)),
        COMPLETADO("COMPLETADO", new Color(16, 185, 129), new Color(52, 211, 153)),
        CANCELADO("CANCELADO", new Color(217, 119, 6), new Color(251, 191, 36)),
        ERROR("ERROR DE RED", new Color(220, 38, 38), new Color(248, 113, 113));

        private final String etiqueta;
        private final Color colorPrincipal;
        private final Color colorBrillo;

        ModoFase(String etiqueta, Color colorPrincipal, Color colorBrillo) {
            this.etiqueta = etiqueta;
            this.colorPrincipal = colorPrincipal;
            this.colorBrillo = colorBrillo;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        public Color getColorPrincipal() {
            return colorPrincipal;
        }

        public Color getColorBrillo() {
            return colorBrillo;
        }
    }

    // Geometría angular del dial (sistema estándar: 0° = Este, +CCW)
    private static final double ANGULO_INICIO_GRADOS = 210.0;  // 8 en punto (0 Mbps)
    private static final double BARRIDO_TOTAL_GRADOS = 240.0;  // Hasta -30° (4 en punto)

    // Escala segmentada tipo SpeedTest: 8 tramos iguales de 30° cada uno
    private static final double[] MARCAS_ESCALA_MBPS = {
        0, 5, 10, 25, 50, 100, 250, 500, 1000
    };
    private static final int SUBDIVISIONES_POR_TRAMO = 4;

    // Parámetros de interpolación suave (~60 FPS en el EDT)
    private static final int INTERVALO_ANIMACION_MS = 16;
    private static final double FACTOR_SUAVIZADO = 0.18;
    private static final double EPSILON_NORMALIZADO = 0.0005;
    private static final double EPSILON_VALOR_MBPS = 0.02;

    // Paleta visual del instrumento (alto contraste para escritorio y proyección)
    private static final Color COLOR_TARJETA_FONDO = new Color(15, 23, 42);
    private static final Color COLOR_TARJETA_BORDE = new Color(30, 41, 59);
    private static final Color COLOR_PISTA_ARCO = new Color(30, 41, 59);
    private static final Color COLOR_MARCA_MAYOR = new Color(148, 163, 184);
    private static final Color COLOR_MARCA_MENOR = new Color(71, 85, 105);
    private static final Color COLOR_TEXTO_ESCALA = new Color(203, 213, 225);
    private static final Color COLOR_TEXTO_VALOR = new Color(248, 250, 252);
    private static final Color COLOR_TEXTO_SECUNDARIO = new Color(148, 163, 184);
    private static final Color COLOR_AGUJA_SOMBRA = new Color(0, 0, 0, 95);
    private static final Color COLOR_AGUJA_CUERPO = new Color(248, 250, 252);
    private static final Color COLOR_PIVOTE_EXTERNO = new Color(51, 65, 85);
    private static final Color COLOR_PIVOTE_INTERNO = new Color(248, 250, 252);

    // Estado dinámico del tacómetro
    private double velocidadObjetivoMbps = 0.0;
    private double velocidadMostradaMbps = 0.0;
    private double velocidadPicoMbps = 0.0;

    // Posición normalizada [0.0, 1.0] interpolada para barrido angular homogéneo
    private double posObjetivoNorm = 0.0;
    private double posActualNorm = 0.0;

    private ModoFase modoFase = ModoFase.ESPERA;
    private String subtituloEstado = "Listo para medir";

    private final Timer timerAnimacion;

    public TacometroVelocidad() {
        setOpaque(false);
        setDoubleBuffered(true);
        setPreferredSize(new Dimension(320, 275));
        setMinimumSize(new Dimension(230, 200));

        this.timerAnimacion = new Timer(INTERVALO_ANIMACION_MS, e -> avanzarInterpolacion());
        this.timerAnimacion.setCoalesce(true);
    }

    // =========================================================================
    // API PÚBLICA PARA INTEGRACIÓN MVC (PanelMonitoreoQoS / ControladorQoS)
    // =========================================================================
    /**
     * Fija la nueva velocidad objetivo en Mbps. La aguja y el display digital
     * convergen suavemente mediante interpolación en el EDT.
     *
     * @param mbps Velocidad instantánea medida (se acota a >= 0.0).
     */
    public void setVelocidadObjetivo(double mbps) {
        final double valorSeguro = (Double.isNaN(mbps) || Double.isInfinite(mbps) || mbps < 0.0)
                ? 0.0 : mbps;

        ejecutarEnEDT(() -> {
            this.velocidadObjetivoMbps = valorSeguro;
            this.posObjetivoNorm = convertirMbpsANormalizado(valorSeguro);
            if (valorSeguro > this.velocidadPicoMbps) {
                this.velocidadPicoMbps = valorSeguro;
            }
            iniciarAnimacionSiNecesario();
        });
    }

    /**
     * Cambia la fase operativa activa del instrumento (ajusta colores e
     * insignia).
     *
     * @param nuevoModo Fase actual de la prueba.
     */
    public void setModoFase(ModoFase nuevoModo) {
        setModoFase(nuevoModo, null);
    }

    /**
     * Cambia la fase operativa activa y actualiza el subtítulo inferior
     * opcional.
     *
     * @param nuevoModo Fase actual de la prueba.
     * @param nuevoSubtitulo Texto auxiliar (si es null, conserva el subtítulo
     * actual).
     */
    public void setModoFase(ModoFase nuevoModo, String nuevoSubtitulo) {
        final ModoFase modoSeguro = (nuevoModo != null) ? nuevoModo : ModoFase.ESPERA;

        ejecutarEnEDT(() -> {
            this.modoFase = modoSeguro;
            if (nuevoSubtitulo != null) {
                this.subtituloEstado = nuevoSubtitulo;
            }
            repaint();
        });
    }

    /**
     * Reinicia la velocidad objetivo a 0 Mbps (con retorno suave de la aguja),
     * limpia el pico registrado y devuelve el dial a modo ESPERA.
     */
    public void reiniciar() {
        ejecutarEnEDT(() -> {
            this.velocidadObjetivoMbps = 0.0;
            this.posObjetivoNorm = 0.0;
            this.velocidadPicoMbps = 0.0;
            this.modoFase = ModoFase.ESPERA;
            this.subtituloEstado = "Listo para medir";
            iniciarAnimacionSiNecesario();
            repaint();
        });
    }

    /**
     * Reinicia únicamente el indicador de pico máximo (útil al transicionar
     * entre la fase de DESCARGA y la fase de SUBIDA).
     */
    public void reiniciarPico() {
        ejecutarEnEDT(() -> {
            this.velocidadPicoMbps = 0.0;
            repaint();
        });
    }

    /**
     * Detiene explícitamente el temporizador interno de animación.
     */
    public void detenerAnimacion() {
        if (timerAnimacion.isRunning()) {
            timerAnimacion.stop();
        }
    }

    public double getVelocidadObjetivoMbps() {
        return velocidadObjetivoMbps;
    }

    public double getVelocidadMostradaMbps() {
        return velocidadMostradaMbps;
    }

    public double getVelocidadPicoMbps() {
        return velocidadPicoMbps;
    }

    public ModoFase getModoFase() {
        return modoFase;
    }

    // =========================================================================
    // MOTOR DE INTERPOLACIÓN Y CONVERSIÓN DE ESCALA
    // =========================================================================
    private void iniciarAnimacionSiNecesario() {
        if (!timerAnimacion.isRunning()) {
            timerAnimacion.start();
        }
    }

    private void avanzarInterpolacion() {
        double deltaNorm = posObjetivoNorm - posActualNorm;
        double deltaValor = velocidadObjetivoMbps - velocidadMostradaMbps;

        boolean normConvergio = Math.abs(deltaNorm) < EPSILON_NORMALIZADO;
        boolean valorConvergio = Math.abs(deltaValor) < EPSILON_VALOR_MBPS;

        if (normConvergio && valorConvergio) {
            posActualNorm = posObjetivoNorm;
            velocidadMostradaMbps = velocidadObjetivoMbps;
            timerAnimacion.stop();
        } else {
            posActualNorm += deltaNorm * FACTOR_SUAVIZADO;
            velocidadMostradaMbps += deltaValor * FACTOR_SUAVIZADO;
        }
        repaint();
    }

    /**
     * Mapea una velocidad en Mbps hacia [0.0, 1.0] utilizando interpolación
     * lineal por tramos sobre MARCAS_ESCALA_MBPS.
     */
    public static double convertirMbpsANormalizado(double mbps) {
        if (mbps <= MARCAS_ESCALA_MBPS[0]) {
            return 0.0;
        }
        int numTramos = MARCAS_ESCALA_MBPS.length - 1;
        if (mbps >= MARCAS_ESCALA_MBPS[numTramos]) {
            return 1.0;
        }
        for (int i = 0; i < numTramos; i++) {
            double limInf = MARCAS_ESCALA_MBPS[i];
            double limSup = MARCAS_ESCALA_MBPS[i + 1];
            if (mbps <= limSup) {
                double fraccion = (mbps - limInf) / (limSup - limInf);
                return (i + fraccion) / (double) numTramos;
            }
        }
        return 1.0;
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
            configurarCalidadRenderizado(g2);

            int ancho = getWidth();
            int alto = getHeight();
            if (ancho <= 20 || alto <= 20) {
                return;
            }

            // 1. Fondo tipo tarjeta de instrumento
            dibujarFondoTarjeta(g2, ancho, alto);

            // 2. Geometría proporcional al contenedor
            double centroX = ancho / 2.0;
            double centroY = alto * 0.51;
            double radioArco = Math.min(ancho * 0.39, alto * 0.42);
            float grosorArco = Math.max(8f, (float) (radioArco * 0.115));

            // 3. Pista base del arco y arco activo coloreado según fase
            dibujarArcoPistaYProgreso(g2, centroX, centroY, radioArco, grosorArco);

            // 4. Graduaciones mayores, menores y etiquetas de escala en Mbps
            dibujarEscalaYMarcas(g2, centroX, centroY, radioArco, grosorArco);

            // 5. Aguja indicadora vectorial con sombra y pivote central
            dibujarAgujaYPivote(g2, centroX, centroY, radioArco);

            // 6. Display digital central, unidad, insignia de fase y pico
            dibujarLecturaDigitalEInsignia(g2, centroX, centroY, radioArco);

        } finally {
            g2.dispose();
        }
    }

    private void configurarCalidadRenderizado(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void dibujarFondoTarjeta(Graphics2D g2, int ancho, int alto) {
        double margen = 2.0;
        double arcoEsquina = 18.0;
        RoundRectangle2D tarjeta = new RoundRectangle2D.Double(
                margen, margen, ancho - 2 * margen, alto - 2 * margen, arcoEsquina, arcoEsquina);

        g2.setColor(COLOR_TARJETA_FONDO);
        g2.fill(tarjeta);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(COLOR_TARJETA_BORDE);
        g2.draw(tarjeta);
    }

    private void dibujarArcoPistaYProgreso(Graphics2D g2, double cx, double cy,
            double radio, float grosor) {

        double x = cx - radio;
        double y = cy - radio;
        double diametro = radio * 2.0;

        // Pista inactiva completa (240° en sentido horario)
        Arc2D arcoPista = new Arc2D.Double(
                x, y, diametro, diametro,
                ANGULO_INICIO_GRADOS, -BARRIDO_TOTAL_GRADOS, Arc2D.OPEN);

        g2.setStroke(new BasicStroke(grosor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COLOR_PISTA_ARCO);
        g2.draw(arcoPista);

        // Arco activo proporcional a la posición suavizada
        if (posActualNorm > 0.001) {
            double barridoActivo = -BARRIDO_TOTAL_GRADOS * posActualNorm;
            Arc2D arcoActivo = new Arc2D.Double(
                    x, y, diametro, diametro,
                    ANGULO_INICIO_GRADOS, barridoActivo, Arc2D.OPEN);

            GradientPaint gradiente = new GradientPaint(
                    (float) (cx - radio), (float) cy, modoFase.getColorPrincipal(),
                    (float) (cx + radio), (float) (cy - radio * 0.5), modoFase.getColorBrillo());

            g2.setPaint(gradiente);
            g2.setStroke(new BasicStroke(grosor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(arcoActivo);
        }
    }

    private void dibujarEscalaYMarcas(Graphics2D g2, double cx, double cy,
            double radioArco, float grosorArco) {

        int numTramos = MARCAS_ESCALA_MBPS.length - 1;
        double rBordeInternoArco = radioArco - (grosorArco / 2.0);
        double rMarcaExt = rBordeInternoArco - Math.max(4.0, radioArco * 0.04);
        double rMarcaMayorInt = rMarcaExt - Math.max(7.0, radioArco * 0.085);
        double rMarcaMenorInt = rMarcaExt - Math.max(4.0, radioArco * 0.045);
        double rEtiquetas = rMarcaMayorInt - Math.max(13.0, radioArco * 0.13);

        int tamFuenteEscala = Math.max(9, (int) Math.round(radioArco * 0.105));
        Font fuenteEscala = new Font("Segoe UI", Font.BOLD, tamFuenteEscala);
        g2.setFont(fuenteEscala);
        FontMetrics fm = g2.getFontMetrics();

        // Sub-marcas menores entre cada par de marcas mayores
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COLOR_MARCA_MENOR);
        int totalSubdivisiones = numTramos * SUBDIVISIONES_POR_TRAMO;
        for (int s = 0; s <= totalSubdivisiones; s++) {
            if (s % SUBDIVISIONES_POR_TRAMO == 0) {
                continue; // Las marcas mayores se dibujan aparte
            }
            double norm = s / (double) totalSubdivisiones;
            double angRad = Math.toRadians(ANGULO_INICIO_GRADOS - BARRIDO_TOTAL_GRADOS * norm);
            double cos = Math.cos(angRad);
            double sin = Math.sin(angRad);

            g2.draw(new Line2D.Double(
                    cx + rMarcaMenorInt * cos, cy - rMarcaMenorInt * sin,
                    cx + rMarcaExt * cos, cy - rMarcaExt * sin));
        }

        // Marcas mayores y etiquetas numéricas en Mbps
        for (int i = 0; i <= numTramos; i++) {
            double norm = i / (double) numTramos;
            double angRad = Math.toRadians(ANGULO_INICIO_GRADOS - BARRIDO_TOTAL_GRADOS * norm);
            double cos = Math.cos(angRad);
            double sin = Math.sin(angRad);

            boolean alcanzada = (posActualNorm + 0.005 >= norm) && (posActualNorm > 0.001);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(alcanzada ? modoFase.getColorBrillo() : COLOR_MARCA_MAYOR);

            g2.draw(new Line2D.Double(
                    cx + rMarcaMayorInt * cos, cy - rMarcaMayorInt * sin,
                    cx + rMarcaExt * cos, cy - rMarcaExt * sin));

            String textoMarca = (MARCAS_ESCALA_MBPS[i] >= 1000)
                    ? "1G"
                    : String.valueOf((int) MARCAS_ESCALA_MBPS[i]);

            double tx = cx + rEtiquetas * cos - (fm.stringWidth(textoMarca) / 2.0);
            double ty = cy - rEtiquetas * sin + (fm.getAscent() / 2.0) - 1.5;

            g2.setColor(alcanzada ? COLOR_TEXTO_VALOR : COLOR_TEXTO_ESCALA);
            g2.drawString(textoMarca, (float) tx, (float) ty);
        }
    }

    private void dibujarAgujaYPivote(Graphics2D g2, double cx, double cy, double radioArco) {
        double angGrados = ANGULO_INICIO_GRADOS - (BARRIDO_TOTAL_GRADOS * posActualNorm);
        double angRad = Math.toRadians(angGrados);
        double cos = Math.cos(angRad);
        double sin = Math.sin(angRad);

        double largoPunta = radioArco * 0.78;
        double largoCola = radioArco * 0.16;
        double semiAnchoBase = Math.max(3.0, radioArco * 0.042);

        // Vector perpendicular unitario a (cos, -sin) -> (-sin, -cos)
        double perpX = -sin;
        double perpY = -cos;

        Path2D aguja = construirPoligonoAguja(
                cx, cy, cos, sin, perpX, perpY, largoPunta, largoCola, semiAnchoBase);

        // Sombra suave desplazada 2 px
        Path2D sombra = construirPoligonoAguja(
                cx + 2.0, cy + 2.5, cos, sin, perpX, perpY, largoPunta, largoCola, semiAnchoBase);
        g2.setColor(COLOR_AGUJA_SOMBRA);
        g2.fill(sombra);

        // Cuerpo principal de la aguja
        g2.setColor(COLOR_AGUJA_CUERPO);
        g2.fill(aguja);

        // Borde sutil con el tono de la fase activa
        g2.setStroke(new BasicStroke(1.0f));
        g2.setColor(modoFase.getColorBrillo());
        g2.draw(aguja);

        // Pivote central metálico de dos capas
        double rPivoteExt = Math.max(7.5, radioArco * 0.095);
        double rPivoteInt = rPivoteExt * 0.48;

        g2.setColor(COLOR_PIVOTE_EXTERNO);
        g2.fill(new Ellipse2D.Double(cx - rPivoteExt, cy - rPivoteExt,
                rPivoteExt * 2.0, rPivoteExt * 2.0));

        g2.setStroke(new BasicStroke(1.8f));
        g2.setColor(modoFase.getColorBrillo());
        g2.draw(new Ellipse2D.Double(cx - rPivoteExt, cy - rPivoteExt,
                rPivoteExt * 2.0, rPivoteExt * 2.0));

        g2.setColor(COLOR_PIVOTE_INTERNO);
        g2.fill(new Ellipse2D.Double(cx - rPivoteInt, cy - rPivoteInt,
                rPivoteInt * 2.0, rPivoteInt * 2.0));
    }

    private Path2D construirPoligonoAguja(double cx, double cy, double cos, double sin,
            double perpX, double perpY, double largoPunta, double largoCola, double semiAncho) {

        Path2D path = new Path2D.Double();
        path.moveTo(cx + perpX * semiAncho, cy + perpY * semiAncho);
        path.lineTo(cx + largoPunta * cos, cy - largoPunta * sin);
        path.lineTo(cx - perpX * semiAncho, cy - perpY * semiAncho);
        path.lineTo(cx - largoCola * cos, cy + largoCola * sin);
        path.closePath();
        return path;
    }

    private void dibujarLecturaDigitalEInsignia(Graphics2D g2, double cx, double cy, double radioArco) {
        // Lectura numérica instantánea
        int tamValor = Math.max(18, (int) Math.round(radioArco * 0.27));
        Font fuenteValor = new Font("Segoe UI", Font.BOLD, tamValor);
        g2.setFont(fuenteValor);
        FontMetrics fmValor = g2.getFontMetrics();

        String textoValor = (velocidadMostradaMbps >= 100.0)
                ? String.format(Locale.US, "%.1f", velocidadMostradaMbps)
                : String.format(Locale.US, "%.2f", velocidadMostradaMbps);

        double yValor = cy + radioArco * 0.44;
        g2.setColor(COLOR_TEXTO_VALOR);
        g2.drawString(textoValor, (float) (cx - fmValor.stringWidth(textoValor) / 2.0), (float) yValor);

        // Unidad "Mbps"
        int tamUnidad = Math.max(10, (int) Math.round(radioArco * 0.115));
        Font fuenteUnidad = new Font("Segoe UI", Font.BOLD, tamUnidad);
        g2.setFont(fuenteUnidad);
        FontMetrics fmUnidad = g2.getFontMetrics();

        String textoUnidad = "Mbps";
        double yUnidad = yValor + fmUnidad.getAscent() + 3.0;
        g2.setColor(COLOR_TEXTO_SECUNDARIO);
        g2.drawString(textoUnidad, (float) (cx - fmUnidad.stringWidth(textoUnidad) / 2.0), (float) yUnidad);

        // Insignia tipo píldora con la fase activa
        int tamFase = Math.max(9, (int) Math.round(radioArco * 0.095));
        Font fuenteFase = new Font("Segoe UI", Font.BOLD, tamFase);
        g2.setFont(fuenteFase);
        FontMetrics fmFase = g2.getFontMetrics();

        String textoFase = modoFase.getEtiqueta();
        double padX = 12.0;
        double pillAncho = fmFase.stringWidth(textoFase) + (padX * 2.0);
        double pillAlto = fmFase.getHeight() + 5.0;
        double pillX = cx - (pillAncho / 2.0);
        double pillY = yUnidad + Math.max(6.0, radioArco * 0.06);

        Color fondoPildora = new Color(
                modoFase.getColorPrincipal().getRed(),
                modoFase.getColorPrincipal().getGreen(),
                modoFase.getColorPrincipal().getBlue(),
                45);

        RoundRectangle2D pildora = new RoundRectangle2D.Double(
                pillX, pillY, pillAncho, pillAlto, pillAlto, pillAlto);
        g2.setColor(fondoPildora);
        g2.fill(pildora);

        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(modoFase.getColorBrillo());
        g2.draw(pildora);

        double yTextoFase = pillY + ((pillAlto - fmFase.getHeight()) / 2.0) + fmFase.getAscent();
        g2.setColor(modoFase.getColorBrillo());
        g2.drawString(textoFase, (float) (cx - fmFase.stringWidth(textoFase) / 2.0), (float) yTextoFase);

        // Línea inferior con pico máximo o subtítulo
        int tamPie = Math.max(9, (int) Math.round(radioArco * 0.085));
        Font fuentePie = new Font("Segoe UI", Font.PLAIN, tamPie);
        g2.setFont(fuentePie);
        FontMetrics fmPie = g2.getFontMetrics();

        String textoPie = (velocidadPicoMbps > 0.0)
                ? String.format(Locale.US, "Pico: %.2f Mbps", velocidadPicoMbps)
                : subtituloEstado;

        double yPie = pillY + pillAlto + fmPie.getAscent() + 4.0;
        if (yPie < getHeight() - 6) {
            g2.setColor(COLOR_TEXTO_SECUNDARIO);
            g2.drawString(textoPie, (float) (cx - fmPie.stringWidth(textoPie) / 2.0), (float) yPie);
        }
    }

    // =========================================================================
    // PRUEBA VISUAL AISLADA (Shift + F6 en NetBeans para QA de Tarea #22)
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame marco = new JFrame("QA Visual Tarea #22 - TacometroVelocidad (Graphics2D)");
            marco.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            marco.setLayout(new BorderLayout(10, 10));
            marco.getContentPane().setBackground(new Color(241, 245, 249));

            TacometroVelocidad tacometro = new TacometroVelocidad();
            tacometro.setPreferredSize(new Dimension(420, 360));
            marco.add(tacometro, BorderLayout.CENTER);

            // Simulación de ráfagas de red cada 200 ms (5 Hz)
            double[] muestrasDescarga = {12.4, 48.2, 92.6, 136.14, 141.8, 129.5, 134.9};
            double[] muestrasSubida = {3.2, 6.28, 18.5, 42.1, 53.87, 51.4};

            JButton btnSimular = new JButton("► Simular Ciclo (200 ms)");
            btnSimular.setContentAreaFilled(false);
            btnSimular.setOpaque(true);
            btnSimular.setBackground(new Color(2, 132, 199));
            btnSimular.setForeground(Color.WHITE);
            btnSimular.setFocusPainted(false);

            JButton btnReiniciar = new JButton("■ Reiniciar");
            btnReiniciar.setContentAreaFilled(false);
            btnReiniciar.setOpaque(true);
            btnReiniciar.setBackground(new Color(71, 85, 105));
            btnReiniciar.setForeground(Color.WHITE);
            btnReiniciar.setFocusPainted(false);

            final Timer[] simTimer = new Timer[1];
            btnSimular.addActionListener(e -> {
                if (simTimer[0] != null && simTimer[0].isRunning()) {
                    simTimer[0].stop();
                }
                tacometro.reiniciar();
                final int[] paso = {0};

                simTimer[0] = new Timer(200, ev -> {
                    int p = paso[0]++;
                    if (p < 3) {
                        tacometro.setModoFase(ModoFase.PING, "Midiendo RTT...");
                        tacometro.setVelocidadObjetivo(0.0);
                    } else if (p < 3 + muestrasDescarga.length) {
                        if (p == 3) {
                            tacometro.reiniciarPico();
                            tacometro.setModoFase(ModoFase.DESCARGA);
                        }
                        tacometro.setVelocidadObjetivo(muestrasDescarga[p - 3]);
                    } else if (p < 3 + muestrasDescarga.length + muestrasSubida.length) {
                        int idxSub = p - (3 + muestrasDescarga.length);
                        if (idxSub == 0) {
                            tacometro.reiniciarPico();
                            tacometro.setModoFase(ModoFase.SUBIDA);
                        }
                        tacometro.setVelocidadObjetivo(muestrasSubida[idxSub]);
                    } else {
                        tacometro.setModoFase(ModoFase.COMPLETADO, "Ensayo finalizado");
                        ((Timer) ev.getSource()).stop();
                    }
                });
                simTimer[0].start();
            });

            btnReiniciar.addActionListener(e -> {
                if (simTimer[0] != null && simTimer[0].isRunning()) {
                    simTimer[0].stop();
                }
                tacometro.reiniciar();
            });

            JPanel barra = new JPanel();
            barra.setOpaque(false);
            barra.add(btnSimular);
            barra.add(btnReiniciar);
            marco.add(barra, BorderLayout.SOUTH);

            marco.pack();
            marco.setLocationRelativeTo(null);
            marco.setVisible(true);
        });
    }
}
