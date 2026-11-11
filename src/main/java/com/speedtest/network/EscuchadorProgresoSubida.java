package com.speedtest.network;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.network.MotorSubidaWorker.ResumenSubida;

/**
 * Tarea #18 (HU-04 / RF-ADQ-03, RF-ADQ-04): Interfaz de callback para despachar
 * hacia el Event Dispatch Thread (EDT) cada muestra instantánea de velocidad de
 * subida (Upload) evaluada en intervalos de Δt = 200 ms.
 */
public interface EscuchadorProgresoSubida {

    /**
     * Se invoca en el EDT cada vez que se completa una ventana de transmisión
     * de 200 ms.
     *
     * @param muestra Muestra instantánea de la fase UP con su velocidad en
     * Mbps.
     * @param resumenParcial Estadísticas acumuladas de subida hasta el instante
     * actual.
     * @param porcentajeAvance Porcentaje de progreso de la fase de subida (0 a
     * 100%).
     */
    void alRecibirMuestraSubida(MuestraTelemetria muestra, ResumenSubida resumenParcial, int porcentajeAvance);

    /**
     * Se invoca en el EDT cuando concluye la transmisión de subida o cuando es
     * abortada manualmente.
     *
     * @param resumenFinal Resumen estadístico consolidado (Throughput promedio,
     * pico, bytes enviados y duración).
     * @param cancelado true si fue cancelada manualmente por el operador; false
     * si terminó normalmente.
     */
    void alFinalizarSubida(ResumenSubida resumenFinal, boolean cancelado);

    /**
     * Se invoca en el EDT si ocurre una excepción de red o fallo al abrir el
     * canal de transmisión.
     *
     * @param mensajeError Descripción técnica del error ocurrido.
     */
    void alOcurrirErrorSubida(String mensajeError);
}
