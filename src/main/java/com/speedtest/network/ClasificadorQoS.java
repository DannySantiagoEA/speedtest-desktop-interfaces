package com.speedtest.network;

import com.speedtest.network.MedidorQoS.ResumenQoS;
import com.speedtest.network.MotorDescargaWorker.ResumenDescarga;
import com.speedtest.network.MotorSubidaWorker.ResumenSubida;

/**
 * Tarea #19 (HU-04 / RF-ADQ-06): Motor algorítmico de correlación multivariable
 * y clasificación categórica de Calidad de Servicio (QoS).
 *
 * Consolida el Throughput final (Descarga y Subida en Mbps), la Latencia media
 * (RTT en ms), la Fluctuación de fase (Jitter en ms) y la Tasa de paquetes
 * perdidos (% Packet Loss), evaluándolos frente a los umbrales de Nivel de
 * Servicio (SLA) para categorizar el enlace en: EXCELENTE, BUENA, DEGRADADA o
 * CRITICA.
 */
public class ClasificadorQoS {

    /**
     * Categorías oficiales de Calidad de Servicio (RF-ADQ-06) compatibles con
     * la columna 'calificacion_qos' en MySQL (HU-07) y las tarjetas semafóricas
     * (HU-05).
     */
    public enum CategoriaQoS {
        EXCELENTE("Excelente", "Cumplimiento óptimo de SLA: apto para streaming 4K, VoIP, telemedicina y baja latencia."),
        BUENA("Buena", "Cumplimiento satisfactorio de SLA: enlace estable para navegación intensiva y videoconferencia HD."),
        DEGRADADA("Degradada", "Incumplimiento parcial de SLA: presencia de congestión, encolamiento (bufferbloat) o asimetría."),
        CRITICA("Crítica", "Incumplimiento severo de SLA: microcortes, pérdida de paquetes elevada o degradación extrema.");

        private final String etiqueta;
        private final String descripcionSla;

        CategoriaQoS(String etiqueta, String descripcionSla) {
            this.etiqueta = etiqueta;
            this.descripcionSla = descripcionSla;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        public String getDescripcionSla() {
            return descripcionSla;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /**
     * Perfil parametrizable de umbrales de Acuerdo de Nivel de Servicio (SLA).
     * Permite evaluar tanto conexiones estándar de banda ancha / LAN como
     * contratos ISP específicos.
     */
    public static class UmbralesSLA {

        // Umbrales de Descarga (Mbps)
        private final double descargaExcelenteMbps;
        private final double descargaBuenaMbps;
        private final double descargaDegradadaMbps;

        // Umbrales de Subida (Mbps)
        private final double subidaExcelenteMbps;
        private final double subidaBuenaMbps;
        private final double subidaDegradadaMbps;

        // Umbrales de Latencia Media RTT (ms - menor es mejor)
        private final double rttExcelenteMs;
        private final double rttBuenoMs;
        private final double rttDegradadoMs;

        // Umbrales de Jitter Acumulado (ms - menor es mejor)
        private final double jitterExcelenteMs;
        private final double jitterBuenoMs;
        private final double jitterDegradadoMs;

        // Umbrales de Pérdida de Paquetes (% - menor es mejor)
        private final double perdidaExcelentePct;
        private final double perdidaBuenaPct;
        private final double perdidaDegradadaPct;

        public UmbralesSLA(double descargaExcelenteMbps, double descargaBuenaMbps, double descargaDegradadaMbps,
                double subidaExcelenteMbps, double subidaBuenaMbps, double subidaDegradadaMbps,
                double rttExcelenteMs, double rttBuenoMs, double rttDegradadoMs,
                double jitterExcelenteMs, double jitterBuenoMs, double jitterDegradadoMs,
                double perdidaExcelentePct, double perdidaBuenaPct, double perdidaDegradadaPct) {
            this.descargaExcelenteMbps = descargaExcelenteMbps;
            this.descargaBuenaMbps = descargaBuenaMbps;
            this.descargaDegradadaMbps = descargaDegradadaMbps;
            this.subidaExcelenteMbps = subidaExcelenteMbps;
            this.subidaBuenaMbps = subidaBuenaMbps;
            this.subidaDegradadaMbps = subidaDegradadaMbps;
            this.rttExcelenteMs = rttExcelenteMs;
            this.rttBuenoMs = rttBuenoMs;
            this.rttDegradadoMs = rttDegradadoMs;
            this.jitterExcelenteMs = jitterExcelenteMs;
            this.jitterBuenoMs = jitterBuenoMs;
            this.jitterDegradadoMs = jitterDegradadoMs;
            this.perdidaExcelentePct = perdidaExcelentePct;
            this.perdidaBuenaPct = perdidaBuenaPct;
            this.perdidaDegradadaPct = perdidaDegradadaPct;
        }

        /**
         * Perfil SLA predeterminado calibrado bajo estándares de banda ancha e
         * ITU-T G.1010 / Y.1541.
         */
        public static UmbralesSLA obtenerPerfilEstandar() {
            return new UmbralesSLA(
                    50.0, 20.0, 5.0, // Descarga: Excelente >= 50, Buena >= 20, Degradada >= 5 Mbps
                    20.0, 10.0, 2.0, // Subida:   Excelente >= 20, Buena >= 10, Degradada >= 2 Mbps
                    40.0, 80.0, 150.0, // RTT:      Excelente <= 40, Buena <= 80, Degradada <= 150 ms
                    10.0, 25.0, 50.0, // Jitter:   Excelente <= 10, Buena <= 25, Degradada <= 50 ms
                    0.5, 2.0, 5.0 // Loss %:   Excelente <= 0.5%, Buena <= 2%, Degradada <= 5%
            );
        }

        public double getDescargaExcelenteMbps() {
            return descargaExcelenteMbps;
        }

        public double getDescargaBuenaMbps() {
            return descargaBuenaMbps;
        }

        public double getDescargaDegradadaMbps() {
            return descargaDegradadaMbps;
        }

        public double getSubidaExcelenteMbps() {
            return subidaExcelenteMbps;
        }

        public double getSubidaBuenaMbps() {
            return subidaBuenaMbps;
        }

        public double getSubidaDegradadaMbps() {
            return subidaDegradadaMbps;
        }

        public double getRttExcelenteMs() {
            return rttExcelenteMs;
        }

        public double getRttBuenoMs() {
            return rttBuenoMs;
        }

        public double getRttDegradadoMs() {
            return rttDegradadoMs;
        }

        public double getJitterExcelenteMs() {
            return jitterExcelenteMs;
        }

        public double getJitterBuenoMs() {
            return jitterBuenoMs;
        }

        public double getJitterDegradadoMs() {
            return jitterDegradadoMs;
        }

        public double getPerdidaExcelentePct() {
            return perdidaExcelentePct;
        }

        public double getPerdidaBuenaPct() {
            return perdidaBuenaPct;
        }

        public double getPerdidaDegradadaPct() {
            return perdidaDegradadaPct;
        }
    }

    /**
     * DTO inmutable con el veredicto consolidado de Calidad de Servicio (QoS),
     * los subíndices normalizados (0..100) para graficación polar/radar y el
     * diagnóstico de SLA.
     */
    public static class ResultadoDiagnosticoQoS {

        private final double descargaMbps;
        private final double subidaMbps;
        private final double rttPromedioMs;
        private final double jitterMs;
        private final double perdidaPaquetesPct;

        private final double puntajeDescarga;
        private final double puntajeSubida;
        private final double puntajeLatencia;
        private final double puntajeJitter;
        private final double puntajePerdida;

        private final double indiceGlobalQoS;
        private final CategoriaQoS categoria;
        private final boolean cumpleSla;
        private final String diagnosticoTecnico;

        public ResultadoDiagnosticoQoS(double descargaMbps, double subidaMbps,
                double rttPromedioMs, double jitterMs, double perdidaPaquetesPct,
                double puntajeDescarga, double puntajeSubida,
                double puntajeLatencia, double puntajeJitter, double puntajePerdida,
                double indiceGlobalQoS, CategoriaQoS categoria,
                boolean cumpleSla, String diagnosticoTecnico) {
            this.descargaMbps = descargaMbps;
            this.subidaMbps = subidaMbps;
            this.rttPromedioMs = rttPromedioMs;
            this.jitterMs = jitterMs;
            this.perdidaPaquetesPct = perdidaPaquetesPct;
            this.puntajeDescarga = puntajeDescarga;
            this.puntajeSubida = puntajeSubida;
            this.puntajeLatencia = puntajeLatencia;
            this.puntajeJitter = puntajeJitter;
            this.puntajePerdida = puntajePerdida;
            this.indiceGlobalQoS = indiceGlobalQoS;
            this.categoria = categoria;
            this.cumpleSla = cumpleSla;
            this.diagnosticoTecnico = diagnosticoTecnico;
        }

        public double getDescargaMbps() {
            return descargaMbps;
        }

        public double getSubidaMbps() {
            return subidaMbps;
        }

        public double getRttPromedioMs() {
            return rttPromedioMs;
        }

        public double getJitterMs() {
            return jitterMs;
        }

        public double getPerdidaPaquetesPct() {
            return perdidaPaquetesPct;
        }

        public double getPuntajeDescarga() {
            return puntajeDescarga;
        }

        public double getPuntajeSubida() {
            return puntajeSubida;
        }

        public double getPuntajeLatencia() {
            return puntajeLatencia;
        }

        public double getPuntajeJitter() {
            return puntajeJitter;
        }

        public double getPuntajePerdida() {
            return puntajePerdida;
        }

        public double getIndiceGlobalQoS() {
            return indiceGlobalQoS;
        }

        public CategoriaQoS getCategoria() {
            return categoria;
        }

        public String getCalificacionTexto() {
            return categoria.getEtiqueta();
        }

        public boolean isCumpleSla() {
            return cumpleSla;
        }

        public String getDiagnosticoTecnico() {
            return diagnosticoTecnico;
        }

        @Override
        public String toString() {
            return String.format(
                    "QoS Global: %s (%.2f/100) [SLA: %s] | Down: %.2f Mbps (%.0f pts) | Up: %.2f Mbps (%.0f pts) | "
                    + "RTT: %.2f ms (%.0f pts) | Jitter: %.2f ms (%.0f pts) | Loss: %.2f%% (%.0f pts) | Diagnóstico: %s",
                    categoria.getEtiqueta().toUpperCase(),
                    indiceGlobalQoS,
                    cumpleSla ? "CUMPLE" : "INCUMPLE",
                    descargaMbps, puntajeDescarga,
                    subidaMbps, puntajeSubida,
                    rttPromedioMs, puntajeLatencia,
                    jitterMs, puntajeJitter,
                    perdidaPaquetesPct, puntajePerdida,
                    diagnosticoTecnico
            );
        }
    }

    // =========================================================================
    // PESOS DE CORRELACIÓN MULTIVARIABLE (SUMA = 1.00)
    // =========================================================================
    public static final double PESO_DESCARGA = 0.20;
    public static final double PESO_SUBIDA = 0.15;
    public static final double PESO_LATENCIA = 0.25;
    public static final double PESO_JITTER = 0.15;
    public static final double PESO_PERDIDA = 0.25;

    private final UmbralesSLA umbrales;

    /**
     * Constructor con el perfil estándar de umbrales SLA.
     */
    public ClasificadorQoS() {
        this(UmbralesSLA.obtenerPerfilEstandar());
    }

    /**
     * Constructor con perfil de umbrales SLA personalizado.
     */
    public ClasificadorQoS(UmbralesSLA umbrales) {
        this.umbrales = (umbrales != null) ? umbrales : UmbralesSLA.obtenerPerfilEstandar();
    }

    /**
     * Evalúa y clasifica la Calidad de Servicio (QoS) a partir de los DTOs
     * generados por los tres motores de adquisición (MedidorQoS,
     * MotorDescargaWorker y MotorSubidaWorker).
     */
    public ResultadoDiagnosticoQoS evaluarEnsayoCompleto(ResumenQoS resumenQoS,
            ResumenDescarga resumenDescarga,
            ResumenSubida resumenSubida) {
        double downMbps = (resumenDescarga != null) ? resumenDescarga.getVelocidadPromedioMbps() : 0.0;
        double upMbps = (resumenSubida != null) ? resumenSubida.getVelocidadPromedioMbps() : 0.0;
        double rttMs = (resumenQoS != null) ? resumenQoS.getRttPromedioMs() : 0.0;
        double jitterMs = (resumenQoS != null) ? resumenQoS.getJitterAcumuladoMs() : 0.0;
        double lossPct = (resumenQoS != null) ? resumenQoS.getPorcentajePerdida() : 100.0;

        // Si todos los paquetes de sondeo se perdieron (100% loss) y no hubo respuesta RTT,
        // penalizamos el RTT efectivo como timeout crítico.
        if (resumenQoS != null && resumenQoS.getPaquetesEnviados() > 0 && resumenQoS.getPaquetesRecibidos() == 0) {
            rttMs = 1000.0;
            jitterMs = 100.0;
            lossPct = 100.0;
        }

        return evaluarCalidadEnlace(downMbps, upMbps, rttMs, jitterMs, lossPct);
    }

    /**
     * Función algorítmica central de la Tarea #19 (RF-ADQ-06): Correlaciona
     * Throughput (Descarga y Subida), Latencia media (RTT), Fluctuación
     * (Jitter) y Pérdida de paquetes (% Packet Loss) contra los umbrales de
     * SLA.
     *
     * @param descargaMbps Velocidad sostenida de descarga en Mbps.
     * @param subidaMbps Velocidad sostenida de subida en Mbps.
     * @param rttPromedioMs Latencia media de ida y vuelta en milisegundos.
     * @param jitterMs Fluctuación acumulada de fase en milisegundos.
     * @param perdidaPaquetesPct Porcentaje de paquetes perdidos (0.0 a 100.0%).
     * @return Objeto inmutable ResultadoDiagnosticoQoS con el índice, categoría
     * y diagnóstico.
     */
    public ResultadoDiagnosticoQoS evaluarCalidadEnlace(double descargaMbps,
            double subidaMbps,
            double rttPromedioMs,
            double jitterMs,
            double perdidaPaquetesPct) {
        double down = Math.max(0.0, descargaMbps);
        double up = Math.max(0.0, subidaMbps);
        double rtt = Math.max(0.0, rttPromedioMs);
        double jitter = Math.max(0.0, jitterMs);
        double loss = Math.min(100.0, Math.max(0.0, perdidaPaquetesPct));

        // 1. Calcular los 5 subíndices normalizados en escala [0.0, 100.0]
        double scoreDown = evaluarPuntajeDirecto(
                down,
                umbrales.getDescargaExcelenteMbps(),
                umbrales.getDescargaBuenaMbps(),
                umbrales.getDescargaDegradadaMbps()
        );

        double scoreUp = evaluarPuntajeDirecto(
                up,
                umbrales.getSubidaExcelenteMbps(),
                umbrales.getSubidaBuenaMbps(),
                umbrales.getSubidaDegradadaMbps()
        );

        double scoreRtt = (rtt <= 0.0 && loss >= 100.0) ? 0.0 : evaluarPuntajeInverso(
                rtt,
                umbrales.getRttExcelenteMs(),
                umbrales.getRttBuenoMs(),
                umbrales.getRttDegradadoMs(),
                300.0
        );

        double scoreJitter = (rtt <= 0.0 && loss >= 100.0) ? 0.0 : evaluarPuntajeInverso(
                jitter,
                umbrales.getJitterExcelenteMs(),
                umbrales.getJitterBuenoMs(),
                umbrales.getJitterDegradadoMs(),
                100.0
        );

        double scoreLoss = evaluarPuntajeInverso(
                loss,
                umbrales.getPerdidaExcelentePct(),
                umbrales.getPerdidaBuenaPct(),
                umbrales.getPerdidaDegradadaPct(),
                25.0
        );

        // 2. Calcular el Índice Global de Correlación QoS ponderado
        double indicePonderado = (PESO_DESCARGA * scoreDown)
                + (PESO_SUBIDA * scoreUp)
                + (PESO_LATENCIA * scoreRtt)
                + (PESO_JITTER * scoreJitter)
                + (PESO_PERDIDA * scoreLoss);

        indicePonderado = redondearDosDecimales(acotarRangoCien(indicePonderado));

        // 3. Determinar categoría base por índice global
        CategoriaQoS categoria = determinarCategoriaPorIndice(indicePonderado);

        // 4. Aplicar reglas de penalización por incumplimiento crítico de SLA (Veto Técnico)
        if (loss >= 20.0 || (down < 0.5 && up < 0.2) || (rtt >= 500.0)) {
            categoria = CategoriaQoS.CRITICA;
        } else if (loss > umbrales.getPerdidaDegradadaPct()
                || rtt > umbrales.getRttDegradadoMs()
                || jitter > umbrales.getJitterDegradadoMs()
                || down < umbrales.getDescargaDegradadaMbps()) {
            if (categoria == CategoriaQoS.EXCELENTE || categoria == CategoriaQoS.BUENA) {
                categoria = CategoriaQoS.DEGRADADA;
            }
        } else if (loss > umbrales.getPerdidaBuenaPct()
                || rtt > umbrales.getRttBuenoMs()
                || jitter > umbrales.getJitterBuenoMs()
                || down < umbrales.getDescargaBuenaMbps()) {
            if (categoria == CategoriaQoS.EXCELENTE) {
                categoria = CategoriaQoS.BUENA;
            }
        }

        // El enlace cumple el SLA si se clasifica como EXCELENTE o BUENA
        boolean cumpleSla = (categoria == CategoriaQoS.EXCELENTE || categoria == CategoriaQoS.BUENA);

        // 5. Construir diagnóstico técnico detallado para auditoría frente al ISP
        String diagnostico = construirDiagnosticoTecnico(down, up, rtt, jitter, loss, categoria);

        return new ResultadoDiagnosticoQoS(
                redondearDosDecimales(down),
                redondearDosDecimales(up),
                redondearDosDecimales(rtt),
                redondearDosDecimales(jitter),
                redondearDosDecimales(loss),
                redondearDosDecimales(scoreDown),
                redondearDosDecimales(scoreUp),
                redondearDosDecimales(scoreRtt),
                redondearDosDecimales(scoreJitter),
                redondearDosDecimales(scoreLoss),
                indicePonderado,
                categoria,
                cumpleSla,
                diagnostico
        );
    }

    /**
     * Evalúa métricas únicamente de canal (Ping, Jitter y Packet Loss) cuando
     * aún no se han corrido las fases de descarga/subida (útil para
     * prediagnóstico en el PanelMonitoreoQoS).
     */
    public ResultadoDiagnosticoQoS evaluarSoloCalidadCanal(ResumenQoS resumenQoS) {
        if (resumenQoS == null || resumenQoS.getPaquetesEnviados() == 0) {
            return evaluarCalidadEnlace(0.0, 0.0, 0.0, 0.0, 100.0);
        }
        double rtt = resumenQoS.getPaquetesRecibidos() > 0 ? resumenQoS.getRttPromedioMs() : 1000.0;
        double jitter = resumenQoS.getPaquetesRecibidos() > 0 ? resumenQoS.getJitterAcumuladoMs() : 100.0;
        double loss = resumenQoS.getPorcentajePerdida();

        double scoreRtt = evaluarPuntajeInverso(
                rtt, umbrales.getRttExcelenteMs(), umbrales.getRttBuenoMs(), umbrales.getRttDegradadoMs(), 300.0);
        double scoreJitter = evaluarPuntajeInverso(
                jitter, umbrales.getJitterExcelenteMs(), umbrales.getJitterBuenoMs(), umbrales.getJitterDegradadoMs(), 100.0);
        double scoreLoss = evaluarPuntajeInverso(
                loss, umbrales.getPerdidaExcelentePct(), umbrales.getPerdidaBuenaPct(), umbrales.getPerdidaDegradadaPct(), 25.0);

        double indiceCanal = redondearDosDecimales((0.40 * scoreRtt) + (0.25 * scoreJitter) + (0.35 * scoreLoss));
        CategoriaQoS categoria = determinarCategoriaPorIndice(indiceCanal);

        if (loss >= 20.0 || resumenQoS.getPaquetesRecibidos() == 0) {
            categoria = CategoriaQoS.CRITICA;
        } else if (loss > umbrales.getPerdidaDegradadaPct() || rtt > umbrales.getRttDegradadoMs()) {
            if (categoria == CategoriaQoS.EXCELENTE || categoria == CategoriaQoS.BUENA) {
                categoria = CategoriaQoS.DEGRADADA;
            }
        }

        boolean cumpleSla = (categoria == CategoriaQoS.EXCELENTE || categoria == CategoriaQoS.BUENA);
        String diagnostico = construirDiagnosticoTecnico(
                umbrales.getDescargaExcelenteMbps(), umbrales.getSubidaExcelenteMbps(), rtt, jitter, loss, categoria);

        return new ResultadoDiagnosticoQoS(
                0.0, 0.0,
                redondearDosDecimales(rtt),
                redondearDosDecimales(jitter),
                redondearDosDecimales(loss),
                0.0, 0.0,
                redondearDosDecimales(scoreRtt),
                redondearDosDecimales(scoreJitter),
                redondearDosDecimales(scoreLoss),
                indiceCanal,
                categoria,
                cumpleSla,
                diagnostico
        );
    }

    /**
     * Normaliza en escala [0, 100] una variable directa (donde MAYOR valor es
     * mejor: Download y Upload). - Valor >= umbralExcelente -> [85 .. 100] -
     * Valor en [umbralBueno, umbralExcelente) -> [70 .. 85) - Valor en
     * [umbralDegradado, umbralBueno) -> [45 .. 70) - Valor en [0,
     * umbralDegradado) -> [0 .. 45)
     */
    public static double evaluarPuntajeDirecto(double valor,
            double umbralExcelente,
            double umbralBueno,
            double umbralDegradado) {
        if (valor <= 0.0) {
            return 0.0;
        }
        if (valor >= umbralExcelente) {
            double extra = Math.min(1.0, (valor - umbralExcelente) / Math.max(1.0, umbralExcelente));
            return acotarRangoCien(85.0 + (15.0 * extra));
        }
        if (valor >= umbralBueno) {
            double fraccion = (valor - umbralBueno) / Math.max(0.001, (umbralExcelente - umbralBueno));
            return acotarRangoCien(70.0 + (15.0 * fraccion));
        }
        if (valor >= umbralDegradado) {
            double fraccion = (valor - umbralDegradado) / Math.max(0.001, (umbralBueno - umbralDegradado));
            return acotarRangoCien(45.0 + (25.0 * fraccion));
        }
        double fraccionCritica = valor / Math.max(0.001, umbralDegradado);
        return acotarRangoCien(45.0 * fraccionCritica);
    }

    /**
     * Normaliza en escala [0, 100] una variable inversa (donde MENOR valor es
     * mejor: RTT, Jitter, Packet Loss). - Valor <= umbralExcelente -> [85 ..
     * 100] - Valor en (umbralExcelente, umbralBueno] -> [70 .. 85) - Valor en
     * (umbralBueno, umbralDegradado] -> [45 .. 70) - Valor > umbralDegradado ->
     * decae desde 45 hasta 0 en limiteCriticoMax.
     */
    public static double evaluarPuntajeInverso(double valor,
            double umbralExcelente,
            double umbralBueno,
            double umbralDegradado,
            double limiteCriticoMax) {
        if (valor <= 0.0) {
            return 100.0;
        }
        if (valor <= umbralExcelente) {
            double fraccion = 1.0 - (valor / Math.max(0.001, umbralExcelente));
            return acotarRangoCien(85.0 + (15.0 * fraccion));
        }
        if (valor <= umbralBueno) {
            double fraccion = (umbralBueno - valor) / Math.max(0.001, (umbralBueno - umbralExcelente));
            return acotarRangoCien(70.0 + (15.0 * fraccion));
        }
        if (valor <= umbralDegradado) {
            double fraccion = (umbralDegradado - valor) / Math.max(0.001, (umbralDegradado - umbralBueno));
            return acotarRangoCien(45.0 + (25.0 * fraccion));
        }
        if (valor >= limiteCriticoMax) {
            return 0.0;
        }
        double fraccionCritica = (limiteCriticoMax - valor) / Math.max(0.001, (limiteCriticoMax - umbralDegradado));
        return acotarRangoCien(45.0 * fraccionCritica);
    }

    public static CategoriaQoS determinarCategoriaPorIndice(double indiceGlobalQoS) {
        if (indiceGlobalQoS >= 85.0) {
            return CategoriaQoS.EXCELENTE;
        }
        if (indiceGlobalQoS >= 70.0) {
            return CategoriaQoS.BUENA;
        }
        if (indiceGlobalQoS >= 45.0) {
            return CategoriaQoS.DEGRADADA;
        }
        return CategoriaQoS.CRITICA;
    }

    private String construirDiagnosticoTecnico(double down, double up,
            double rtt, double jitter,
            double loss, CategoriaQoS categoria) {
        StringBuilder hallazgos = new StringBuilder();

        if (loss > umbrales.getPerdidaDegradadaPct()) {
            hallazgos.append(String.format("Pérdida severa de paquetes (%.1f%% > %.1f%% SLA). ",
                    loss, umbrales.getPerdidaDegradadaPct()));
        } else if (loss > umbrales.getPerdidaExcelentePct()) {
            hallazgos.append(String.format("Pérdida leve de paquetes detectada (%.1f%%). ", loss));
        }

        if (rtt > umbrales.getRttDegradadoMs()) {
            hallazgos.append(String.format("Latencia elevada (%.1f ms > %.0f ms SLA). ",
                    rtt, umbrales.getRttDegradadoMs()));
        }

        if (jitter > umbrales.getJitterDegradadoMs()) {
            hallazgos.append(String.format("Alta fluctuación de fase/Jitter (%.1f ms: posible bufferbloat). ", jitter));
        }

        if (down > 0.0 && down < umbrales.getDescargaDegradadaMbps()) {
            hallazgos.append(String.format("Caudal de descarga insuficiente (%.2f Mbps < %.0f Mbps SLA). ",
                    down, umbrales.getDescargaDegradadaMbps()));
        }

        if (up > 0.0 && up < umbrales.getSubidaDegradadaMbps()) {
            hallazgos.append(String.format("Caudal de subida limitado (%.2f Mbps < %.0f Mbps SLA). ",
                    up, umbrales.getSubidaDegradadaMbps()));
        }

        if (hallazgos.length() == 0) {
            return categoria.getDescripcionSla();
        }
        return hallazgos.toString().trim();
    }

    public UmbralesSLA getUmbrales() {
        return umbrales;
    }

    private static double acotarRangoCien(double valor) {
        return Math.min(100.0, Math.max(0.0, valor));
    }

    private static double redondearDosDecimales(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
