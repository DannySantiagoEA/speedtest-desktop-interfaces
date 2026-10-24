package com.speedtest.network;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.network.MedidorQoS.ResumenQoS;

/**
 * Interfaz de callback para notificar en tiempo real (sobre el Event Dispatch
 * Thread) el avance del muestreo concurrente de Latencia, Jitter y Pérdida de
 * Paquetes (HU-03).
 */
public interface EscuchadorProgresoQoS {

    /**
     * Se invoca en el EDT cada vez que el hilo secundario captura una nueva
     * muestra (cada 200 ms).
     *
     * @param muestra Muestra elemental recién capturada (RTT_i, |ΔRTT|,
     * pérdida).
     * @param resumenAcumulado Estado estadístico parcial acumulado hasta el
     * paquete actual.
     * @param porcentajeAvance Progreso porcentual de la ráfaga (0 a 100%).
     */
    void alRecibirMuestra(MuestraTelemetria muestra, ResumenQoS resumenAcumulado, int porcentajeAvance);

    /**
     * Se invoca en el EDT cuando el worker finaliza toda la secuencia de
     * paquetes.
     *
     * @param resumenFinal Estadísticas consolidadas del ensayo (Ping promedio,
     * Jitter, Packet Loss %).
     * @param cancelado true si el ensayo fue abortado manualmente por el
     * operador; false si concluyó normal.
     */
    void alFinalizarMuestreo(ResumenQoS resumenFinal, boolean cancelado);

    /**
     * Se invoca en el EDT si ocurre un error crítico de resolución DNS o red
     * antes de iniciar la ráfaga.
     *
     * @param mensajeError Descripción técnica del fallo.
     */
    void alOcurrirError(String mensajeError);
}
