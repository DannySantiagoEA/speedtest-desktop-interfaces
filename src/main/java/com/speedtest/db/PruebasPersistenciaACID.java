package com.speedtest.db;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.model.PruebaVelocidad;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tarea #35 (HU-07 / RF-BD-02, RF-BD-03, RF-BD-06, RF-BD-07): Suite formal de
 * pruebas de caja negra para validar: - Caso 1: Persistencia masiva por lotes
 * (Batch Insert) de cientos de muestras (600 muestras a 200 ms). - Caso 2:
 * Captura de SQLException en medio del Batch Insert y reversión atómica
 * (ROLLBACK) sin dejar cabeceras ni muestras huérfanas. - Caso 3: Simulación de
 * desconexión/aborto abrupto de conexión JDBC antes del COMMIT, comprobando
 * reversión automática en InnoDB y cero registros huérfanos. - Caso 4:
 * Verificación de claves foráneas (FOREIGN KEY) en information_schema, rechazo
 * de muestras con FK inválida y purga automática en cascada (ON DELETE
 * CASCADE).
 */
public class PruebasPersistenciaACID {

    private final PruebaVelocidadDAO pruebaDAO = new PruebaVelocidadDAO();
    private final MuestraTelemetriaDAO muestraDAO = new MuestraTelemetriaDAO();

    public static void main(String[] args) {
        PruebasPersistenciaACID suite = new PruebasPersistenciaACID();
        suite.ejecutarBateriaCompleta();
    }

    public void ejecutarBateriaCompleta() {
        System.out.println("==========================================================================================");
        System.out.println(" SUITE DE PRUEBAS DE CAJA NEGRA - TAREA #35 (HU-07: PERSISTENCIA ACID Y ROLLBACK)");
        System.out.println("==========================================================================================");

        int pruebasAprobadas = 0;
        final int totalPruebas = 4;
        int idPruebaEstres = -1;

        try {
            // =================================================================
            // CASO 1: ESTRES BATCH INSERT (600 MUESTRAS A 200 ms + SUB-LOTES 500 + 100)
            // =================================================================
            System.out.println("\n[CASO 1] ESTRES BATCH INSERT: 600 muestras de telemetria a 200 ms (50 PING + 275 DOWN + 275 UP)");
            int sesionesAntesCaso1 = contarTotalFilas("prueba_velocidad");
            int muestrasAntesCaso1 = contarTotalFilas("muestra_telemetria");

            List<MuestraTelemetria> lote600 = generarSerieEstres(50, 275, 275);
            PruebaVelocidad sesionEstres = crearPruebaEjemplo(
                    "speed.cloudflare.com:443",
                    "Prueba de estres QA #35: 600 muestras (120 seg equivalentes a 5 Hz)"
            );

            long inicioNano = System.nanoTime();
            idPruebaEstres = pruebaDAO.registrarEnsayoCompletoACID(sesionEstres, lote600);
            double duracionMs = (System.nanoTime() - inicioNano) / 1_000_000.0;

            int sesionesDespuesCaso1 = contarTotalFilas("prueba_velocidad");
            int muestrasDespuesCaso1 = contarTotalFilas("muestra_telemetria");
            int muestrasAsociadas = muestraDAO.contarMuestrasPorPrueba(idPruebaEstres);
            int huerfanosCaso1 = contarMuestrasHuerfanas();

            boolean okCaso1 = (idPruebaEstres > 0)
                    && (sesionesDespuesCaso1 == sesionesAntesCaso1 + 1)
                    && (muestrasDespuesCaso1 == muestrasAntesCaso1 + 600)
                    && (muestrasAsociadas == 600)
                    && (huerfanosCaso1 == 0);

            System.out.printf("  -> ID Padre generado (RETURN_GENERATED_KEYS): id_prueba = %d%n", idPruebaEstres);
            System.out.printf("  -> Tiempo total transaccion ACID (1 padre + 600 muestras en Batch): %.2f ms%n", duracionMs);
            System.out.printf("  -> Verificacion en MySQL: Sesiones (+%d), Muestras vinculadas=%d/600, Huerfanos=%d%n",
                    (sesionesDespuesCaso1 - sesionesAntesCaso1), muestrasAsociadas, huerfanosCaso1);
            System.out.println("  -> RESULTADO CASO 1: " + (okCaso1 ? "[APROBADO]" : "[FALLIDO]"));
            if (okCaso1) {
                pruebasAprobadas++;
            }

            // =================================================================
            // CASO 2: ABORTO FORZADO EN MEDIO DEL BATCH INSERT -> SQLException + ROLLBACK
            // =================================================================
            System.out.println("\n[CASO 2] REVERSION POR ROLLBACK ANTE SQLException EN MEDIO DEL LOTE (Muestra #250 corrupta)");
            int sesionesAntesCaso2 = contarTotalFilas("prueba_velocidad");
            int muestrasAntesCaso2 = contarTotalFilas("muestra_telemetria");

            // Construimos un lote de 300 muestras e inyectamos un desbordamiento VARCHAR(10) en la muestra #250
            // para forzar una BatchUpdateException/SQLException real en MySQL DESPUES de insertar la fila padre.
            List<MuestraTelemetria> loteConFallo = generarSerieEstres(50, 150, 100);
            loteConFallo.get(249).setEtapa("ETAPA_INVALIDA_QUE_EXCEDE_VARCHAR_10_PARA_FORZAR_ROLLBACK");

            PruebaVelocidad sesionAbortada = crearPruebaEjemplo(
                    "8.8.8.8:53",
                    "Esta sesion padre NO debe quedar guardada: debe revertirse por ROLLBACK"
            );

            boolean excepcionCapturadaCaso2 = false;
            try {
                pruebaDAO.registrarEnsayoCompletoACID(sesionAbortada, loteConFallo);
            } catch (SQLException exEsperada) {
                excepcionCapturadaCaso2 = true;
                System.out.println("  -> SQLException capturada correctamente durante executeBatch(): " + exEsperada.getMessage());
            }

            int sesionesDespuesCaso2 = contarTotalFilas("prueba_velocidad");
            int muestrasDespuesCaso2 = contarTotalFilas("muestra_telemetria");
            int huerfanosCaso2 = contarMuestrasHuerfanas();

            boolean okCaso2 = excepcionCapturadaCaso2
                    && (sesionesDespuesCaso2 == sesionesAntesCaso2)
                    && (muestrasDespuesCaso2 == muestrasAntesCaso2)
                    && (huerfanosCaso2 == 0);

            System.out.printf("  -> Verificacion post-ROLLBACK: Sesiones antes=%d / despues=%d | Muestras antes=%d / despues=%d | Huerfanos=%d%n",
                    sesionesAntesCaso2, sesionesDespuesCaso2, muestrasAntesCaso2, muestrasDespuesCaso2, huerfanosCaso2);
            System.out.println("  -> RESULTADO CASO 2: " + (okCaso2 ? "[APROBADO]" : "[FALLIDO]"));
            if (okCaso2) {
                pruebasAprobadas++;
            }

            // =================================================================
            // CASO 3: SIMULACION DE DESCONEXION ABRUPTA DE BD ANTES DEL COMMIT
            // =================================================================
            System.out.println("\n[CASO 3] SIMULACION DE DESCONEXION ABRUPTA DE CONEXION JDBC EN MEDIO DE LA TRANSACCION");
            int sesionesAntesCaso3 = contarTotalFilas("prueba_velocidad");
            int muestrasAntesCaso3 = contarTotalFilas("muestra_telemetria");
            boolean falloDesconexionCapturado = false;

            Connection connSimulada = null;
            try {
                connSimulada = ConexionBD.getConexion();
                connSimulada.setAutoCommit(false);

                // 1. Se inserta la cabecera padre dentro de la transacción abierta
                PruebaVelocidad sesionCorte = crearPruebaEjemplo(
                        "1.1.1.1:53",
                        "Sesion interrumpida por caida de conexion JDBC"
                );
                int idTemporal = pruebaDAO.insertarEnTransaccion(connSimulada, sesionCorte);
                System.out.println("  -> Cabecera temporal insertada en transaccion sin confirmar (id_prueba=" + idTemporal + ").");

                // 2. Se simula desconexión física abrupta cerrando la conexión antes del Batch Insert y del commit
                connSimulada.close();
                System.out.println("  -> Desconexion abrupta simulada (conn.close() ejecutado antes de insertar muestras y antes de commit).");

                // 3. Al intentar continuar el lote sobre la conexión caída, debe lanzar SQLException
                muestraDAO.insertarLoteEnTransaccion(connSimulada, idTemporal, generarSerieEstres(10, 10, 10));

            } catch (SQLException exCorte) {
                falloDesconexionCapturado = true;
                System.out.println("  -> SQLException capturada ante conexion caida: " + exCorte.getMessage());
            } finally {
                if (connSimulada != null) {
                    try {
                        if (!connSimulada.isClosed()) {
                            connSimulada.rollback();
                            connSimulada.close();
                        }
                    } catch (SQLException ignored) {
                    }
                }
            }

            // Verificar desde una nueva conexión que InnoDB revirtió íntegramente la transacción no confirmada
            int sesionesDespuesCaso3 = contarTotalFilas("prueba_velocidad");
            int muestrasDespuesCaso3 = contarTotalFilas("muestra_telemetria");
            int huerfanosCaso3 = contarMuestrasHuerfanas();

            boolean okCaso3 = falloDesconexionCapturado
                    && (sesionesDespuesCaso3 == sesionesAntesCaso3)
                    && (muestrasDespuesCaso3 == muestrasAntesCaso3)
                    && (huerfanosCaso3 == 0);

            System.out.printf("  -> Verificacion post-desconexion: Sesiones antes=%d / despues=%d | Muestras antes=%d / despues=%d | Huerfanos=%d%n",
                    sesionesAntesCaso3, sesionesDespuesCaso3, muestrasAntesCaso3, muestrasDespuesCaso3, huerfanosCaso3);
            System.out.println("  -> RESULTADO CASO 3: " + (okCaso3 ? "[APROBADO]" : "[FALLIDO]"));
            if (okCaso3) {
                pruebasAprobadas++;
            }

            // =================================================================
            // CASO 4: INTEGRIDAD REFERENCIAL (FOREIGN KEY) Y ON DELETE CASCADE
            // =================================================================
            System.out.println("\n[CASO 4] VERIFICACION DE CLAVES FORANEAS (FK), RECHAZO DE HUERFANOS Y ON DELETE CASCADE");

            // 4A. Consultar metadatos de MySQL (information_schema)
            String ddlReporte = ConexionBD.verificarIntegridadReferencialDDL();
            System.out.println("  -> Metadatos DDL activos en information_schema:");
            for (String linea : ddlReporte.split("\\R")) {
                System.out.println("     " + linea);
            }
            boolean tieneCascade = ddlReporte.contains("fk_muestra_prueba") && ddlReporte.contains("CASCADE");

            // 4B. Intentar insertar muestras apuntando a una FK inexistente (id_prueba = 999999)
            boolean fkRechazoHuerfano = false;
            try {
                muestraDAO.insertarLoteTransaccional(999999, generarSerieEstres(5, 0, 0));
            } catch (SQLException exFk) {
                fkRechazoHuerfano = true;
                System.out.println("  -> Insercion de muestra con FK inexistente (id_prueba=999999) rechazada por MySQL: OK");
            }

            // 4C. Eliminar la sesión padre de estrés del Caso 1 y verificar purga en cascada de las 600 muestras
            boolean borradoPadre = pruebaDAO.eliminarPorId(idPruebaEstres);
            int muestrasTrasCascade = muestraDAO.contarMuestrasPorPrueba(idPruebaEstres);
            int huerfanosFinales = contarMuestrasHuerfanas();

            boolean okCaso4 = tieneCascade && fkRechazoHuerfano && borradoPadre
                    && (muestrasTrasCascade == 0) && (huerfanosFinales == 0);

            System.out.printf("  -> Eliminacion padre id_prueba=%d: %s | Muestras hijas restantes tras ON DELETE CASCADE: %d | Huerfanos globales: %d%n",
                    idPruebaEstres, (borradoPadre ? "OK" : "ERROR"), muestrasTrasCascade, huerfanosFinales);
            System.out.println("  -> RESULTADO CASO 4: " + (okCaso4 ? "[APROBADO]" : "[FALLIDO]"));
            if (okCaso4) {
                pruebasAprobadas++;
            }

            // =================================================================
            // RESUMEN FINAL DE LA SUITE QA (TAREA #35)
            // =================================================================
            System.out.println("\n==========================================================================================");
            System.out.printf(" VEREDICTO FINAL TAREA #35 (HU-07): %d / %d PRUEBAS DE CAJA NEGRA ACID APROBADAS (%s)%n",
                    pruebasAprobadas, totalPruebas, (pruebasAprobadas == totalPruebas ? "100% EXITOSO" : "REVISAR"));
            System.out.println("==========================================================================================");

        } catch (Exception ex) {
            System.err.println("[ERROR CRITICO SUITE QA #35] " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private PruebaVelocidad crearPruebaEjemplo(String destino, String nota) {
        return new PruebaVelocidad(
                1,
                "192.168.1.20",
                "ISP Fibra Optica EIEE",
                destino,
                21.40,
                2.35,
                142.80,
                54.20,
                0.0,
                96.40,
                "Excelente",
                PruebaVelocidad.ESTADO_COMPLETADA,
                nota
        );
    }

    private List<MuestraTelemetria> generarSerieEstres(int nPing, int nDown, int nUp) {
        List<MuestraTelemetria> lista = new ArrayList<>(nPing + nDown + nUp);
        for (int i = 1; i <= nPing; i++) {
            double rtt = 20.0 + (i % 5) * 0.8;
            lista.add(MuestraTelemetria.crearMuestraPing(i, i * 0.200, rtt, (i == 1 ? 0.0 : 0.8), false));
        }
        for (int i = 1; i <= nDown; i++) {
            long bytes = 3_500_000L; // 140 Mbps en 200 ms
            double vel = (bytes * 8.0) / (0.200 * 1_000_000.0);
            lista.add(MuestraTelemetria.crearMuestraDescarga(i, i * 0.200, bytes, vel));
        }
        for (int i = 1; i <= nUp; i++) {
            long bytes = 1_350_000L; // 54 Mbps en 200 ms
            double vel = (bytes * 8.0) / (0.200 * 1_000_000.0);
            lista.add(MuestraTelemetria.crearMuestraSubida(i, i * 0.200, bytes, vel));
        }
        return lista;
    }

    private int contarTotalFilas(String tabla) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM " + tabla;
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    /**
     * Consulta SQL de auditoría que detecta cualquier muestra en
     * 'muestra_telemetria' cuya clave foránea no exista en 'prueba_velocidad'
     * (debe ser siempre 0).
     */
    private int contarMuestrasHuerfanas() throws SQLException {
        String sql = "SELECT COUNT(*) AS huerfanos "
                + "FROM muestra_telemetria m "
                + "LEFT JOIN prueba_velocidad p ON m.id_prueba = p.id_prueba "
                + "WHERE p.id_prueba IS NULL";
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
            return rs.next() ? rs.getInt("huerfanos") : 0;
        }
    }
}
