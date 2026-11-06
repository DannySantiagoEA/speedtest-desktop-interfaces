package com.speedtest.network;

import com.speedtest.model.MuestraTelemetria;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingWorker;

/**
 * Tarea #17 (HU-04 / RF-ADQ-02, RF-ADQ-04, RF-ADQ-05): Motor concurrente para
 * la recepción asíncrona de flujos binarios mediante peticiones HTTP/TCP, con
 * muestreador dinámico cada Δt = 200 ms que aplica la ecuación:
 *
 * Velocidad (Mbps) = (Bytes * 8) / (Δt * 10^6)
 *
 * Se ejecuta en un hilo secundario (SwingWorker) garantizando que el Event
 * Dispatch Thread (EDT) de la interfaz gráfica nunca se bloquee.
 */
public class MotorDescargaWorker extends SwingWorker<MotorDescargaWorker.ResumenDescarga, MuestraTelemetria> {

    /**
     * Objeto inmutable de transferencia (DTO) con el resumen parcial o
     * consolidado de descarga.
     */
    public static class ResumenDescarga {

        private final int totalMuestras;
        private final long bytesTotalesRecibidos;
        private final double duracionTotalSeg;
        private final double velocidadUltimaMbps;
        private final double velocidadPromedioMbps;
        private final double velocidadMinimaMbps;
        private final double velocidadMaximaMbps;
        private final String endpointUtilizado;

        public ResumenDescarga(int totalMuestras, long bytesTotalesRecibidos,
                double duracionTotalSeg, double velocidadUltimaMbps,
                double velocidadPromedioMbps, double velocidadMinimaMbps,
                double velocidadMaximaMbps, String endpointUtilizado) {
            this.totalMuestras = totalMuestras;
            this.bytesTotalesRecibidos = bytesTotalesRecibidos;
            this.duracionTotalSeg = duracionTotalSeg;
            this.velocidadUltimaMbps = velocidadUltimaMbps;
            this.velocidadPromedioMbps = velocidadPromedioMbps;
            this.velocidadMinimaMbps = velocidadMinimaMbps;
            this.velocidadMaximaMbps = velocidadMaximaMbps;
            this.endpointUtilizado = endpointUtilizado;
        }

        public int getTotalMuestras() {
            return totalMuestras;
        }

        public long getBytesTotalesRecibidos() {
            return bytesTotalesRecibidos;
        }

        public double getDuracionTotalSeg() {
            return duracionTotalSeg;
        }

        public double getVelocidadUltimaMbps() {
            return velocidadUltimaMbps;
        }

        public double getVelocidadPromedioMbps() {
            return velocidadPromedioMbps;
        }

        public double getVelocidadMinimaMbps() {
            return velocidadMinimaMbps;
        }

        public double getVelocidadMaximaMbps() {
            return velocidadMaximaMbps;
        }

        public String getEndpointUtilizado() {
            return endpointUtilizado;
        }

        @Override
        public String toString() {
            return String.format(
                    "Descarga [Muestras: %d | Recibido: %,d B (%.2f MB) | Duración: %.2f s | "
                    + "Promedio: %.2f Mbps | Pico: %.2f Mbps | Mín: %.2f Mbps | Endpoint: %s]",
                    totalMuestras,
                    bytesTotalesRecibidos,
                    bytesTotalesRecibidos / (1024.0 * 1024.0),
                    duracionTotalSeg,
                    velocidadPromedioMbps,
                    velocidadMaximaMbps,
                    velocidadMinimaMbps,
                    endpointUtilizado
            );
        }
    }

    // =========================================================================
    // CONSTANTES DE MUESTREO Y RED (TAREA #17 - HU-04)
    // =========================================================================
    /**
     * Ventana de muestreo dinámico exigida por la arquitectura: Δt = 200 ms (5
     * Hz).
     */
    public static final int INTERVALO_MUESTREO_MS = 200;

    /**
     * Duración por defecto de la fase de descarga: 6000 ms (30 muestras de 200
     * ms).
     */
    public static final int DURACION_DEFECTO_MS = 6000;

    /**
     * Timeout máximo de conexión y lectura en socket (4000 ms).
     */
    public static final int TIMEOUT_CONEXION_MS = 4000;

    /**
     * Tamaño del búfer de lectura de bloques binarios (32 KB).
     */
    private static final int TAMANO_BUFFER_BYTES = 32 * 1024;

    /**
     * Endpoint público de Cloudflare SpeedTest para descarga de payload binario
     * continuo (50 MB).
     */
    public static final String URL_CLOUDFLARE_DOWN = "https://speed.cloudflare.com/__down?bytes=50000000";

    // =========================================================================
    // ATRIBUTOS DE CONFIGURACIÓN Y CONTROL CONCURRENTE
    // =========================================================================
    private final String hostDestino;
    private final int puertoDestino;
    private final int duracionMaximaMs;
    private final int intervaloMuestreoMs;
    private final EscuchadorProgresoDescarga escuchador;

    private final List<MuestraTelemetria> historialMuestras;

    private long bytesTotalesAcumulados;
    private double duracionTotalSeg;
    private double velocidadUltimaMbps;
    private double velocidadMinimaMbps;
    private double velocidadMaximaMbps;
    private double sumaVelocidadesMuestreadasMbps;
    private String descripcionEndpointActivo;

    /**
     * Primitivas atómicas para cancelación inmediata y cierre seguro de
     * descriptores (RF-ADQ-05).
     */
    private final AtomicBoolean solicitudCancelacion;
    private final AtomicReference<HttpURLConnection> conexionHttpActivaRef;
    private final AtomicReference<InputStream> streamActivoRef;
    private final AtomicReference<Socket> socketTcpActivoRef;

    /**
     * Constructor estándar (muestreo cada 200 ms durante 6 segundos).
     */
    public MotorDescargaWorker(String hostDestino, int puertoDestino, EscuchadorProgresoDescarga escuchador) {
        this(hostDestino, puertoDestino, DURACION_DEFECTO_MS, INTERVALO_MUESTREO_MS, escuchador);
    }

    /**
     * Constructor completo parametrizable.
     */
    public MotorDescargaWorker(String hostDestino, int puertoDestino,
            int duracionMaximaMs, int intervaloMuestreoMs,
            EscuchadorProgresoDescarga escuchador) {
        this.hostDestino = (hostDestino != null) ? hostDestino.trim() : "";
        this.puertoDestino = puertoDestino;
        this.duracionMaximaMs = Math.max(1000, duracionMaximaMs);
        this.intervaloMuestreoMs = Math.max(50, intervaloMuestreoMs);
        this.escuchador = escuchador;
        this.historialMuestras = Collections.synchronizedList(new ArrayList<>());

        this.solicitudCancelacion = new AtomicBoolean(false);
        this.conexionHttpActivaRef = new AtomicReference<>(null);
        this.streamActivoRef = new AtomicReference<>(null);
        this.socketTcpActivoRef = new AtomicReference<>(null);

        reiniciarAcumuladores();
    }

    private synchronized void reiniciarAcumuladores() {
        this.historialMuestras.clear();
        this.bytesTotalesAcumulados = 0L;
        this.duracionTotalSeg = 0.0;
        this.velocidadUltimaMbps = 0.0;
        this.velocidadMinimaMbps = Double.MAX_VALUE;
        this.velocidadMaximaMbps = 0.0;
        this.sumaVelocidadesMuestreadasMbps = 0.0;
        this.descripcionEndpointActivo = hostDestino + ":" + puertoDestino;
    }

    /**
     * Ecuación matemática oficial de la Tarea #17 (RF-ADQ-02):
     *
     * Velocidad (Mbps) = (Bytes * 8) / (Δt * 10^6)
     *
     * @param bytesEnVentana Cantidad de bytes transferidos dentro del intervalo
     * Δt.
     * @param deltaTiempoSeg Duración exacta Δt de la ventana en segundos.
     * @return Velocidad instantánea en Mbps redondeada a 2 decimales.
     */
    public static double calcularVelocidadMbps(long bytesEnVentana, double deltaTiempoSeg) {
        if (bytesEnVentana <= 0L || deltaTiempoSeg <= 0.0) {
            return 0.0;
        }
        double mbps = (bytesEnVentana * 8.0) / (deltaTiempoSeg * 1_000_000.0);
        return redondearDosDecimales(mbps);
    }

    // =========================================================================
    // 1. EJECUCIÓN ASÍNCRONA EN HILO SECUNDARIO (FUERA DEL EDT)
    // =========================================================================
    @Override
    protected ResumenDescarga doInBackground() throws Exception {
        reiniciarAcumuladores();

        if (hostDestino.isEmpty()) {
            throw new IllegalArgumentException("El host o servidor de destino para la descarga no puede estar vacío.");
        }

        // Validación previa de alcanzabilidad TCP hacia el nodo seleccionado
        verificarAlcanzabilidadNodo(hostDestino, puertoDestino);

        if (esNodoLocalhost(hostDestino)) {
            // Modo LAN / Loopback (ej. Localhost XAMPP 127.0.0.1:80):
            // Levanta un flujo continuo de chunks binarios por Socket TCP local
            ejecutarDescargaBinariaSocketLocal();
        } else {
            // Modo WAN / Servidor externo:
            // Descarga flujo binario mediante petición HTTP/HTTPS asíncrona con respaldo TCP
            String urlDescarga = resolverEndpointHttp(hostDestino, puertoDestino);
            try {
                ejecutarDescargaBinariaHttp(urlDescarga);
            } catch (IOException exHttp) {
                if (!isCancelled() && !solicitudCancelacion.get()) {
                    ejecutarDescargaTcpDirecta(hostDestino, puertoDestino);
                }
            }
        }

        return obtenerResumenActual();
    }

    private void verificarAlcanzabilidadNodo(String host, int puerto) throws IOException {
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return;
        }
        try (Socket socketPrueba = new Socket()) {
            socketTcpActivoRef.set(socketPrueba);
            socketPrueba.connect(new InetSocketAddress(host, puerto), TIMEOUT_CONEXION_MS);
        } finally {
            socketTcpActivoRef.set(null);
        }
    }

    private String resolverEndpointHttp(String host, int puerto) {
        String h = host.toLowerCase();
        if (h.startsWith("http://") || h.startsWith("https://")) {
            this.descripcionEndpointActivo = host;
            return host;
        }
        if (h.contains("1.1.1.1") || h.contains("1.0.0.1") || h.contains("cloudflare")
                || h.contains("8.8.8.8") || h.contains("8.8.4.4") || h.contains("google")) {
            this.descripcionEndpointActivo = URL_CLOUDFLARE_DOWN + " (Nodo: " + host + ")";
            return URL_CLOUDFLARE_DOWN;
        }
        String esquema = (puerto == 443) ? "https://" : "http://";
        String url = esquema + host + ":" + puerto + "/";
        this.descripcionEndpointActivo = url;
        return url;
    }

    private boolean esNodoLocalhost(String host) {
        String h = host.toLowerCase();
        return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1");
    }

    /**
     * Recepción asíncrona de flujo binario vía HTTP/HTTPS con muestreo cada Δt
     * = 200 ms.
     */
    private void ejecutarDescargaBinariaHttp(String urlEndpoint) throws Exception {
        byte[] buffer = new byte[TAMANO_BUFFER_BYTES];
        double intervaloObjetivoSeg = intervaloMuestreoMs / 1000.0;

        long tiempoInicioFaseNano = System.nanoTime();
        long tiempoInicioVentanaNano = tiempoInicioFaseNano;
        long bytesEnVentana = 0L;
        int secuencia = 0;

        while (!isCancelled() && !solicitudCancelacion.get()) {
            if ((System.nanoTime() - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                break;
            }

            URL url = URI.create(urlEndpoint).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conexionHttpActivaRef.set(conn);

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_CONEXION_MS);
            conn.setReadTimeout(TIMEOUT_CONEXION_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "SpeedTestDesktop-Univalle-EIEE/1.0");
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            conn.setRequestProperty("Accept-Encoding", "identity");

            try (InputStream in = conn.getInputStream()) {
                streamActivoRef.set(in);
                int bytesLeidos;

                while (!isCancelled() && !solicitudCancelacion.get()
                        && (bytesLeidos = in.read(buffer)) != -1) {

                    bytesEnVentana += bytesLeidos;
                    bytesTotalesAcumulados += bytesLeidos;

                    long ahoraNano = System.nanoTime();
                    double deltaVentanaSeg = (ahoraNano - tiempoInicioVentanaNano) / 1_000_000_000.0;
                    double tiempoTranscurridoSeg = (ahoraNano - tiempoInicioFaseNano) / 1_000_000_000.0;

                    // Muestreador dinámico cada Δt = 200 ms (0.200 s)
                    if (deltaVentanaSeg >= intervaloObjetivoSeg) {
                        secuencia++;
                        procesarYPublicarVentana(secuencia, tiempoTranscurridoSeg, bytesEnVentana, deltaVentanaSeg);

                        bytesEnVentana = 0L;
                        tiempoInicioVentanaNano = ahoraNano;
                    }

                    if ((ahoraNano - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                        break;
                    }
                }
            } finally {
                liberarDescriptoresRed();
            }
        }

        long finNano = System.nanoTime();
        this.duracionTotalSeg = redondearDosDecimales((finNano - tiempoInicioFaseNano) / 1_000_000_000.0);
        double deltaRemanenteSeg = (finNano - tiempoInicioVentanaNano) / 1_000_000_000.0;

        if (!isCancelled() && !solicitudCancelacion.get()
                && bytesEnVentana > 0L && deltaRemanenteSeg >= 0.05) {
            secuencia++;
            procesarYPublicarVentana(secuencia, this.duracionTotalSeg, bytesEnVentana, deltaRemanenteSeg);
        }
    }

    /**
     * Recepción asíncrona de flujo binario sobre Socket TCP local (LAN /
     * Loopback) con muestreo cada Δt = 200 ms.
     */
    private void ejecutarDescargaBinariaSocketLocal() throws Exception {
        this.descripcionEndpointActivo = "Socket TCP LAN Loopback (" + hostDestino + ":" + puertoDestino + ")";

        try (ServerSocket servidorLocal = new ServerSocket(0)) {
            int puertoLocal = servidorLocal.getLocalPort();

            Thread hiloGeneradorFlujo = new Thread(() -> {
                byte[] bloqueBinario = new byte[TAMANO_BUFFER_BYTES];
                Arrays.fill(bloqueBinario, (byte) 0x5A);
                try (Socket cliente = servidorLocal.accept(); OutputStream out = cliente.getOutputStream()) {
                    while (!solicitudCancelacion.get() && !Thread.currentThread().isInterrupted()) {
                        out.write(bloqueBinario);
                        out.flush();
                        Thread.sleep(2);
                    }
                } catch (Exception ignored) {
                }
            }, "LAN-Download-BinaryStream");
            hiloGeneradorFlujo.setDaemon(true);
            hiloGeneradorFlujo.start();

            byte[] buffer = new byte[TAMANO_BUFFER_BYTES];
            double intervaloObjetivoSeg = intervaloMuestreoMs / 1000.0;

            long tiempoInicioFaseNano = System.nanoTime();
            long tiempoInicioVentanaNano = tiempoInicioFaseNano;
            long bytesEnVentana = 0L;
            int secuencia = 0;

            try (Socket socketCliente = new Socket()) {
                socketTcpActivoRef.set(socketCliente);
                socketCliente.connect(new InetSocketAddress("127.0.0.1", puertoLocal), TIMEOUT_CONEXION_MS);
                socketCliente.setSoTimeout(TIMEOUT_CONEXION_MS);

                try (InputStream in = socketCliente.getInputStream()) {
                    streamActivoRef.set(in);
                    int bytesLeidos;

                    while (!isCancelled() && !solicitudCancelacion.get()
                            && (bytesLeidos = in.read(buffer)) != -1) {

                        bytesEnVentana += bytesLeidos;
                        bytesTotalesAcumulados += bytesLeidos;

                        long ahoraNano = System.nanoTime();
                        double deltaVentanaSeg = (ahoraNano - tiempoInicioVentanaNano) / 1_000_000_000.0;
                        double tiempoTranscurridoSeg = (ahoraNano - tiempoInicioFaseNano) / 1_000_000_000.0;

                        if (deltaVentanaSeg >= intervaloObjetivoSeg) {
                            secuencia++;
                            procesarYPublicarVentana(secuencia, tiempoTranscurridoSeg, bytesEnVentana, deltaVentanaSeg);

                            bytesEnVentana = 0L;
                            tiempoInicioVentanaNano = ahoraNano;
                        }

                        if ((ahoraNano - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                            break;
                        }
                    }
                }
            } finally {
                hiloGeneradorFlujo.interrupt();
                liberarDescriptoresRed();
            }

            long finNano = System.nanoTime();
            this.duracionTotalSeg = redondearDosDecimales((finNano - tiempoInicioFaseNano) / 1_000_000_000.0);
        }
    }

    /**
     * Respaldo de recepción de flujos por Socket TCP directo hacia el host y
     * puerto configurados.
     */
    private void ejecutarDescargaTcpDirecta(String host, int puerto) throws Exception {
        this.descripcionEndpointActivo = "Socket TCP Directo (" + host + ":" + puerto + ")";
        byte[] buffer = new byte[TAMANO_BUFFER_BYTES];
        byte[] solicitudHttp = ("GET / HTTP/1.1\r\nHost: " + host + "\r\nConnection: keep-alive\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);

        double intervaloObjetivoSeg = intervaloMuestreoMs / 1000.0;
        long tiempoInicioFaseNano = System.nanoTime();
        long tiempoInicioVentanaNano = tiempoInicioFaseNano;
        long bytesEnVentana = 0L;
        int secuencia = 0;

        while (!isCancelled() && !solicitudCancelacion.get()) {
            if ((System.nanoTime() - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                break;
            }

            try (Socket socket = new Socket()) {
                socketTcpActivoRef.set(socket);
                socket.connect(new InetSocketAddress(host, puerto), TIMEOUT_CONEXION_MS);
                socket.setSoTimeout(TIMEOUT_CONEXION_MS);

                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                streamActivoRef.set(in);

                out.write(solicitudHttp);
                out.flush();

                int bytesLeidos;
                while (!isCancelled() && !solicitudCancelacion.get()
                        && (bytesLeidos = in.read(buffer)) != -1) {

                    bytesEnVentana += bytesLeidos;
                    bytesTotalesAcumulados += bytesLeidos;

                    long ahoraNano = System.nanoTime();
                    double deltaVentanaSeg = (ahoraNano - tiempoInicioVentanaNano) / 1_000_000_000.0;
                    double tiempoTranscurridoSeg = (ahoraNano - tiempoInicioFaseNano) / 1_000_000_000.0;

                    if (deltaVentanaSeg >= intervaloObjetivoSeg) {
                        secuencia++;
                        procesarYPublicarVentana(secuencia, tiempoTranscurridoSeg, bytesEnVentana, deltaVentanaSeg);

                        bytesEnVentana = 0L;
                        tiempoInicioVentanaNano = ahoraNano;
                    }

                    if ((ahoraNano - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                        break;
                    }
                }
            } finally {
                liberarDescriptoresRed();
            }
        }

        this.duracionTotalSeg = redondearDosDecimales((System.nanoTime() - tiempoInicioFaseNano) / 1_000_000_000.0);
    }

    /**
     * Calcula la velocidad instantánea de la ventana de 200 ms, crea la entidad
     * MuestraTelemetria con etapa "DOWN" y la publica hacia el EDT.
     */
    private synchronized void procesarYPublicarVentana(int secuencia, double tiempoTranscurridoSeg,
            long bytesVentana, double deltaTiempoSeg) {
        double velocidadInstMbps = calcularVelocidadMbps(bytesVentana, deltaTiempoSeg);

        this.velocidadUltimaMbps = velocidadInstMbps;
        this.sumaVelocidadesMuestreadasMbps += velocidadInstMbps;
        this.duracionTotalSeg = redondearDosDecimales(tiempoTranscurridoSeg);

        if (velocidadInstMbps < this.velocidadMinimaMbps) {
            this.velocidadMinimaMbps = velocidadInstMbps;
        }
        if (velocidadInstMbps > this.velocidadMaximaMbps) {
            this.velocidadMaximaMbps = velocidadInstMbps;
        }

        MuestraTelemetria muestra = MuestraTelemetria.crearMuestraDescarga(
                secuencia,
                tiempoTranscurridoSeg,
                bytesVentana,
                velocidadInstMbps
        );

        this.historialMuestras.add(muestra);
        publish(muestra);
    }

    // =========================================================================
    // 2. RECEPCIÓN ASÍNCRONA EN EL EVENT DISPATCH THREAD (EDT - GUI SEGURA)
    // =========================================================================
    @Override
    protected void process(List<MuestraTelemetria> chunks) {
        if (escuchador == null || chunks == null || chunks.isEmpty()) {
            return;
        }
        ResumenDescarga resumenParcial = obtenerResumenActual();
        for (MuestraTelemetria muestra : chunks) {
            int porcentaje = (int) Math.min(100,
                    Math.round((muestra.getTiempoTranscurridoSeg() * 1000.0 / duracionMaximaMs) * 100.0));
            escuchador.alRecibirMuestraDescarga(muestra, resumenParcial, porcentaje);
        }
    }

    @Override
    protected void done() {
        if (escuchador == null) {
            return;
        }
        try {
            if (isCancelled() || solicitudCancelacion.get()) {
                escuchador.alFinalizarDescarga(obtenerResumenActual(), true);
            } else {
                ResumenDescarga resultado = get();
                escuchador.alFinalizarDescarga(resultado, false);
            }
        } catch (CancellationException ex) {
            escuchador.alFinalizarDescarga(obtenerResumenActual(), true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            escuchador.alFinalizarDescarga(obtenerResumenActual(), true);
        } catch (ExecutionException ex) {
            Throwable causa = (ex.getCause() != null) ? ex.getCause() : ex;
            escuchador.alOcurrirErrorDescarga(causa.getMessage());
        }
    }

    // =========================================================================
    // 3. CONSULTA DE RESUMEN Y CANCELACIÓN SEGURA DE RECURSOS (RF-ADQ-05)
    // =========================================================================
    public synchronized ResumenDescarga obtenerResumenActual() {
        int n = historialMuestras.size();
        double promedioMbps = 0.0;

        if (duracionTotalSeg > 0.0 && bytesTotalesAcumulados > 0L) {
            promedioMbps = calcularVelocidadMbps(bytesTotalesAcumulados, duracionTotalSeg);
        } else if (n > 0) {
            promedioMbps = redondearDosDecimales(sumaVelocidadesMuestreadasMbps / n);
        }

        double minVal = (n > 0 && velocidadMinimaMbps != Double.MAX_VALUE)
                ? redondearDosDecimales(velocidadMinimaMbps) : 0.0;
        double maxVal = (n > 0) ? redondearDosDecimales(velocidadMaximaMbps) : 0.0;

        return new ResumenDescarga(
                n,
                bytesTotalesAcumulados,
                duracionTotalSeg,
                redondearDosDecimales(velocidadUltimaMbps),
                promedioMbps,
                minVal,
                maxVal,
                descripcionEndpointActivo
        );
    }

    /**
     * Cancela de inmediato la transferencia de descarga cerrando el stream de
     * lectura, el socket TCP y la conexión HTTP activa sin congelar el EDT
     * (RF-ADQ-05).
     */
    public void solicitarCancelacionSegura() {
        solicitudCancelacion.set(true);
        liberarDescriptoresRed();
        cancel(true);
    }

    private void liberarDescriptoresRed() {
        InputStream in = streamActivoRef.getAndSet(null);
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }

        Socket sock = socketTcpActivoRef.getAndSet(null);
        if (sock != null && !sock.isClosed()) {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }

        HttpURLConnection conn = conexionHttpActivaRef.getAndSet(null);
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized List<MuestraTelemetria> obtenerMuestrasCapturadas() {
        return Collections.unmodifiableList(new ArrayList<>(historialMuestras));
    }

    private static double redondearDosDecimales(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
