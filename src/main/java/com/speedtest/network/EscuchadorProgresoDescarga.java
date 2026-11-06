package com.speedtest.network;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.network.MotorDescargaWorker.ResumenDescarga;

/**
 * Tarea #17 (HU-04 / RF-ADQ-02, RF-ADQ-04): Interfaz de callback para despachar
 * hacia el Event Dispatch Thread (EDT) cada muestra instantánea de descarga
 * capturada en ventanas de Δt = 200 ms.
 */
public interface EscuchadorProgresoDescarga {

    /**
     * Se invoca en el EDT cada vez que se cumple un intervalo de muestreo Δt =
     * 200 ms.
     *
     * @param muestra Muestra instantánea de la fase DOWN con su velocidad en
     * Mbps.
     * @param resumenParcial Estadísticas acumuladas de la descarga hasta el
     * instante actual.
     * @param porcentajeAvance Porcentaje de progreso de la fase de descarga (0
     * a 100%).
     */
    void alRecibirMuestraDescarga(MuestraTelemetria muestra, ResumenDescarga resumenParcial, int porcentajeAvance);

    /**
     * Se invoca en el EDT cuando concluye la prueba de descarga o cuando es
     * abortada manualmente.
     *
     * @param resumenFinal Resumen estadístico consolidado (Throughput promedio,
     * pico, bytes y tiempo).
     * @param cancelado true si fue cancelada manualmente por el operador; false
     * si terminó normalmente.
     */
    void alFinalizarDescarga(ResumenDescarga resumenFinal, boolean cancelado);

    /**
     * Se invoca en el EDT si ocurre una excepción de red o imposibilidad de
     * conexión al servidor.
     *
     * @param mensajeError Descripción técnica del error.
     */
    void alOcurrirErrorDescarga(String mensajeError);
}
