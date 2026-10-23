package com.speedtest.network;

import com.speedtest.model.MuestraTelemetria;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tarea #12 (HU-03 / RF-ADQ-01): Motor matemático para el cálculo de RTT
 * (Ping), variabilidad temporal del retardo (Jitter: |RTT_i - RTT_{i-1}|) y
 * porcentaje de paquetes perdidos (Packet Loss %).
 *
 * Diseñado como componente thread-safe para ser invocado desde hilos
 * secundarios (Workers).
 */
public class MedidorQoS {

    /**
     * Objeto inmutable de transferencia (DTO) con el resumen estadístico
     * instantáneo o final.
     */
    public static class ResumenQoS {

        private final int paquetesEnviados;
        private final int paquetesRecibidos;
        private final int paquetesPerdidos;
        private final double rttUltimoMs;
        private final double rttPromedioMs;
        private final double rttMinimoMs;
        private final double rttMaximoMs;
        private final double jitterInstantaneoMs;
        private final double jitterAcumuladoMs;
        private final double porcentajePerdida;

        public ResumenQoS(int paquetesEnviados, int paquetesRecibidos, int paquetesPerdidos,
                double rttUltimoMs, double rttPromedioMs, double rttMinimoMs,
                double rttMaximoMs, double jitterInstantaneoMs,
                double jitterAcumuladoMs, double porcentajePerdida) {
            this.paquetesEnviados = paquetesEnviados;
            this.paquetesRecibidos = paquetesRecibidos;
            this.paquetesPerdidos = paquetesPerdidos;
            this.rttUltimoMs = rttUltimoMs;
            this.rttPromedioMs = rttPromedioMs;
            this.rttMinimoMs = rttMinimoMs;
            this.rttMaximoMs = rttMaximoMs;
            this.jitterInstantaneoMs = jitterInstantaneoMs;
            this.jitterAcumuladoMs = jitterAcumuladoMs;
            this.porcentajePerdida = porcentajePerdida;
        }

        public int getPaquetesEnviados() {
            return paquetesEnviados;
        }

        public int getPaquetesRecibidos() {
            return paquetesRecibidos;
        }

        public int getPaquetesPerdidos() {
            return paquetesPerdidos;
        }

        public double getRttUltimoMs() {
            return rttUltimoMs;
        }

        public double getRttPromedioMs() {
            return rttPromedioMs;
        }

        public double getRttMinimoMs() {
            return rttMinimoMs;
        }

        public double getRttMaximoMs() {
            return rttMaximoMs;
        }

        public double getJitterInstantaneoMs() {
            return jitterInstantaneoMs;
        }

        public double getJitterAcumuladoMs() {
            return jitterAcumuladoMs;
        }

        public double getPorcentajePerdida() {
            return porcentajePerdida;
        }

        @Override
        public String toString() {
            return String.format(
                    "Enviados: %d | Recibidos: %d | Perdidos: %d (%.1f%%) | "
                    + "RTT Prom: %.2f ms (Min: %.2f, Max: %.2f) | Jitter: %.2f ms",
                    paquetesEnviados, paquetesRecibidos, paquetesPerdidos, porcentajePerdida,
                    rttPromedioMs, rttMinimoMs, rttMaximoMs, jitterAcumuladoMs
            );
        }
    }

    // =========================================================================
    // ATRIBUTOS DE ESTADO ACUMULADO DE LA SESIÓN DE SONDEO
    // =========================================================================
    private int paquetesEnviados;
    private int paquetesRecibidos;
    private int paquetesPerdidos;

    private double sumaRttMs;
    private double rttMinimoMs;
    private double rttMaximoMs;
    private double rttAnteriorValidoMs;
    private double rttUltimoMs;

    private double sumaDiferenciasJitterMs;
    private int totalParesConsecutivosJitter;
    private double jitterInstantaneoMs;

    private final List<Double> serieRttValidos;
    private final List<MuestraTelemetria> historialMuestras;

    public MedidorQoS() {
        this.serieRttValidos = new ArrayList<>();
        this.historialMuestras = new ArrayList<>();
        reiniciar();
    }

    /**
     * Reinicia todos los acumuladores estadísticos antes de comenzar un nuevo
     * ensayo.
     */
    public synchronized void reiniciar() {
        this.paquetesEnviados = 0;
        this.paquetesRecibidos = 0;
        this.paquetesPerdidos = 0;
        this.sumaRttMs = 0.0;
        this.rttMinimoMs = Double.MAX_VALUE;
        this.rttMaximoMs = 0.0;
        this.rttAnteriorValidoMs = -1.0;
        this.rttUltimoMs = 0.0;
        this.sumaDiferenciasJitterMs = 0.0;
        this.totalParesConsecutivosJitter = 0;
        this.jitterInstantaneoMs = 0.0;
        this.serieRttValidos.clear();
        this.historialMuestras.clear();
    }

    /**
     * Registra una muestra exitosa de tiempo de respuesta (RTT_i > 0) y
     * actualiza en tiempo real el promedio de RTT, el Jitter |RTT_i -
     * RTT_{i-1}| y el Packet Loss %.
     *
     * @param rttMs Tiempo de ida y vuelta medido en milisegundos.
     * @param tiempoTranscurridoSeg Segundos transcurridos desde el inicio de la
     * fase.
     * @return Instantánea ResumenQoS actualizada.
     */
    public synchronized ResumenQoS registrarMuestraExitosa(double rttMs, double tiempoTranscurridoSeg) {
        if (rttMs < 0) {
            return registrarPaquetePerdido(tiempoTranscurridoSeg);
        }

        paquetesEnviados++;
        paquetesRecibidos++;
        rttUltimoMs = rttMs;
        sumaRttMs += rttMs;
        serieRttValidos.add(rttMs);

        if (rttMs < rttMinimoMs) {
            rttMinimoMs = rttMs;
        }
        if (rttMs > rttMaximoMs) {
            rttMaximoMs = rttMs;
        }

        // Cálculo de desviación temporal consecutiva: |RTT_i - RTT_{i-1}|
        if (rttAnteriorValidoMs >= 0.0) {
            jitterInstantaneoMs = Math.abs(rttMs - rttAnteriorValidoMs);
            sumaDiferenciasJitterMs += jitterInstantaneoMs;
            totalParesConsecutivosJitter++;
        } else {
            jitterInstantaneoMs = 0.0;
        }
        rttAnteriorValidoMs = rttMs;

        MuestraTelemetria muestra = MuestraTelemetria.crearMuestraPing(
                paquetesEnviados,
                tiempoTranscurridoSeg,
                rttMs,
                jitterInstantaneoMs,
                false
        );
        historialMuestras.add(muestra);

        return obtenerResumenActual();
    }

    /**
     * Registra un paquete de sondeo sin respuesta (Timeout o host inalcanzable)
     * e incrementa la relación porcentual: (paquetesPerdidos /
     * paquetesEnviados) * 100.
     *
     * @param tiempoTranscurridoSeg Segundos transcurridos desde el inicio de la
     * fase.
     * @return Instantánea ResumenQoS actualizada.
     */
    public synchronized ResumenQoS registrarPaquetePerdido(double tiempoTranscurridoSeg) {
        paquetesEnviados++;
        paquetesPerdidos++;
        jitterInstantaneoMs = 0.0;

        MuestraTelemetria muestra = MuestraTelemetria.crearMuestraPing(
                paquetesEnviados,
                tiempoTranscurridoSeg,
                0.0,
                0.0,
                true
        );
        historialMuestras.add(muestra);

        return obtenerResumenActual();
    }

    /**
     * Calcula el promedio aritmético de los tiempos RTT recibidos: Σ(RTT_i) /
     * M.
     */
    public synchronized double calcularRttPromedio() {
        if (paquetesRecibidos == 0) {
            return 0.0;
        }
        return redondearDosDecimales(sumaRttMs / paquetesRecibidos);
    }

    /**
     * Calcula el Jitter acumulado promedio como la media de las desviaciones
     * absolutas consecutivas: Σ|RTT_i - RTT_{i-1}| / (M - 1).
     */
    public synchronized double calcularJitterAcumulado() {
        if (totalParesConsecutivosJitter == 0) {
            return 0.0;
        }
        return redondearDosDecimales(sumaDiferenciasJitterMs / totalParesConsecutivosJitter);
    }

    /**
     * Calcula el porcentaje de pérdida de paquetes: (paquetesPerdidos /
     * paquetesEnviados) * 100.
     */
    public synchronized double calcularPorcentajePerdida() {
        if (paquetesEnviados == 0) {
            return 0.0;
        }
        double porcentaje = ((double) paquetesPerdidos / (double) paquetesEnviados) * 100.0;
        return redondearDosDecimales(porcentaje);
    }

    /**
     * Evalúa una serie completa de tiempos RTT (donde valores <= 0 o null
     * representan pérdidas) en una sola llamada estática/utilitaria.
     */
    public synchronized ResumenQoS evaluarSerieTiempos(List<Double> serieTiemposMs, double intervaloMuestreoSeg) {
        reiniciar();
        if (serieTiemposMs == null || serieTiemposMs.isEmpty()) {
            return obtenerResumenActual();
        }
        for (int i = 0; i < serieTiemposMs.size(); i++) {
            Double valor = serieTiemposMs.get(i);
            double tSeg = (i + 1) * intervaloMuestreoSeg;
            if (valor == null || valor <= 0.0) {
                registrarPaquetePerdido(tSeg);
            } else {
                registrarMuestraExitosa(valor, tSeg);
            }
        }
        return obtenerResumenActual();
    }

    /**
     * Construye el objeto inmutable ResumenQoS con el estado matemático actual.
     */
    public synchronized ResumenQoS obtenerResumenActual() {
        double minVal = (paquetesRecibidos > 0) ? redondearDosDecimales(rttMinimoMs) : 0.0;
        double maxVal = (paquetesRecibidos > 0) ? redondearDosDecimales(rttMaximoMs) : 0.0;

        return new ResumenQoS(
                paquetesEnviados,
                paquetesRecibidos,
                paquetesPerdidos,
                redondearDosDecimales(rttUltimoMs),
                calcularRttPromedio(),
                minVal,
                maxVal,
                redondearDosDecimales(jitterInstantaneoMs),
                calcularJitterAcumulado(),
                calcularPorcentajePerdida()
        );
    }

    public synchronized List<MuestraTelemetria> getHistorialMuestras() {
        return Collections.unmodifiableList(new ArrayList<>(historialMuestras));
    }

    public synchronized List<Double> getSerieRttValidos() {
        return Collections.unmodifiableList(new ArrayList<>(serieRttValidos));
    }

    private static double redondearDosDecimales(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
