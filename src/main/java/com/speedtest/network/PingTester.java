package com.speedtest.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Tarea #8 (HU-02 / RF-ADQ-01): Servicio de sondeo exploratorio de conectividad
 * (Ping / RTT). Resuelve la dirección IP del servidor destino y mide el tiempo
 * de ida y vuelta (RTT en ms) mediante handshake de sockets TCP (con respaldo
 * ICMP) y control estricto de timeout.
 */
public class PingTester {

    /**
     * Tiempo máximo de espera por defecto para el sondeo exploratorio (2500
     * ms).
     */
    public static final int TIMEOUT_DEFECTO_MS = 2500;

    /**
     * Estructura inmutable que encapsula el diagnóstico del sondeo
     * exploratorio.
     */
    public static class ResultadoPing {

        private final boolean exito;
        private final String hostConsultado;
        private final String ipResuelta;
        private final int puerto;
        private final long rttMs;
        private final String detalleDiagnostico;

        public ResultadoPing(boolean exito, String hostConsultado, String ipResuelta,
                int puerto, long rttMs, String detalleDiagnostico) {
            this.exito = exito;
            this.hostConsultado = hostConsultado;
            this.ipResuelta = ipResuelta;
            this.puerto = puerto;
            this.rttMs = rttMs;
            this.detalleDiagnostico = detalleDiagnostico;
        }

        public boolean isExito() {
            return exito;
        }

        public String getHostConsultado() {
            return hostConsultado;
        }

        public String getIpResuelta() {
            return ipResuelta;
        }

        public int getPuerto() {
            return puerto;
        }

        public long getRttMs() {
            return rttMs;
        }

        public String getDetalleDiagnostico() {
            return detalleDiagnostico;
        }

        @Override
        public String toString() {
            if (exito) {
                return String.format("[PING OK] Host: %s (%s:%d) | RTT: %d ms | %s",
                        hostConsultado, ipResuelta, puerto, rttMs, detalleDiagnostico);
            } else {
                return String.format("[PING FALLO] Host: %s:%d | Causa: %s",
                        hostConsultado, puerto, detalleDiagnostico);
            }
        }
    }

    /**
     * Ejecuta un sondeo exploratorio con el timeout estándar (2500 ms).
     *
     * @param host Nombre de dominio o dirección IP de destino.
     * @param puerto Puerto TCP de referencia.
     * @return Objeto ResultadoPing con la IP resuelta, RTT en ms o motivo de
     * fallo.
     */
    public ResultadoPing ejecutarSondeo(String host, int puerto) {
        return ejecutarSondeo(host, puerto, TIMEOUT_DEFECTO_MS);
    }

    /**
     * Ejecuta el sondeo exploratorio de conectividad hacia el host y puerto
     * indicados.
     *
     * @param host Nombre de dominio o dirección IP.
     * @param puerto Puerto TCP de destino (1 - 65535).
     * @param timeoutMs Tiempo límite máximo de espera en milisegundos.
     * @return ResultadoPing con las métricas obtenidas.
     */
    public ResultadoPing ejecutarSondeo(String host, int puerto, int timeoutMs) {
        if (host == null || host.trim().isEmpty()) {
            return new ResultadoPing(false, "", "No especificada", puerto, -1, "Host vacío");
        }
        if (puerto < 1 || puerto > 65535) {
            return new ResultadoPing(false, host, "No evaluada", puerto, -1, "Puerto fuera de rango (1-65535)");
        }

        String hostLimpio = host.trim();
        String ipResuelta;
        InetAddress direccionInet;

        // 1. Resolución DNS / Validación de dirección IP
        try {
            direccionInet = InetAddress.getByName(hostLimpio);
            ipResuelta = direccionInet.getHostAddress();
        } catch (UnknownHostException e) {
            return new ResultadoPing(
                    false,
                    hostLimpio,
                    "No resuelto",
                    puerto,
                    -1,
                    "Dominio o IP desconocida (Fallo DNS)"
            );
        }

        // 2. Medición de RTT mediante Socket TCP con control estricto de Timeout
        long inicioNano = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            InetSocketAddress endpoint = new InetSocketAddress(direccionInet, puerto);
            socket.connect(endpoint, timeoutMs);

            long finNano = System.nanoTime();
            long rttMs = Math.max(1L, Math.round((finNano - inicioNano) / 1_000_000.0));

            return new ResultadoPing(
                    true,
                    hostLimpio,
                    ipResuelta,
                    puerto,
                    rttMs,
                    "Conectividad TCP verificada"
            );

        } catch (SocketTimeoutException e) {
            // 3. Respaldo ICMP si el socket TCP expiró por filtrado de puerto
            return intentarRespaldoIcmp(direccionInet, hostLimpio, ipResuelta, puerto, timeoutMs,
                    "Timeout agotado (> " + timeoutMs + " ms)");
        } catch (IOException e) {
            // Conexión rechazada en ese puerto o red inalcanzable -> probar eco ICMP
            return intentarRespaldoIcmp(direccionInet, hostLimpio, ipResuelta, puerto, timeoutMs,
                    "Puerto TCP " + puerto + " cerrado o destino inalcanzable");
        }
    }

    /**
     * Intenta verificar alcanzabilidad mediante paquete ICMP/Echo nativo cuando
     * el puerto TCP falla.
     */
    private ResultadoPing intentarRespaldoIcmp(InetAddress direccionInet, String host,
            String ipResuelta, int puerto, int timeoutMs,
            String motivoFalloTcp) {
        try {
            long inicioNano = System.nanoTime();
            boolean alcanzable = direccionInet.isReachable(timeoutMs);
            long finNano = System.nanoTime();

            if (alcanzable) {
                long rttMs = Math.max(1L, Math.round((finNano - inicioNano) / 1_000_000.0));
                return new ResultadoPing(
                        true,
                        host,
                        ipResuelta,
                        puerto,
                        rttMs,
                        "Respuesta ICMP OK (Puerto TCP " + puerto + " cerrado)"
                );
            }
        } catch (IOException ignored) {
            // Si ICMP también falla, reportamos la causa principal
        }

        return new ResultadoPing(
                false,
                host,
                ipResuelta,
                puerto,
                -1,
                motivoFalloTcp
        );
    }
}
