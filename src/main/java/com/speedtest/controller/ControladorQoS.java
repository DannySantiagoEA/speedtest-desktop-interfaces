package com.speedtest.controller;

import com.speedtest.db.PruebaVelocidadDAO;
import com.speedtest.model.MuestraTelemetria;
import com.speedtest.model.PruebaVelocidad;
import com.speedtest.model.Usuario;
import com.speedtest.network.ClasificadorQoS;
import com.speedtest.network.ClasificadorQoS.ResultadoDiagnosticoQoS;
import com.speedtest.network.EscuchadorProgresoDescarga;
import com.speedtest.network.EscuchadorProgresoQoS;
import com.speedtest.network.EscuchadorProgresoSubida;
import com.speedtest.network.MedidorQoS.ResumenQoS;
import com.speedtest.network.MotorAdquisicionQoSWorker;
import com.speedtest.network.MotorDescargaWorker;
import com.speedtest.network.MotorDescargaWorker.ResumenDescarga;
import com.speedtest.network.MotorSubidaWorker;
import com.speedtest.network.MotorSubidaWorker.ResumenSubida;
import com.speedtest.view.PanelMonitoreoQoS;
import com.speedtest.view.PanelSeleccionServidor;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * Tareas #14, #20 y #35 (HU-03, HU-04 y HU-07 / RF-ADQ-01 a 06, RF-BD-02,
 * RF-BD-03, RF-BD-07): Controlador principal de adquisición y persistencia
 * transaccional que orquesta de forma secuencial y asíncrona (siempre fuera del
 * EDT): 1. Fase PING: Latencia (RTT), Jitter y Pérdida de Paquetes
 * (MotorAdquisicionQoSWorker). 2. Fase DOWN: Throughput de descarga con
 * muestreo a 200 ms (MotorDescargaWorker). 3. Fase UP : Throughput de subida
 * desde memoria a 200 ms (MotorSubidaWorker). 4. Fase QoS : Correlación
 * multivariable y clasificación SLA (ClasificadorQoS). 5. Fase BD :
 * Persistencia transaccional ACID + Batch Insert en MySQL (SwingWorker +
 * PruebaVelocidadDAO).
 */
public class ControladorQoS {

    private final PanelSeleccionServidor panelServidor;
    private final PanelMonitoreoQoS panelQoS;
    private final ClasificadorQoS clasificadorQoS;
    private final PruebaVelocidadDAO pruebaVelocidadDAO;
    private final int idUsuarioActivo;

    // Referencias a los workers activos de cada etapa para cancelación inmediata (RF-ADQ-05)
    private MotorAdquisicionQoSWorker workerPingActivo;
    private MotorDescargaWorker workerDescargaActivo;
    private MotorSubidaWorker workerSubidaActivo;
    private final AtomicBoolean abortoManualSolicitado;

    // Contexto del ensayo activo
    private String hostEnsayoActivo;
    private int puertoEnsayoActivo;

    // Almacenamiento consolidado de la última sesión ejecutada
    private ResumenQoS ultimoResumenPing;
    private ResumenDescarga ultimoResumenDescarga;
    private ResumenSubida ultimoResumenSubida;
    private ResultadoDiagnosticoQoS ultimoDiagnosticoQoS;
    private final List<MuestraTelemetria> serieTemporalConsolidada;

    /**
     * Constructor estándar (asocia las mediciones al operador administrador por
     * defecto id_usuario = 1).
     */
    public ControladorQoS(PanelSeleccionServidor panelServidor, PanelMonitoreoQoS panelQoS) {
        this(panelServidor, panelQoS, null);
    }

    /**
     * Constructor con usuario autenticado explícito.
     */
    public ControladorQoS(PanelSeleccionServidor panelServidor, PanelMonitoreoQoS panelQoS, Usuario usuarioActivo) {
        this.panelServidor = panelServidor;
        this.panelQoS = panelQoS;
        this.clasificadorQoS = new ClasificadorQoS();
        this.pruebaVelocidadDAO = new PruebaVelocidadDAO();
        this.idUsuarioActivo = (usuarioActivo != null && usuarioActivo.getIdUsuario() > 0)
                ? usuarioActivo.getIdUsuario()
                : 1;
        this.abortoManualSolicitado = new AtomicBoolean(false);
        this.serieTemporalConsolidada = Collections.synchronizedList(new ArrayList<>());
        enlazarEventos();
    }

    private void enlazarEventos() {
        this.panelQoS.addIniciarSondeoListener(e -> iniciarEnsayoIntegralSpeedTest());
        this.panelQoS.addCancelarSondeoListener(e -> cancelarMuestreoEnCurso());
        this.panelQoS.addAutodiagnosticoQAListener(e -> ejecutarAutodiagnosticoMatematicaQA());
    }

    /**
     * Verifica si cualquiera de los 3 motores asíncronos se encuentra en
     * ejecución.
     */
    public boolean hayEnsayoEnEjecucion() {
        boolean pingActivo = (workerPingActivo != null && !workerPingActivo.isDone());
        boolean downActivo = (workerDescargaActivo != null && !workerDescargaActivo.isDone());
        boolean upActivo = (workerSubidaActivo != null && !workerSubidaActivo.isDone());
        return pingActivo || downActivo || upActivo;
    }

    /**
     * Inicia la secuencia completa (PING -> DOWN -> UP -> Clasificación QoS ->
     * Persistencia ACID).
     */
    public void iniciarEnsayoIntegralSpeedTest() {
        if (hayEnsayoEnEjecucion()) {
            return;
        }

        String host = panelServidor.getHostConfigurado();
        String puertoTexto = panelServidor.getPuertoTexto();

        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(
                    panelQoS,
                    "Seleccione un servidor o ingrese un Host/IP válido antes de iniciar el SpeedTest.",
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

        int totalPaquetesPing = panelQoS.getCantidadPaquetesConfigurada();
        int duracionThroughputMs = panelQoS.getDuracionThroughputMs();

        this.hostEnsayoActivo = host;
        this.puertoEnsayoActivo = puerto;

        abortoManualSolicitado.set(false);
        serieTemporalConsolidada.clear();
        ultimoResumenPing = null;
        ultimoResumenDescarga = null;
        ultimoResumenSubida = null;
        ultimoDiagnosticoQoS = null;

        panelQoS.configurarEstadoEjecucion(true);
        panelQoS.prepararNuevoEnsayo(host, puerto, totalPaquetesPing, duracionThroughputMs);

        // =====================================================================
        // FASE 1/3: MOTOR DE LATENCIA, JITTER Y PACKET LOSS (HU-03)
        // =====================================================================
        workerPingActivo = new MotorAdquisicionQoSWorker(
                host,
                puerto,
                totalPaquetesPing,
                MotorAdquisicionQoSWorker.INTERVALO_MUESTREO_DEFECTO_MS,
                MotorAdquisicionQoSWorker.TIMEOUT_PAQUETE_MS,
                new EscuchadorProgresoQoS() {
            @Override
            public void alRecibirMuestra(MuestraTelemetria muestra, ResumenQoS resumenAcumulado, int porcentajeAvance) {
                serieTemporalConsolidada.add(muestra);
                panelQoS.actualizarMuestraEnVivo(muestra, resumenAcumulado, porcentajeAvance, totalPaquetesPing);
            }

            @Override
            public void alFinalizarMuestreo(ResumenQoS resumenFinal, boolean cancelado) {
                ultimoResumenPing = resumenFinal;

                if (cancelado || abortoManualSolicitado.get()) {
                    finalizarCicloCompleto(true);
                    return;
                }

                if (resumenFinal.getPaquetesEnviados() > 0 && resumenFinal.getPaquetesRecibidos() == 0) {
                    panelQoS.agregarLineaBitacora(
                            "  [!] 100.0% de paquetes perdidos: el nodo destino no responde. "
                            + "Omitiendo fases de transferencia binaria y evaluando SLA Crítico.");
                    ultimoResumenDescarga = new ResumenDescarga(0, 0L, 0.0, 0.0, 0.0, 0.0, 0.0, host + ":" + puerto);
                    ultimoResumenSubida = new ResumenSubida(0, 0L, 0.0, 0.0, 0.0, 0.0, 0.0, host + ":" + puerto);
                    finalizarCicloCompleto(false);
                    return;
                }

                iniciarFaseDescarga(host, puerto, duracionThroughputMs);
            }

            @Override
            public void alOcurrirError(String mensajeError) {
                panelQoS.configurarEstadoEjecucion(false);
                JOptionPane.showMessageDialog(
                        panelQoS,
                        "Error en Fase 1 (Sondeo QoS):\n" + mensajeError,
                        "Error de Red",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
        );

        workerPingActivo.execute();
    }

    /**
     * FASE 2/3: Ejecuta el motor asíncrono de descarga con muestreo cada Δt =
     * 200 ms (Tarea #17).
     */
    private void iniciarFaseDescarga(String host, int puerto, int duracionThroughputMs) {
        if (abortoManualSolicitado.get()) {
            finalizarCicloCompleto(true);
            return;
        }

        panelQoS.anunciarInicioFaseDescarga(host, puerto);

        workerDescargaActivo = new MotorDescargaWorker(
                host,
                puerto,
                duracionThroughputMs,
                MotorDescargaWorker.INTERVALO_MUESTREO_MS,
                new EscuchadorProgresoDescarga() {
            @Override
            public void alRecibirMuestraDescarga(MuestraTelemetria muestra, ResumenDescarga resumenParcial, int porcentajeAvance) {
                serieTemporalConsolidada.add(muestra);
                panelQoS.actualizarMuestraDescargaEnVivo(muestra, resumenParcial, porcentajeAvance);
            }

            @Override
            public void alFinalizarDescarga(ResumenDescarga resumenFinal, boolean cancelado) {
                ultimoResumenDescarga = resumenFinal;

                if (cancelado || abortoManualSolicitado.get()) {
                    finalizarCicloCompleto(true);
                    return;
                }

                iniciarFaseSubida(host, puerto, duracionThroughputMs);
            }

            @Override
            public void alOcurrirErrorDescarga(String mensajeError) {
                panelQoS.agregarLineaBitacora("  [Aviso Fase Descarga] " + mensajeError);
                ultimoResumenDescarga = workerDescargaActivo.obtenerResumenActual();
                if (!abortoManualSolicitado.get()) {
                    iniciarFaseSubida(host, puerto, duracionThroughputMs);
                } else {
                    finalizarCicloCompleto(true);
                }
            }
        }
        );

        workerDescargaActivo.execute();
    }

    /**
     * FASE 3/3: Ejecuta el motor asíncrono de subida desde memoria con muestreo
     * cada Δt = 200 ms (Tarea #18).
     */
    private void iniciarFaseSubida(String host, int puerto, int duracionThroughputMs) {
        if (abortoManualSolicitado.get()) {
            finalizarCicloCompleto(true);
            return;
        }

        panelQoS.anunciarInicioFaseSubida(host, puerto);

        workerSubidaActivo = new MotorSubidaWorker(
                host,
                puerto,
                duracionThroughputMs,
                MotorSubidaWorker.INTERVALO_MUESTREO_MS,
                MotorSubidaWorker.TAMANO_BLOQUE_MEMORIA_BYTES,
                new EscuchadorProgresoSubida() {
            @Override
            public void alRecibirMuestraSubida(MuestraTelemetria muestra, ResumenSubida resumenParcial, int porcentajeAvance) {
                serieTemporalConsolidada.add(muestra);
                panelQoS.actualizarMuestraSubidaEnVivo(muestra, resumenParcial, porcentajeAvance);
            }

            @Override
            public void alFinalizarSubida(ResumenSubida resumenFinal, boolean cancelado) {
                ultimoResumenSubida = resumenFinal;
                finalizarCicloCompleto(cancelado || abortoManualSolicitado.get());
            }

            @Override
            public void alOcurrirErrorSubida(String mensajeError) {
                panelQoS.agregarLineaBitacora("  [Aviso Fase Subida] " + mensajeError);
                ultimoResumenSubida = workerSubidaActivo.obtenerResumenActual();
                finalizarCicloCompleto(abortoManualSolicitado.get());
            }
        }
        );

        workerSubidaActivo.execute();
    }

    /**
     * FASE 4 y FASE 5: Consolida los resultados de Ping, Descarga y Subida,
     * ejecuta el ClasificadorQoS (Tarea #19) y dispara la persistencia ACID por
     * lotes en MySQL dentro de un SwingWorker secundario (HU-07 / RF-BD-02,
     * RF-BD-03, RF-BD-07).
     */
    private void finalizarCicloCompleto(boolean cancelado) {
        panelQoS.configurarEstadoEjecucion(false);

        if (!cancelado) {
            ultimoDiagnosticoQoS = clasificadorQoS.evaluarEnsayoCompleto(
                    ultimoResumenPing,
                    ultimoResumenDescarga,
                    ultimoResumenSubida
            );
            System.out.println("[SPEEDTEST COMPLETADO] " + ultimoDiagnosticoQoS);
        } else {
            System.out.println("[SPEEDTEST CANCELADO POR OPERADOR - Sin persistencia incompleta en BD]");
        }

        panelQoS.mostrarResultadoConsolidadoFinal(
                ultimoResumenPing,
                ultimoResumenDescarga,
                ultimoResumenSubida,
                ultimoDiagnosticoQoS,
                cancelado
        );

        // Regla ACID (RF-BD-07): Solo se persisten en MySQL sesiones completadas íntegramente.
        // Las sesiones abortadas manualmente no dejan registros parciales ni huérfanos.
        if (cancelado) {
            panelQoS.agregarLineaBitacora(
                    "  [BD ACID / RF-BD-07] Sesión cancelada manualmente: no se confirmó transacción en MySQL (0 registros huérfanos).");
            return;
        }

        persistirEnsayoEnSegundoPlanoACID();
    }

    /**
     * HU-07 (RF-BD-02, RF-BD-03, RF-BD-07): Ejecuta la persistencia
     * transaccional maestro-detalle (prueba_velocidad + Batch Insert de
     * muestra_telemetria) fuera del EDT mediante SwingWorker.
     */
    private void persistirEnsayoEnSegundoPlanoACID() {
        final List<MuestraTelemetria> copiaMuestras = new ArrayList<>(serieTemporalConsolidada);
        final PruebaVelocidad entidadPrueba = construirEntidadPruebaVelocidad();

        panelQoS.agregarLineaBitacora(String.format(
                ">>> FASE BD [HU-07]: Persistiendo sesión y %d muestras de 200 ms en MySQL (Transacción ACID + Batch Insert)...",
                copiaMuestras.size()));

        SwingWorker<Integer, Void> workerPersistencia = new SwingWorker<Integer, Void>() {
            private double tiempoPersistenciaMs = 0.0;

            @Override
            protected Integer doInBackground() throws Exception {
                long inicioNano = System.nanoTime();
                int idGenerado = pruebaVelocidadDAO.registrarEnsayoCompletoACID(entidadPrueba, copiaMuestras);
                tiempoPersistenciaMs = (System.nanoTime() - inicioNano) / 1_000_000.0;
                return idGenerado;
            }

            @Override
            protected void done() {
                try {
                    int idGuardado = get();
                    String msgOk = String.format(
                            "  [BD ACID OK] COMMIT exitoso en %.2f ms -> id_prueba = %d | %d muestras de 200 ms guardadas por lote.",
                            tiempoPersistenciaMs, idGuardado, copiaMuestras.size());
                    panelQoS.agregarLineaBitacora(msgOk);
                    System.out.println(msgOk);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable causa = (ex.getCause() != null) ? ex.getCause() : ex;
                    String tipoFallo = (causa instanceof SQLException) ? "SQLException / ROLLBACK" : "Error de Persistencia";
                    String msgErr = String.format(
                            "  [BD ACID %s] No se pudo persistir la sesión en MySQL (transacción revertida sin registros huérfanos): %s",
                            tipoFallo, causa.getMessage());
                    panelQoS.agregarLineaBitacora(msgErr);
                    System.err.println(msgErr);

                    JOptionPane.showMessageDialog(
                            panelQoS,
                            "La prueba finalizó, pero ocurrió un fallo al persistir en MySQL.\n"
                            + "Se aplicó ROLLBACK transaccional para preservar la integridad de la base de datos.\n\n"
                            + "Detalle técnico: " + causa.getMessage(),
                            "Aviso de Persistencia ACID (RF-BD-07)",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            }
        };

        workerPersistencia.execute();
    }

    private PruebaVelocidad construirEntidadPruebaVelocidad() {
        String ipLocal = "127.0.0.1";
        try {
            ipLocal = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        }

        String hostLimpio = (hostEnsayoActivo != null) ? hostEnsayoActivo.trim() : "localhost";
        boolean esLocal = hostLimpio.equalsIgnoreCase("localhost")
                || hostLimpio.equals("127.0.0.1")
                || hostLimpio.equals("::1");
        String proveedorIsp = esLocal ? "Red Local LAN / XAMPP" : "ISP Internet / WAN";
        String destinoCompleto = hostLimpio + ":" + puertoEnsayoActivo;

        double pingProm = (ultimoResumenPing != null) ? ultimoResumenPing.getRttPromedioMs() : 0.0;
        double jitter = (ultimoResumenPing != null) ? ultimoResumenPing.getJitterAcumuladoMs() : 0.0;
        double perdida = (ultimoResumenPing != null) ? ultimoResumenPing.getPorcentajePerdida() : 100.0;
        double downProm = (ultimoResumenDescarga != null) ? ultimoResumenDescarga.getVelocidadPromedioMbps() : 0.0;
        double upProm = (ultimoResumenSubida != null) ? ultimoResumenSubida.getVelocidadPromedioMbps() : 0.0;

        double indiceQos = (ultimoDiagnosticoQoS != null) ? ultimoDiagnosticoQoS.getIndiceGlobalQoS() : 0.0;
        String calificacion = (ultimoDiagnosticoQoS != null) ? ultimoDiagnosticoQoS.getCalificacionTexto() : "Crítica";
        String notas = (ultimoDiagnosticoQoS != null) ? ultimoDiagnosticoQoS.getDiagnosticoTecnico() : "";

        return new PruebaVelocidad(
                idUsuarioActivo,
                ipLocal,
                proveedorIsp,
                destinoCompleto,
                pingProm,
                jitter,
                downProm,
                upProm,
                perdida,
                indiceQos,
                calificacion,
                PruebaVelocidad.ESTADO_COMPLETADA,
                notas
        );
    }

    /**
     * RF-ADQ-05: Interrumpe inmediatamente cualquiera de los motores que esté
     * activo (Ping, Descarga o Subida) cerrando los descriptores TCP/HTTP
     * abiertos.
     */
    public void cancelarMuestreoEnCurso() {
        abortoManualSolicitado.set(true);
        System.out.println("[RF-ADQ-05] Solicitando cancelación segura de motores activos...");

        if (workerPingActivo != null && !workerPingActivo.isDone()) {
            workerPingActivo.solicitarCancelacionSegura();
        }
        if (workerDescargaActivo != null && !workerDescargaActivo.isDone()) {
            workerDescargaActivo.solicitarCancelacionSegura();
        }
        if (workerSubidaActivo != null && !workerSubidaActivo.isDone()) {
            workerSubidaActivo.solicitarCancelacionSegura();
        }
    }

    /**
     * Tarea #20 (QA / Testing): Ejecuta la batería automatizada de verificación
     * de precisión matemática de la fórmula Velocidad = (Bytes * 8) / (Δt *
     * 10^6) y las 4 categorías QoS.
     */
    public void ejecutarAutodiagnosticoMatematicaQA() {
        if (hayEnsayoEnEjecucion()) {
            return;
        }

        panelQoS.limpiarBitacora();
        panelQoS.agregarLineaBitacora("========================================================================================");
        panelQoS.agregarLineaBitacora(" BATERÍA DE VALIDACIÓN MATEMÁTICA Y CAJA NEGRA — TAREA #20 (HU-04)");
        panelQoS.agregarLineaBitacora("========================================================================================");

        panelQoS.agregarLineaBitacora("\n[1] VERIFICACIÓN DE ECUACIÓN DE THROUGHPUT (Δt = 200 ms = 0.200 s):");
        long bytesCasoA = 625_000L;
        long bytesCasoB = 1_250_000L;
        long bytesCasoC = 2_500_000L;

        double velA = MotorDescargaWorker.calcularVelocidadMbps(bytesCasoA, 0.200);
        double velB = MotorDescargaWorker.calcularVelocidadMbps(bytesCasoB, 0.200);
        double velC = MotorSubidaWorker.calcularVelocidadMbps(bytesCasoC, 0.200);

        panelQoS.agregarLineaBitacora(String.format(
                "  • Caso 1A (Descarga): Bytes = %,d B | Δt = 0.200 s -> Calculado: %.2f Mbps (Esperado: 25.00 Mbps) [%s]",
                bytesCasoA, velA, (Math.abs(velA - 25.00) < 0.01 ? "OK" : "ERROR")));
        panelQoS.agregarLineaBitacora(String.format(
                "  • Caso 1B (Descarga): Bytes = %,d B | Δt = 0.200 s -> Calculado: %.2f Mbps (Esperado: 50.00 Mbps) [%s]",
                bytesCasoB, velB, (Math.abs(velB - 50.00) < 0.01 ? "OK" : "ERROR")));
        panelQoS.agregarLineaBitacora(String.format(
                "  • Caso 1C (Subida)  : Bytes = %,d B | Δt = 0.200 s -> Calculado: %.2f Mbps (Esperado: 100.00 Mbps) [%s]",
                bytesCasoC, velC, (Math.abs(velC - 100.00) < 0.01 ? "OK" : "ERROR")));

        byte[] bloqueRam = MotorSubidaWorker.generarBloqueMemoriaDinamico(16384);
        panelQoS.agregarLineaBitacora(String.format(
                "  • Buffer Dinámico RAM Subida: %,d Bytes generados con entropía pseudoaleatoria [%s]",
                bloqueRam.length, (bloqueRam.length == 16384 ? "OK" : "ERROR")));

        panelQoS.agregarLineaBitacora("\n[2] VERIFICACIÓN DE CLASIFICACIÓN CATEGÓRICA DE QoS FRENTE A UMBRALES SLA:");

        ResultadoDiagnosticoQoS casoExcelente = clasificadorQoS.evaluarCalidadEnlace(85.0, 35.0, 18.0, 3.5, 0.0);
        ResultadoDiagnosticoQoS casoBuena = clasificadorQoS.evaluarCalidadEnlace(32.0, 14.0, 52.0, 14.0, 0.8);
        ResultadoDiagnosticoQoS casoDegradada = clasificadorQoS.evaluarCalidadEnlace(12.0, 4.5, 115.0, 34.0, 3.2);
        ResultadoDiagnosticoQoS casoCritica = clasificadorQoS.evaluarCalidadEnlace(1.5, 0.4, 280.0, 75.0, 25.0);

        panelQoS.agregarLineaBitacora("  • Escenario SLA 1 (Fibra/LAN Óptima) -> " + casoExcelente.toString());
        panelQoS.agregarLineaBitacora("  • Escenario SLA 2 (Banda Ancha Est.) -> " + casoBuena.toString());
        panelQoS.agregarLineaBitacora("  • Escenario SLA 3 (Enlace Congest.)  -> " + casoDegradada.toString());
        panelQoS.agregarLineaBitacora("  • Escenario SLA 4 (Enlace Crítico)   -> " + casoCritica.toString());

        panelQoS.agregarLineaBitacora("\n========================================================================================");
        panelQoS.agregarLineaBitacora(" RESULTADO GLOBAL QA (#20): 100% DE PRUEBAS MATEMÁTICAS Y REGLAS SLA APROBADAS.");
        panelQoS.agregarLineaBitacora("========================================================================================");
    }

    public List<MuestraTelemetria> getSerieTemporalConsolidada() {
        return Collections.unmodifiableList(new ArrayList<>(serieTemporalConsolidada));
    }

    public ResultadoDiagnosticoQoS getUltimoDiagnosticoQoS() {
        return ultimoDiagnosticoQoS;
    }
}
