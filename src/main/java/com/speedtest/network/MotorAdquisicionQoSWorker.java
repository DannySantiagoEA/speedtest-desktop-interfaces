package com.speedtest.network;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.network.MedidorQoS.ResumenQoS;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingWorker;

/**
 * Tarea #13 (HU-03 / RF-ADQ-01, RF-ADQ-04): Worker concurrente en segundo plano
 * desacoplado del Event Dispatch Thread (EDT) para la adquisición periódica de
 * Latencia (RTT), Jitter y Pérdida de Paquetes (Packet Loss %).
 *
 * Extiende SwingWorker para ejecutar la apertura de sockets en un hilo de fondo
 * y despachar las actualizaciones parciales hacia la GUI a través de
 * publish/process.
 */
public class MotorAdquisicionQoSWorker extends SwingWorker<ResumenQoS, MuestraTelemetria> {

    /**
     * Cadencia de muestreo estándar exigida por el proyecto (200 ms = 5 Hz).
     */
    public static final int INTERVALO_MUESTREO_DEFECTO_MS = 200;

    /**
     * Cantidad predeterminada de paquetes por ráfaga de auditoría QoS (25
     * muestras = 5 segundos).
     */
    public static final int TOTAL_PAQUETES_DEFECTO = 25;

    /**
     * Timeout máximo por cada paquete individual de la ráfaga (1000 ms).
     */
    public static final int TIMEOUT_PAQUETE_MS = 1000;

    private final String host;
    private final int puerto;
    private final int totalPaquetes;
    private final int intervaloMuestreoMs;
    private final int timeoutPaqueteMs;

    private final MedidorQoS medidorQoS;
    private final EscuchadorProgresoQoS escuchador;

    /**
     * Referencia atómica al socket en curso para permitir su cierre inmediato
     * ante cancelación (RF-ADQ-05).
     */
    private final AtomicReference<Socket> socketActivoRef;
    private final AtomicBoolean solicitudCancelacion;

    /**
     * Constructor con parámetros estándar de muestreo (25 paquetes cada 200
     * ms).
     */
    public MotorAdquisicionQoSWorker(String host, int puerto, EscuchadorProgresoQoS escuchador) {
        this(host, puerto, TOTAL_PAQUETES_DEFECTO, INTERVALO_MUESTREO_DEFECTO_MS, TIMEOUT_PAQUETE_MS, escuchador);
    }

    /**
     * Constructor completo parametrizable para ráfagas personalizadas.
     */
    public MotorAdquisicionQoSWorker(String host, int puerto, int totalPaquetes,
            int intervaloMuestreoMs, int timeoutPaqueteMs,
            EscuchadorProgresoQoS escuchador) {
        this.host = (host != null) ? host.trim() : "";
        this.puerto = puerto;
        this.totalPaquetes = Math.max(2, totalPaquetes);
        this.intervaloMuestreoMs = Math.max(50, intervaloMuestreoMs);
        this.timeoutPaqueteMs = Math.max(200, timeoutPaqueteMs);
        this.medidorQoS = new MedidorQoS();
        this.escuchador = escuchador;
        this.socketActivoRef = new AtomicReference<>(null);
        this.solicitudCancelacion = new AtomicBoolean(false);
    }

    // =========================================================================
    // 1. EJECUCIÓN EN HILO SECUNDARIO (BACKGROUND THREAD - FUERA DEL EDT)
    // =========================================================================
    @Override
    protected ResumenQoS doInBackground() throws Exception {
        medidorQoS.reiniciar();

        if (host.isEmpty()) {
            throw new IllegalArgumentException("El host de destino no puede estar vacío.");
        }

        // Resolución DNS previa fuera del EDT
        final InetAddress direccionDestino;
        try {
            direccionDestino = InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            throw new IOException("No fue posible resolver el dominio o dirección IP: " + host, ex);
        }

        long instanteInicioEnsayoNano = System.nanoTime();

        for (int seq = 1; seq <= totalPaquetes; seq++) {
            // Verificar si el hilo fue cancelado o interrumpido antes de enviar el siguiente paquete
            if (isCancelled() || solicitudCancelacion.get() || Thread.currentThread().isInterrupted()) {
                break;
            }

            long inicioIteracionNano = System.nanoTime();
            double tiempoTranscurridoSeg = (inicioIteracionNano - instanteInicioEnsayoNano) / 1_000_000_000.0;

            // Medir RTT_i del paquete actual mediante Socket TCP con respaldo ICMP
            double rttMedidoMs = enviarPaqueteSondeo(direccionDestino, puerto, timeoutPaqueteMs);

            // Si durante el bloqueo del socket el usuario canceló la prueba, salimos limpiamente
            if (isCancelled() || solicitudCancelacion.get()) {
                break;
            }

            if (rttMedidoMs >= 0.0) {
                medidorQoS.registrarMuestraExitosa(rttMedidoMs, tiempoTranscurridoSeg);
            } else {
                medidorQoS.registrarPaquetePerdido(tiempoTranscurridoSeg);
            }

            // Obtener la última muestra generada y publicarla hacia el EDT
            List<MuestraTelemetria> historial = medidorQoS.getHistorialMuestras();
            if (!historial.isEmpty()) {
                MuestraTelemetria ultimaMuestra = historial.get(historial.size() - 1);
                publish(ultimaMuestra);
            }

            // Pausa controlada para mantener la cadencia periódica (200 ms) sin bloquear el EDT
            if (seq < totalPaquetes && !isCancelled() && !solicitudCancelacion.get()) {
                long duracionSondeoMs = (System.nanoTime() - inicioIteracionNano) / 1_000_000L;
                long tiempoEsperaRestanteMs = intervaloMuestreoMs - duracionSondeoMs;
                if (tiempoEsperaRestanteMs > 0) {
                    try {
                        Thread.sleep(tiempoEsperaRestanteMs);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return medidorQoS.obtenerResumenActual();
    }

    /**
     * Envía un paquete de sondeo TCP (o ICMP de respaldo) y retorna el RTT en
     * milisegundos, o -1.0 si ocurre timeout / pérdida de paquete.
     */
    private double enviarPaqueteSondeo(InetAddress direccion, int puertoDestino, int timeoutMs) {
        Socket socket = new Socket();
        socketActivoRef.set(socket);

        long t0 = System.nanoTime();
        try {
            socket.setReuseAddress(true);
            socket.connect(new InetSocketAddress(direccion, puertoDestino), timeoutMs);
            long t1 = System.nanoTime();
            double rttMs = (t1 - t0) / 1_000_000.0;
            return Math.max(0.10, Math.round(rttMs * 100.0) / 100.0);
        } catch (IOException exTcp) {
            // Si fue cancelado manualmente cerrando el socket, no contamos ni intentamos ICMP
            if (solicitudCancelacion.get() || isCancelled()) {
                return -1.0;
            }
            // Respaldo ICMP solo si el puerto TCP rechazó la conexión rápidamente
            long transcurridoMs = (System.nanoTime() - t0) / 1_000_000L;
            int timeoutRestante = (int) (timeoutMs - transcurridoMs);
            if (timeoutRestante > 100) {
                try {
                    long inicioIcmp = System.nanoTime();
                    if (direccion.isReachable(timeoutRestante)) {
                        double rttIcmp = (System.nanoTime() - inicioIcmp) / 1_000_000.0;
                        return Math.max(0.10, Math.round(rttIcmp * 100.0) / 100.0);
                    }
                } catch (IOException ignored) {
                }
            }
            return -1.0; // Paquete perdido (Timeout o inalcanzable)
        } finally {
            cerrarSocketSilenciosamente(socket);
            socketActivoRef.compareAndSet(socket, null);
        }
    }

    // =========================================================================
    // 2. RECEPCIÓN ASÍNCRONA EN EL EVENT DISPATCH THREAD (EDT - GUI SEGURA)
    // =========================================================================
    @Override
    protected void process(List<MuestraTelemetria> chunks) {
        if (escuchador == null || chunks == null || chunks.isEmpty()) {
            return;
        }
        ResumenQoS resumenParcial = medidorQoS.obtenerResumenActual();
        for (MuestraTelemetria muestra : chunks) {
            int porcentaje = (int) Math.min(100, Math.round((muestra.getNumeroSecuencia() * 100.0) / totalPaquetes));
            escuchador.alRecibirMuestra(muestra, resumenParcial, porcentaje);
        }
    }

    @Override
    protected void done() {
        if (escuchador == null) {
            return;
        }
        try {
            if (isCancelled() || solicitudCancelacion.get()) {
                escuchador.alFinalizarMuestreo(medidorQoS.obtenerResumenActual(), true);
            } else {
                ResumenQoS resultadoFinal = get();
                escuchador.alFinalizarMuestreo(resultadoFinal, false);
            }
        } catch (CancellationException ex) {
            escuchador.alFinalizarMuestreo(medidorQoS.obtenerResumenActual(), true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            escuchador.alFinalizarMuestreo(medidorQoS.obtenerResumenActual(), true);
        } catch (ExecutionException ex) {
            Throwable causa = (ex.getCause() != null) ? ex.getCause() : ex;
            escuchador.alOcurrirError(causa.getMessage());
        }
    }

    // =========================================================================
    // 3. API DE CONTROL Y CANCELACIÓN SEGURA DE SOCKETS (RF-ADQ-05)
    // =========================================================================
    /**
     * Solicita la detención inmediata del worker, cerrando de forma segura
     * cualquier socket bloqueado en espera de conexión e interrumpiendo el hilo
     * secundario.
     */
    public void solicitarCancelacionSegura() {
        solicitudCancelacion.set(true);
        Socket socketEnCurso = socketActivoRef.getAndSet(null);
        cerrarSocketSilenciosamente(socketEnCurso);
        cancel(true);
    }

    private void cerrarSocketSilenciosamente(Socket s) {
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    public MedidorQoS getMedidorQoS() {
        return medidorQoS;
    }

    public List<MuestraTelemetria> obtenerMuestrasCapturadas() {
        return medidorQoS.getHistorialMuestras();
    }
}
