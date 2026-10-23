package com.speedtest.model;

import java.time.LocalDateTime;

/**
 * Entidad de dominio que representa un registro elemental de la serie temporal.
 * Mapea directamente hacia la tabla 'muestra_telemetria' en MySQL (HU-03,
 * HU-04, HU-07).
 */
public class MuestraTelemetria {

    public static final String ETAPA_PING = "PING";
    public static final String ETAPA_DOWN = "DOWN";
    public static final String ETAPA_UP = "UP";

    private long idMuestra;
    private int idPrueba;
    private int numeroSecuencia;
    private LocalDateTime timestamp;
    private double tiempoTranscurridoSeg;
    private String etapa;             // 'PING', 'DOWN', 'UP'
    private long bytesTransferidos;
    private double velocidadInstMbps; // Para fases DOWN y UP
    private double latenciaInstMs;    // RTT_i en ms (o -1 si el paquete se perdió)
    private double jitterInstMs;      // |RTT_i - RTT_{i-1}| en ms
    private boolean paquetePerdido;

    public MuestraTelemetria() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor específico para muestras de sondeo de calidad de canal (Fase
     * PING / HU-03).
     */
    public static MuestraTelemetria crearMuestraPing(int numeroSecuencia, double tiempoSeg,
            double rttMs, double jitterInstMs,
            boolean paquetePerdido) {
        MuestraTelemetria m = new MuestraTelemetria();
        m.setNumeroSecuencia(numeroSecuencia);
        m.setTiempoTranscurridoSeg(tiempoSeg);
        m.setEtapa(ETAPA_PING);
        m.setBytesTransferidos(paquetePerdido ? 0L : 64L);
        m.setVelocidadInstMbps(0.0);
        m.setLatenciaInstMs(paquetePerdido ? 0.0 : rttMs);
        m.setJitterInstMs(paquetePerdido ? 0.0 : jitterInstMs);
        m.setPaquetePerdido(paquetePerdido);
        return m;
    }

    public long getIdMuestra() {
        return idMuestra;
    }

    public void setIdMuestra(long idMuestra) {
        this.idMuestra = idMuestra;
    }

    public int getIdPrueba() {
        return idPrueba;
    }

    public void setIdPrueba(int idPrueba) {
        this.idPrueba = idPrueba;
    }

    public int getNumeroSecuencia() {
        return numeroSecuencia;
    }

    public void setNumeroSecuencia(int numeroSecuencia) {
        this.numeroSecuencia = numeroSecuencia;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public double getTiempoTranscurridoSeg() {
        return tiempoTranscurridoSeg;
    }

    public void setTiempoTranscurridoSeg(double tiempoTranscurridoSeg) {
        this.tiempoTranscurridoSeg = tiempoTranscurridoSeg;
    }

    public String getEtapa() {
        return etapa;
    }

    public void setEtapa(String etapa) {
        this.etapa = etapa;
    }

    public long getBytesTransferidos() {
        return bytesTransferidos;
    }

    public void setBytesTransferidos(long bytesTransferidos) {
        this.bytesTransferidos = bytesTransferidos;
    }

    public double getVelocidadInstMbps() {
        return velocidadInstMbps;
    }

    public void setVelocidadInstMbps(double velocidadInstMbps) {
        this.velocidadInstMbps = velocidadInstMbps;
    }

    public double getLatenciaInstMs() {
        return latenciaInstMs;
    }

    public void setLatenciaInstMs(double latenciaInstMs) {
        this.latenciaInstMs = latenciaInstMs;
    }

    public double getJitterInstMs() {
        return jitterInstMs;
    }

    public void setJitterInstMs(double jitterInstMs) {
        this.jitterInstMs = jitterInstMs;
    }

    public boolean isPaquetePerdido() {
        return paquetePerdido;
    }

    public void setPaquetePerdido(boolean paquetePerdido) {
        this.paquetePerdido = paquetePerdido;
    }

    @Override
    public String toString() {
        if (paquetePerdido) {
            return String.format("[Seq #%02d | t=%.2fs] PAQUETE PERDIDO (Timeout)",
                    numeroSecuencia, tiempoTranscurridoSeg);
        }
        return String.format("[Seq #%02d | t=%.2fs] RTT_i = %.2f ms | |ΔRTT| = %.2f ms",
                numeroSecuencia, tiempoTranscurridoSeg, latenciaInstMs, jitterInstMs);
    }
}
