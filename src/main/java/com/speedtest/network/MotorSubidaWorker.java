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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.SwingWorker;

/**
 * Tarea #18 (HU-04 / RF-ADQ-03, RF-ADQ-04, RF-ADQ-05): Motor concurrente de
 * transferencia de bloques en memoria (buffer dinámico) para la medición de la
 * tasa de subida (Upload) con muestreo cada Δt = 200 ms, aplicando:
 *
 * Velocidad (Mbps) = (Bytes Despachados * 8) / (Δt * 10^6)
 *
 * Se ejecuta en un hilo secundario (SwingWorker) garantizando que el Event
 * Dispatch Thread (EDT) de la interfaz gráfica se mantenga libre de bloqueos.
 */
public class MotorSubidaWorker extends SwingWorker<MotorSubidaWorker.ResumenSubida, MuestraTelemetria> {

    /**
     * Objeto inmutable de transferencia (DTO) con las estadísticas parciales o
     * finales de subida.
     */
    public static class ResumenSubida {

        private final int totalMuestras;
        private final long bytesTotalesEnviados;
        private final double duracionTotalSeg;
        private final double velocidadUltimaMbps;
        private final double velocidadPromedioMbps;
        private final double velocidadMinimaMbps;
        private final double velocidadMaximaMbps;
        private final String endpointUtilizado;

        public ResumenSubida(int totalMuestras, long bytesTotalesEnviados,
                double duracionTotalSeg, double velocidadUltimaMbps,
                double velocidadPromedioMbps, double velocidadMinimaMbps,
                double velocidadMaximaMbps, String endpointUtilizado) {
            this.totalMuestras = totalMuestras;
            this.bytesTotalesEnviados = bytesTotalesEnviados;
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

        public long getBytesTotalesEnviados() {
            return bytesTotalesEnviados;
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
                    "Subida [Muestras: %d | Enviado: %,d B (%.2f MB) | Duración: %.2f s | "
                    + "Promedio: %.2f Mbps | Pico: %.2f Mbps | Mín: %.2f Mbps | Endpoint: %s]",
                    totalMuestras,
                    bytesTotalesEnviados,
                    bytesTotalesEnviados / (1024.0 * 1024.0),
                    duracionTotalSeg,
                    velocidadPromedioMbps,
                    velocidadMaximaMbps,
                    velocidadMinimaMbps,
                    endpointUtilizado
            );
        }
    }

    // =========================================================================
    // CONSTANTES DE CALIBRACIÓN Y TELEMETRÍA (TAREA #18 - HU-04)
    // =========================================================================
    /**
     * Periodo de evaluación de volumen despachado: Δt = 200 ms (5 Hz).
     */
    public static final int INTERVALO_MUESTREO_MS = 200;

    /**
     * Duración estándar de la fase de subida: 6000 ms (30 muestras de 200 ms).
     */
    public static final int DURACION_DEFECTO_MS = 6000;

    /**
     * Timeout de apertura y escritura de socket en milisegundos.
     */
    public static final int TIMEOUT_CONEXION_MS = 4000;

    /**
     * Tamaño del bloque dinámico de bytes generado en memoria RAM (16 KB por
     * ráfaga).
     */
    public static final int TAMANO_BLOQUE_MEMORIA_BYTES = 16 * 1024;

    /**
     * Endpoint público de Cloudflare SpeedTest para recepción de flujos de
     * subida (POST).
     */
    public static final String URL_CLOUDFLARE_UP = "https://speed.cloudflare.com/__up";

    // =========================================================================
    // ATRIBUTOS DE CONFIGURACIÓN Y ESTADO DEL WORKER
    // =========================================================================
    private final String hostDestino;
    private final int puertoDestino;
    private final int duracionMaximaMs;
    private final int intervaloMuestreoMs;
    private final int tamanoBloqueBytes;
    private final EscuchadorProgresoSubida escuchador;

    /**
     * Buffer dinámico en memoria RAM con carga útil binaria para transmisión
     * sostenida.
     */
    private byte[] bufferMemoriaDinamico;

    private final List<MuestraTelemetria> historialMuestras;

    private long bytesTotalesEnviados;
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
    private final AtomicReference<OutputStream> streamSalidaActivoRef;
    private final AtomicReference<Socket> socketTcpActivoRef;

    /**
     * Constructor estándar (muestreo cada 200 ms durante 6 segundos con bloques
     * en memoria de 16 KB).
     */
    public MotorSubidaWorker(String hostDestino, int puertoDestino, EscuchadorProgresoSubida escuchador) {
        this(hostDestino, puertoDestino, DURACION_DEFECTO_MS, INTERVALO_MUESTREO_MS, TAMANO_BLOQUE_MEMORIA_BYTES, escuchador);
    }

    /**
     * Constructor completo parametrizable.
     */
    public MotorSubidaWorker(String hostDestino, int puertoDestino,
            int duracionMaximaMs, int intervaloMuestreoMs,
            int tamanoBloqueBytes, EscuchadorProgresoSubida escuchador) {
        this.hostDestino = (hostDestino != null) ? hostDestino.trim() : "";
        this.puertoDestino = puertoDestino;
        this.duracionMaximaMs = Math.max(1000, duracionMaximaMs);
        this.intervaloMuestreoMs = Math.max(50, intervaloMuestreoMs);
        this.tamanoBloqueBytes = Math.max(1024, tamanoBloqueBytes);
        this.escuchador = escuchador;
        this.historialMuestras = Collections.synchronizedList(new ArrayList<>());

        this.solicitudCancelacion = new AtomicBoolean(false);
        this.conexionHttpActivaRef = new AtomicReference<>(null);
        this.streamSalidaActivoRef = new AtomicReference<>(null);
        this.socketTcpActivoRef = new AtomicReference<>(null);

        reiniciarAcumuladores();
    }

    private synchronized void reiniciarAcumuladores() {
        this.historialMuestras.clear();
        this.bytesTotalesEnviados = 0L;
        this.duracionTotalSeg = 0.0;
        this.velocidadUltimaMbps = 0.0;
        this.velocidadMinimaMbps = Double.MAX_VALUE;
        this.velocidadMaximaMbps = 0.0;
        this.sumaVelocidadesMuestreadasMbps = 0.0;
        this.descripcionEndpointActivo = hostDestino + ":" + puertoDestino;
    }

    /**
     * Genera en memoria RAM un bloque de bytes pseudoaleatorios no comprimibles
     * (Buffer Dinámico) para alimentar la transmisión sostenida sin lecturas de
     * disco.
     *
     * @param tamanoBytes Cantidad de bytes del bloque en memoria.
     * @return Arreglo de bytes inicializado en RAM.
     */
    public static byte[] generarBloqueMemoriaDinamico(int tamanoBytes) {
        byte[] bloque = new byte[Math.max(1024, tamanoBytes)];
        Random generador = new Random(0x5350454544544553L); // Semilla determinista de alta entropía
        generador.nextBytes(bloque);
        return bloque;
    }

    /**
     * Ecuación fundamental de la Tarea #18 (RF-ADQ-03):
     *
     * Velocidad (Mbps) = (Bytes Despachados * 8) / (Δt * 10^6)
     *
     * @param bytesDespachados Cantidad de bytes transmitidos en la ventana Δt.
     * @param deltaTiempoSeg Duración exacta Δt de la ventana en segundos.
     * @return Throughput instantáneo de subida en Mbps redondeado a 2
     * decimales.
     */
    public static double calcularVelocidadMbps(long bytesDespachados, double deltaTiempoSeg) {
        if (bytesDespachados <= 0L || deltaTiempoSeg <= 0.0) {
            return 0.0;
        }
        double mbps = (bytesDespachados * 8.0) / (deltaTiempoSeg * 1_000_000.0);
        return redondearDosDecimales(mbps);
    }

    // =========================================================================
    // 1. EJECUCIÓN ASÍNCRONA EN HILO SECUNDARIO (FUERA DEL EDT)
    // =========================================================================
    @Override
    protected ResumenSubida doInBackground() throws Exception {
        reiniciarAcumuladores();

        if (hostDestino.isEmpty()) {
            throw new IllegalArgumentException("El host o servidor de destino para la subida no puede estar vacío.");
        }

        // 1. Generar el buffer dinámico en memoria RAM antes de abrir el canal de transmisión
        this.bufferMemoriaDinamico = generarBloqueMemoriaDinamico(this.tamanoBloqueBytes);

        // 2. Verificar alcanzabilidad inicial del nodo destino
        verificarAlcanzabilidadNodo(hostDestino, puertoDestino);

        // 3. Ejecutar la transmisión sostenida según el tipo de nodo (LAN Loopback vs WAN/Servidor)
        if (esNodoLocalhost(hostDestino)) {
            ejecutarSubidaBinariaSocketLocal();
        } else {
            try {
                ejecutarSubidaBinariaSocketWan(hostDestino, puertoDestino);
            } catch (IOException exSocket) {
                if (!isCancelled() && !solicitudCancelacion.get()) {
                    String urlSubida = resolverEndpointHttpSubida(hostDestino, puertoDestino);
                    ejecutarSubidaBinariaHttpChunked(urlSubida);
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

    private String resolverEndpointHttpSubida(String host, int puerto) {
        String h = host.toLowerCase();
        if (h.startsWith("http://") || h.startsWith("https://")) {
            this.descripcionEndpointActivo = host;
            return host;
        }
        if (h.contains("1.1.1.1") || h.contains("1.0.0.1") || h.contains("cloudflare")
                || h.contains("8.8.8.8") || h.contains("8.8.4.4") || h.contains("google")) {
            this.descripcionEndpointActivo = URL_CLOUDFLARE_UP + " (Nodo: " + host + ")";
            return URL_CLOUDFLARE_UP;
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
     * Transmite de forma sostenida el bloque de memoria hacia un endpoint WAN
     * mediante Socket TCP/TLS directo (ej. speed.cloudflare.com:443 o servidor
     * remoto), evaluando el volumen real de bytes despachados cada Δt = 200 ms.
     */
    private void ejecutarSubidaBinariaSocketWan(String hostOriginal, int puertoOriginal) throws Exception {
        String hostEfectivo = hostOriginal;
        int puertoEfectivo = puertoOriginal;
        boolean usarTls = (puertoOriginal == 443);

        String h = hostOriginal.toLowerCase();
        if (h.contains("1.1.1.1") || h.contains("1.0.0.1") || h.contains("cloudflare")
                || h.contains("8.8.8.8") || h.contains("8.8.4.4") || h.contains("google")) {
            hostEfectivo = "speed.cloudflare.com";
            puertoEfectivo = 443;
            usarTls = true;
        }

        this.descripcionEndpointActivo = (usarTls ? "TLS/HTTPS Upload (" : "TCP Upload (")
                + hostEfectivo + ":" + puertoEfectivo + ")";

        double intervaloObjetivoSeg = intervaloMuestreoMs / 1000.0;
        long tiempoInicioFaseNano = System.nanoTime();
        long tiempoInicioVentanaNano = tiempoInicioFaseNano;
        long bytesDespachadosEnVentana = 0L;
        int secuencia = 0;

        while (!isCancelled() && !solicitudCancelacion.get()) {
            if ((System.nanoTime() - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                break;
            }

            Socket socketBase = new Socket();
            socketTcpActivoRef.set(socketBase);
            socketBase.setSendBufferSize(64 * 1024);
            socketBase.setTcpNoDelay(true);
            socketBase.connect(new InetSocketAddress(hostEfectivo, puertoEfectivo), TIMEOUT_CONEXION_MS);
            socketBase.setSoTimeout(TIMEOUT_CONEXION_MS);

            Socket socketTransmision = socketBase;
            if (usarTls) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(socketBase, hostEfectivo, puertoEfectivo, true);
                sslSocket.startHandshake();
                socketTransmision = sslSocket;
                socketTcpActivoRef.set(sslSocket);
            }

            try (OutputStream out = socketTransmision.getOutputStream()) {
                streamSalidaActivoRef.set(out);

                // Cabecera HTTP POST con Transfer-Encoding: chunked o Content-Length amplio
                // para que el servidor remoto acepte el flujo binario entrante de forma continua
                String cabeceraPost = "POST /__up HTTP/1.1\r\n"
                        + "Host: " + hostEfectivo + "\r\n"
                        + "User-Agent: SpeedTestDesktop-Univalle-EIEE/1.0\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Length: 100000000\r\n"
                        + "Connection: keep-alive\r\n\r\n";
                byte[] bytesCabecera = cabeceraPost.getBytes(StandardCharsets.US_ASCII);
                out.write(bytesCabecera);
                out.flush();

                bytesDespachadosEnVentana += bytesCabecera.length;
                bytesTotalesEnviados += bytesCabecera.length;

                while (!isCancelled() && !solicitudCancelacion.get()) {
                    // Transmisión del bloque de bytes desde el buffer dinámico en memoria
                    out.write(bufferMemoriaDinamico, 0, bufferMemoriaDinamico.length);
                    out.flush();

                    bytesDespachadosEnVentana += bufferMemoriaDinamico.length;
                    bytesTotalesEnviados += bufferMemoriaDinamico.length;

                    long ahoraNano = System.nanoTime();
                    double deltaVentanaSeg = (ahoraNano - tiempoInicioVentanaNano) / 1_000_000_000.0;
                    double tiempoTranscurridoSeg = (ahoraNano - tiempoInicioFaseNano) / 1_000_000_000.0;

                    // Evaluación del volumen despachado cada Δt = 200 ms
                    if (deltaVentanaSeg >= intervaloObjetivoSeg) {
                        secuencia++;
                        procesarYPublicarVentanaSubida(
                                secuencia,
                                tiempoTranscurridoSeg,
                                bytesDespachadosEnVentana,
                                deltaVentanaSeg
                        );

                        bytesDespachadosEnVentana = 0L;
                        tiempoInicioVentanaNano = ahoraNano;
                    }

                    if ((ahoraNano - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                        break;
                    }
                }
            } catch (IOException exEscritura) {
                // Si el servidor cierra el POST actual tras recibir un bloque grande y aún
                // queda tiempo de prueba, el bucle externo abre el siguiente flujo limpiamente
                if (secuencia == 0 && !isCancelled() && !solicitudCancelacion.get()) {
                    throw exEscritura;
                }
            } finally {
                liberarDescriptoresRed();
            }
        }

        long finNano = System.nanoTime();
        this.duracionTotalSeg = redondearDosDecimales((finNano - tiempoInicioFaseNano) / 1_000_000_000.0);
        double deltaRemanenteSeg = (finNano - tiempoInicioVentanaNano) / 1_000_000_000.0;

        if (!isCancelled() && !solicitudCancelacion.get()
                && bytesDespachadosEnVentana > 0L && deltaRemanenteSeg >= 0.05) {
            secuencia++;
            procesarYPublicarVentanaSubida(
                    secuencia,
                    this.duracionTotalSeg,
                    bytesDespachadosEnVentana,
                    deltaRemanenteSeg
            );
        }
    }

    /**
     * Respaldo de subida mediante HttpURLConnection en modo de streaming por
     * fragmentos (setChunkedStreamingMode) para transmitir directamente desde
     * memoria sin acumular en heap.
     */
    private void ejecutarSubidaBinariaHttpChunked(String urlEndpoint) throws Exception {
        double intervaloObjetivoSeg = intervaloMuestreoMs / 1000.0;
        long tiempoInicioFaseNano = System.nanoTime();
        long tiempoInicioVentanaNano = tiempoInicioFaseNano;
        long bytesDespachadosEnVentana = 0L;
        int secuencia = 0;

        while (!isCancelled() && !solicitudCancelacion.get()) {
            if ((System.nanoTime() - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                break;
            }

            URL url = URI.create(urlEndpoint).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conexionHttpActivaRef.set(conn);

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_CONEXION_MS);
            conn.setReadTimeout(TIMEOUT_CONEXION_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "SpeedTestDesktop-Univalle-EIEE/1.0");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setChunkedStreamingMode(bufferMemoriaDinamico.length);

            try (OutputStream out = conn.getOutputStream()) {
                streamSalidaActivoRef.set(out);

                while (!isCancelled() && !solicitudCancelacion.get()) {
                    out.write(bufferMemoriaDinamico, 0, bufferMemoriaDinamico.length);
                    out.flush();

                    bytesDespachadosEnVentana += bufferMemoriaDinamico.length;
                    bytesTotalesEnviados += bufferMemoriaDinamico.length;

                    long ahoraNano = System.nanoTime();
                    double deltaVentanaSeg = (ahoraNano - tiempoInicioVentanaNano) / 1_000_000_000.0;
                    double tiempoTranscurridoSeg = (ahoraNano - tiempoInicioFaseNano) / 1_000_000_000.0;

                    if (deltaVentanaSeg >= intervaloObjetivoSeg) {
                        secuencia++;
                        procesarYPublicarVentanaSubida(
                                secuencia,
                                tiempoTranscurridoSeg,
                                bytesDespachadosEnVentana,
                                deltaVentanaSeg
                        );

                        bytesDespachadosEnVentana = 0L;
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
     * Ejecuta la transmisión sostenida de bloques en memoria hacia un socket
     * sumidero local (LAN / Loopback) con control de cadencia para pruebas de
     * laboratorio en Localhost XAMPP.
     */
    private void ejecutarSubidaBinariaSocketLocal() throws Exception {
        this.descripcionEndpointActivo = "Socket TCP LAN Loopback (" + hostDestino + ":" + puertoDestino + ")";

        try (ServerSocket servidorSumidero = new ServerSocket(0)) {
            int puertoSumidero = servidorSumidero.getLocalPort();

            // Hilo receptor (Sink) que drena continuamente los bloques enviados por el cliente
            Thread hiloReceptorSumidero = new Thread(() -> {
                byte[] bufferDrenaje = new byte[tamanoBloqueBytes];
                try (Socket socketAceptado = servidorSumidero.accept(); InputStream in = socketAceptado.getInputStream()) {
                    while (!solicitudCancelacion.get() && !Thread.currentThread().isInterrupted()) {
                        if (in.read(bufferDrenaje) == -1) {
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }, "LAN-Upload-Sink");
            hiloReceptorSumidero.setDaemon(true);
            hiloReceptorSumidero.start();

            double intervaloObjetivoSeg = intervaloMuestreoMs / 1000.0;
            long tiempoInicioFaseNano = System.nanoTime();
            long tiempoInicioVentanaNano = tiempoInicioFaseNano;
            long bytesDespachadosEnVentana = 0L;
            int secuencia = 0;

            try (Socket socketEmisor = new Socket()) {
                socketTcpActivoRef.set(socketEmisor);
                socketEmisor.connect(new InetSocketAddress("127.0.0.1", puertoSumidero), TIMEOUT_CONEXION_MS);
                socketEmisor.setSoTimeout(TIMEOUT_CONEXION_MS);

                try (OutputStream out = socketEmisor.getOutputStream()) {
                    streamSalidaActivoRef.set(out);

                    while (!isCancelled() && !solicitudCancelacion.get()) {
                        out.write(bufferMemoriaDinamico, 0, bufferMemoriaDinamico.length);
                        out.flush();

                        bytesDespachadosEnVentana += bufferMemoriaDinamico.length;
                        bytesTotalesEnviados += bufferMemoriaDinamico.length;

                        // Pausa de 2 ms para emular un canal de subida LAN estable (~65-100 Mbps)
                        Thread.sleep(2);

                        long ahoraNano = System.nanoTime();
                        double deltaVentanaSeg = (ahoraNano - tiempoInicioVentanaNano) / 1_000_000_000.0;
                        double tiempoTranscurridoSeg = (ahoraNano - tiempoInicioFaseNano) / 1_000_000_000.0;

                        if (deltaVentanaSeg >= intervaloObjetivoSeg) {
                            secuencia++;
                            procesarYPublicarVentanaSubida(
                                    secuencia,
                                    tiempoTranscurridoSeg,
                                    bytesDespachadosEnVentana,
                                    deltaVentanaSeg
                            );

                            bytesDespachadosEnVentana = 0L;
                            tiempoInicioVentanaNano = ahoraNano;
                        }

                        if ((ahoraNano - tiempoInicioFaseNano) / 1_000_000L >= duracionMaximaMs) {
                            break;
                        }
                    }
                }
            } finally {
                hiloReceptorSumidero.interrupt();
                liberarDescriptoresRed();
            }

            long finNano = System.nanoTime();
            this.duracionTotalSeg = redondearDosDecimales((finNano - tiempoInicioFaseNano) / 1_000_000_000.0);
        }
    }

    /**
     * Calcula la velocidad instantánea de subida en la ventana de 200 ms,
     * instancia MuestraTelemetria con etapa "UP" y la publica hacia el EDT.
     */
    private synchronized void procesarYPublicarVentanaSubida(int secuencia, double tiempoTranscurridoSeg,
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

        MuestraTelemetria muestra = MuestraTelemetria.crearMuestraSubida(
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
        ResumenSubida resumenParcial = obtenerResumenActual();
        for (MuestraTelemetria muestra : chunks) {
            int porcentaje = (int) Math.min(100,
                    Math.round((muestra.getTiempoTranscurridoSeg() * 1000.0 / duracionMaximaMs) * 100.0));
            escuchador.alRecibirMuestraSubida(muestra, resumenParcial, porcentaje);
        }
    }

    @Override
    protected void done() {
        if (escuchador == null) {
            return;
        }
        try {
            if (isCancelled() || solicitudCancelacion.get()) {
                escuchador.alFinalizarSubida(obtenerResumenActual(), true);
            } else {
                ResumenSubida resultado = get();
                escuchador.alFinalizarSubida(resultado, false);
            }
        } catch (CancellationException ex) {
            escuchador.alFinalizarSubida(obtenerResumenActual(), true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            escuchador.alFinalizarSubida(obtenerResumenActual(), true);
        } catch (ExecutionException ex) {
            Throwable causa = (ex.getCause() != null) ? ex.getCause() : ex;
            escuchador.alOcurrirErrorSubida(causa.getMessage());
        }
    }

    // =========================================================================
    // 3. CONSULTA DE RESUMEN Y CANCELACIÓN SEGURA DE RECURSOS (RF-ADQ-05)
    // =========================================================================
    public synchronized ResumenSubida obtenerResumenActual() {
        int n = historialMuestras.size();
        double promedioMbps = 0.0;

        if (duracionTotalSeg > 0.0 && bytesTotalesEnviados > 0L) {
            promedioMbps = calcularVelocidadMbps(bytesTotalesEnviados, duracionTotalSeg);
        } else if (n > 0) {
            promedioMbps = redondearDosDecimales(sumaVelocidadesMuestreadasMbps / n);
        }

        double minVal = (n > 0 && velocidadMinimaMbps != Double.MAX_VALUE)
                ? redondearDosDecimales(velocidadMinimaMbps) : 0.0;
        double maxVal = (n > 0) ? redondearDosDecimales(velocidadMaximaMbps) : 0.0;

        return new ResumenSubida(
                n,
                bytesTotalesEnviados,
                duracionTotalSeg,
                redondearDosDecimales(velocidadUltimaMbps),
                promedioMbps,
                minVal,
                maxVal,
                descripcionEndpointActivo
        );
    }

    /**
     * Detiene de inmediato la transmisión de subida cerrando el OutputStream,
     * el Socket TCP/TLS y la conexión HTTP activa sin congelar el EDT
     * (RF-ADQ-05).
     */
    public void solicitarCancelacionSegura() {
        solicitudCancelacion.set(true);
        liberarDescriptoresRed();
        cancel(true);
    }

    private void liberarDescriptoresRed() {
        OutputStream out = streamSalidaActivoRef.getAndSet(null);
        if (out != null) {
            try {
                out.close();
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
