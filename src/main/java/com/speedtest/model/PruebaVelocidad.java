package com.speedtest.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tarea #32 (HU-07 / RF-BD-02): Entidad de dominio que representa la sesión
 * consolidada de una prueba de rendimiento y Calidad de Servicio (QoS). Mapea
 * directamente hacia la tabla padre 'prueba_velocidad' en MySQL y mantiene la
 * relación 1:N con su lista de registros hijos 'MuestraTelemetria'.
 */
public class PruebaVelocidad {

    public static final String ESTADO_COMPLETADA = "Completada";
    public static final String ESTADO_CANCELADA = "Cancelada";
    public static final String ESTADO_FALLIDA = "Fallida";

    private int idPrueba;
    private int idUsuario;
    private LocalDateTime fechaHoraEnsayo;
    private String ipOrigen;
    private String proveedorIsp;
    private String servidorDestino;
    private double pingPromedio;
    private double jitterTotal;
    private double bajadaMaxima;
    private double subidaMaxima;
    private double perdidaPaquetes;
    private double indiceQos;
    private String calificacionQos;
    private String estadoSesion;
    private String notasAuditoria;

    /**
     * Relación 1:N con las muestras temporales de 200 ms asociadas a la sesión.
     */
    private List<MuestraTelemetria> muestrasTelemetria;

    public PruebaVelocidad() {
        this.fechaHoraEnsayo = LocalDateTime.now();
        this.ipOrigen = "127.0.0.1";
        this.proveedorIsp = "Red Local / ISP";
        this.estadoSesion = ESTADO_COMPLETADA;
        this.notasAuditoria = "";
        this.muestrasTelemetria = new ArrayList<>();
    }

    public PruebaVelocidad(int idUsuario, String ipOrigen, String proveedorIsp,
            String servidorDestino, double pingPromedio, double jitterTotal,
            double bajadaMaxima, double subidaMaxima, double perdidaPaquetes,
            double indiceQos, String calificacionQos, String estadoSesion,
            String notasAuditoria) {
        this();
        this.idUsuario = idUsuario;
        this.ipOrigen = (ipOrigen != null && !ipOrigen.isBlank()) ? ipOrigen : "127.0.0.1";
        this.proveedorIsp = (proveedorIsp != null && !proveedorIsp.isBlank()) ? proveedorIsp : "Red Local / ISP";
        this.servidorDestino = servidorDestino;
        this.pingPromedio = pingPromedio;
        this.jitterTotal = jitterTotal;
        this.bajadaMaxima = bajadaMaxima;
        this.subidaMaxima = subidaMaxima;
        this.perdidaPaquetes = perdidaPaquetes;
        this.indiceQos = indiceQos;
        this.calificacionQos = calificacionQos;
        this.estadoSesion = (estadoSesion != null && !estadoSesion.isBlank()) ? estadoSesion : ESTADO_COMPLETADA;
        this.notasAuditoria = notasAuditoria;
    }

    public int getIdPrueba() {
        return idPrueba;
    }

    public void setIdPrueba(int idPrueba) {
        this.idPrueba = idPrueba;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public LocalDateTime getFechaHoraEnsayo() {
        return fechaHoraEnsayo;
    }

    public void setFechaHoraEnsayo(LocalDateTime fechaHoraEnsayo) {
        this.fechaHoraEnsayo = fechaHoraEnsayo;
    }

    public String getIpOrigen() {
        return ipOrigen;
    }

    public void setIpOrigen(String ipOrigen) {
        this.ipOrigen = ipOrigen;
    }

    public String getProveedorIsp() {
        return proveedorIsp;
    }

    public void setProveedorIsp(String proveedorIsp) {
        this.proveedorIsp = proveedorIsp;
    }

    public String getServidorDestino() {
        return servidorDestino;
    }

    public void setServidorDestino(String servidorDestino) {
        this.servidorDestino = servidorDestino;
    }

    public double getPingPromedio() {
        return pingPromedio;
    }

    public void setPingPromedio(double pingPromedio) {
        this.pingPromedio = pingPromedio;
    }

    public double getJitterTotal() {
        return jitterTotal;
    }

    public void setJitterTotal(double jitterTotal) {
        this.jitterTotal = jitterTotal;
    }

    public double getBajadaMaxima() {
        return bajadaMaxima;
    }

    public void setBajadaMaxima(double bajadaMaxima) {
        this.bajadaMaxima = bajadaMaxima;
    }

    public double getSubidaMaxima() {
        return subidaMaxima;
    }

    public void setSubidaMaxima(double subidaMaxima) {
        this.subidaMaxima = subidaMaxima;
    }

    public double getPerdidaPaquetes() {
        return perdidaPaquetes;
    }

    public void setPerdidaPaquetes(double perdidaPaquetes) {
        this.perdidaPaquetes = perdidaPaquetes;
    }

    public double getIndiceQos() {
        return indiceQos;
    }

    public void setIndiceQos(double indiceQos) {
        this.indiceQos = indiceQos;
    }

    public String getCalificacionQos() {
        return calificacionQos;
    }

    public void setCalificacionQos(String calificacionQos) {
        this.calificacionQos = calificacionQos;
    }

    public String getEstadoSesion() {
        return estadoSesion;
    }

    public void setEstadoSesion(String estadoSesion) {
        this.estadoSesion = estadoSesion;
    }

    public String getNotasAuditoria() {
        return notasAuditoria;
    }

    public void setNotasAuditoria(String notasAuditoria) {
        this.notasAuditoria = notasAuditoria;
    }

    public List<MuestraTelemetria> getMuestrasTelemetria() {
        return muestrasTelemetria;
    }

    public void setMuestrasTelemetria(List<MuestraTelemetria> muestrasTelemetria) {
        this.muestrasTelemetria = (muestrasTelemetria != null) ? muestrasTelemetria : new ArrayList<>();
    }

    public void agregarMuestra(MuestraTelemetria muestra) {
        if (muestra != null) {
            this.muestrasTelemetria.add(muestra);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "PruebaVelocidad [ID=%d | Usuario=%d | Destino=%s | Down=%.2f Mbps | Up=%.2f Mbps | "
                + "Ping=%.2f ms | Jitter=%.2f ms | Loss=%.1f%% | QoS=%s (%.1f/100) | Muestras=%d]",
                idPrueba, idUsuario, servidorDestino, bajadaMaxima, subidaMaxima,
                pingPromedio, jitterTotal, perdidaPaquetes, calificacionQos, indiceQos,
                (muestrasTelemetria != null ? muestrasTelemetria.size() : 0)
        );
    }
}
