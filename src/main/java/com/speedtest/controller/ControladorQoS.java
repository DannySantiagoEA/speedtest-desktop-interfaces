package com.speedtest.controller;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.network.EscuchadorProgresoQoS;
import com.speedtest.network.MedidorQoS.ResumenQoS;
import com.speedtest.network.MotorAdquisicionQoSWorker;
import com.speedtest.view.PanelMonitoreoQoS;
import com.speedtest.view.PanelSeleccionServidor;
import javax.swing.JOptionPane;

/**
 * Tarea #14 (HU-03 / RF-ADQ-01, RF-ADQ-04, RF-ADQ-05): Controlador que orquesta
 * el inicio, recepción de telemetría en tiempo real y cancelación segura de
 * sockets del motor concurrente de Latencia, Jitter y Pérdida de Paquetes.
 */
public class ControladorQoS {

    private final PanelSeleccionServidor panelServidor;
    private final PanelMonitoreoQoS panelQoS;
    private MotorAdquisicionQoSWorker workerActivo;

    public ControladorQoS(PanelSeleccionServidor panelServidor, PanelMonitoreoQoS panelQoS) {
        this.panelServidor = panelServidor;
        this.panelQoS = panelQoS;
        enlazarEventos();
    }

    private void enlazarEventos() {
        this.panelQoS.addIniciarSondeoListener(e -> iniciarMuestreoConcurrente());
        this.panelQoS.addCancelarSondeoListener(e -> cancelarMuestreoEnCurso());
    }

    public void iniciarMuestreoConcurrente() {
        // Evitar ejecuciones simultáneas
        if (workerActivo != null && !workerActivo.isDone()) {
            return;
        }

        String host = panelServidor.getHostConfigurado();
        String puertoTexto = panelServidor.getPuertoTexto();

        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(
                    panelQoS,
                    "Seleccione un servidor o ingrese un Host/IP válido antes de iniciar el sondeo.",
                    "Host Requerido",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        final int puerto;
        try {
            puerto = Integer.parseInt(puertoTexto);
            if (puerto < 1 || puerto > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    panelQoS,
                    "El puerto TCP debe ser un número entero entre 1 y 65535.",
                    "Puerto Inválido",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int totalPaquetes = panelQoS.getCantidadPaquetesConfigurada();

        panelQoS.configurarEstadoEjecucion(true);
        panelQoS.prepararNuevoEnsayo(host, puerto, totalPaquetes);

        // Instanciar el Worker en segundo plano (Tarea #13 y #14)
        workerActivo = new MotorAdquisicionQoSWorker(
                host,
                puerto,
                totalPaquetes,
                MotorAdquisicionQoSWorker.INTERVALO_MUESTREO_DEFECTO_MS,
                MotorAdquisicionQoSWorker.TIMEOUT_PAQUETE_MS,
                new EscuchadorProgresoQoS() {
            @Override
            public void alRecibirMuestra(MuestraTelemetria muestra, ResumenQoS resumenAcumulado, int porcentajeAvance) {
                panelQoS.actualizarMuestraEnVivo(muestra, resumenAcumulado, porcentajeAvance, totalPaquetes);
            }

            @Override
            public void alFinalizarMuestreo(ResumenQoS resumenFinal, boolean cancelado) {
                panelQoS.configurarEstadoEjecucion(false);
                panelQoS.mostrarCierreEnsayo(resumenFinal, cancelado);
                System.out.println(cancelado
                        ? "[QoS CANCELADO] " + resumenFinal
                        : "[QoS COMPLETADO] " + resumenFinal);
            }

            @Override
            public void alOcurrirError(String mensajeError) {
                panelQoS.configurarEstadoEjecucion(false);
                JOptionPane.showMessageDialog(
                        panelQoS,
                        "Error al ejecutar el motor de sondeo QoS:\n" + mensajeError,
                        "Error de Red",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
        );

        workerActivo.execute();
    }

    /**
     * Tarea #14 (RF-ADQ-05): Interrumpe inmediatamente el hilo secundario y
     * cierra de forma forzosa y segura el socket activo sin dejar bloqueado el
     * proceso.
     */
    public void cancelarMuestreoEnCurso() {
        if (workerActivo != null && !workerActivo.isDone()) {
            System.out.println("[RF-ADQ-05] Solicitando cancelación segura y cierre de socket activo...");
            workerActivo.solicitarCancelacionSegura();
        }
    }
}
