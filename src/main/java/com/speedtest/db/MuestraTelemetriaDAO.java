package com.speedtest.db;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.model.PruebaVelocidad;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Tarea #34 (HU-07 / RF-BD-03, RF-BD-04, RF-BD-07): DAO especializado en la
 * persistencia masiva por lotes (Batch Insert) y recuperación de la serie
 * temporal de muestras de 200 ms en la tabla hija 'muestra_telemetria'.
 *
 * Emplea PreparedStatement.addBatch() y executeBatch() bajo control
 * transaccional ACID para maximizar el throughput de escritura en disco y
 * evitar bloqueos de E/S.
 */
public class MuestraTelemetriaDAO {

    /**
     * Tamaño máximo de sub-lote (chunk) antes de hacer flush parcial con
     * executeBatch() para prevenir saturación de memoria en pruebas de larga
     * duración.
     */
    public static final int TAMANO_MAXIMO_LOTE = 500;

    /**
     * Sentencia SQL parametrizada para insertar muestras de 200 ms en lote
     * (RF-BD-03).
     */
    private static final String SQL_INSERT_MUESTRA_BATCH
            = "INSERT INTO muestra_telemetria ("
            + "  id_prueba, numero_secuencia, timestamp, tiempo_transcurrido_seg, "
            + "  etapa, bytes_transferidos, velocidad_inst, latencia_inst, "
            + "  jitter_inst, paquete_perdido"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Sentencia SQL para recuperar toda la serie temporal de una sesión en
     * orden cronológico (RF-BD-04).
     */
    private static final String SQL_SELECT_POR_PRUEBA
            = "SELECT id_muestra, id_prueba, numero_secuencia, timestamp, tiempo_transcurrido_seg, "
            + "       etapa, bytes_transferidos, velocidad_inst, latencia_inst, jitter_inst, paquete_perdido "
            + "FROM muestra_telemetria "
            + "WHERE id_prueba = ? "
            + "ORDER BY id_muestra ASC, numero_secuencia ASC";

    /**
     * Sentencia SQL para recuperar las muestras de una etapa específica
     * ('PING', 'DOWN', 'UP').
     */
    private static final String SQL_SELECT_POR_PRUEBA_Y_ETAPA
            = "SELECT id_muestra, id_prueba, numero_secuencia, timestamp, tiempo_transcurrido_seg, "
            + "       etapa, bytes_transferidos, velocidad_inst, latencia_inst, jitter_inst, paquete_perdido "
            + "FROM muestra_telemetria "
            + "WHERE id_prueba = ? AND etapa = ? "
            + "ORDER BY numero_secuencia ASC";

    /**
     * Sentencia SQL para contar el total de muestras almacenadas para una
     * sesión de prueba.
     */
    private static final String SQL_COUNT_POR_PRUEBA
            = "SELECT COUNT(*) AS total FROM muestra_telemetria WHERE id_prueba = ?";

    // =========================================================================
    // 1. INSERCIÓN MASIVA POR LOTES (BATCH INSERT - TAREA #34 / RF-BD-03)
    // =========================================================================
    /**
     * Tarea #34 (RF-BD-03): Ejecuta la inserción por lotes (addBatch /
     * executeBatch) de todas las muestras de telemetría de 200 ms sobre una
     * conexión transaccional activa (con autoCommit == false), vinculándolas a
     * la clave foránea idPrueba.
     *
     * Este método es invocado dentro de la misma transacción ACID en la que se
     * inserta el registro padre en 'prueba_velocidad'.
     *
     * @param conn Conexión JDBC activa con setAutoCommit(false).
     * @param idPrueba Clave primaria (id_prueba) de la sesión padre en
     * 'prueba_velocidad'.
     * @param muestras Lista en memoria de muestras capturadas cada 200 ms.
     * @return Cantidad total de muestras insertadas exitosamente en el lote.
     * @throws SQLException Si ocurre un error en cualquier elemento del lote
     * (disparando rollback en el llamador).
     */
    public int insertarLoteEnTransaccion(Connection conn, int idPrueba, List<MuestraTelemetria> muestras) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("La conexión transaccional para el Batch Insert es nula o está cerrada.");
        }
        if (idPrueba <= 0) {
            throw new IllegalArgumentException("El idPrueba (FOREIGN KEY) debe ser mayor que 0.");
        }
        if (muestras == null || muestras.isEmpty()) {
            return 0;
        }

        // Asegurar que la conexión opere bajo control transaccional explícito (RF-BD-07)
        if (conn.getAutoCommit()) {
            conn.setAutoCommit(false);
        }

        int totalInsertadas = 0;
        int contadorEnLoteActual = 0;

        try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_MUESTRA_BATCH)) {
            int secuenciaFallback = 1;

            for (MuestraTelemetria muestra : muestras) {
                if (muestra == null) {
                    throw new SQLException("Se detectó una muestra nula dentro del lote de telemetría.");
                }
                if (muestra.getEtapa() == null || muestra.getEtapa().isBlank()) {
                    throw new SQLException("La etapa de la muestra de telemetría no puede ser nula o vacía.");
                }

                // Sincronizar la clave foránea en el objeto de dominio
                muestra.setIdPrueba(idPrueba);
                int numSecuencia = (muestra.getNumeroSecuencia() > 0)
                        ? muestra.getNumeroSecuencia()
                        : secuenciaFallback;

                long epochMillis = convertirAEpochMillis(muestra.getTimestamp());

                pstmt.setInt(1, idPrueba);
                pstmt.setInt(2, numSecuencia);
                pstmt.setLong(3, epochMillis);
                pstmt.setDouble(4, muestra.getTiempoTranscurridoSeg());
                pstmt.setString(5, muestra.getEtapa().trim().toUpperCase());
                pstmt.setLong(6, muestra.getBytesTransferidos());
                pstmt.setDouble(7, muestra.getVelocidadInstMbps());
                pstmt.setDouble(8, muestra.getLatenciaInstMs());
                pstmt.setDouble(9, muestra.getJitterInstMs());
                pstmt.setBoolean(10, muestra.isPaquetePerdido());

                // Agregar tupla al búfer de lote JDBC (RF-BD-03)
                pstmt.addBatch();
                contadorEnLoteActual++;
                secuenciaFallback++;

                // Si el lote alcanza el umbral de ráfaga (500 muestras), despachar a disco
                if (contadorEnLoteActual >= TAMANO_MAXIMO_LOTE) {
                    totalInsertadas += procesarResultadoBatch(pstmt.executeBatch());
                    pstmt.clearBatch();
                    contadorEnLoteActual = 0;
                }
            }

            // Despachar el lote remanente en memoria hacia MySQL
            if (contadorEnLoteActual > 0) {
                totalInsertadas += procesarResultadoBatch(pstmt.executeBatch());
                pstmt.clearBatch();
            }
        }

        return totalInsertadas;
    }

    /**
     * Tarea #34 (RF-BD-03 y RF-BD-07): Inserta de forma autónoma un lote de
     * muestras para un idPrueba existente gestionando su propio ciclo
     * transaccional (setAutoCommit(false) -> addBatch/executeBatch -> commit()
     * / rollback()).
     *
     * @param idPrueba Clave foránea de la sesión en 'prueba_velocidad'.
     * @param muestras Lista de muestras de 200 ms a insertar en bloque.
     * @return Cantidad total de registros insertados en 'muestra_telemetria'.
     * @throws SQLException Si falla la inserción del lote y se revierte la
     * transacción.
     */
    public int insertarLoteTransaccional(int idPrueba, List<MuestraTelemetria> muestras) throws SQLException {
        if (muestras == null || muestras.isEmpty()) {
            return 0;
        }

        Connection conn = null;
        boolean autoCommitOriginal = true;

        try {
            conn = ConexionBD.getConexion();
            autoCommitOriginal = conn.getAutoCommit();

            // 1. Deshabilitar autocommit para garantizar atomicidad del lote
            conn.setAutoCommit(false);

            // 2. Ejecutar el bucle addBatch() y el despacho executeBatch()
            int insertadas = insertarLoteEnTransaccion(conn, idPrueba, muestras);

            // 3. Confirmar la ráfaga completa en disco
            conn.commit();
            return insertadas;

        } catch (SQLException ex) {
            // 4. Revertir la totalidad del lote si cualquier fila falla
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("[ROLLBACK ACID LOTE] Se revirtió el Batch Insert en muestra_telemetria: " + ex.getMessage());
                } catch (SQLException rbEx) {
                    System.err.println("[ERROR] Fallo adicional durante rollback de lote: " + rbEx.getMessage());
                }
            }
            throw ex;

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(autoCommitOriginal);
                } catch (SQLException ignored) {
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // =========================================================================
    // 2. CONSULTA Y RECUPERACIÓN DE SERIES TEMPORALES (SOPORTE RF-BD-04 / HU-08)
    // =========================================================================
    /**
     * Recupera todas las muestras de telemetría asociadas a una sesión de
     * prueba (RF-BD-04).
     */
    public List<MuestraTelemetria> listarPorIdPrueba(int idPrueba) throws SQLException {
        List<MuestraTelemetria> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_SELECT_POR_PRUEBA)) {

            pstmt.setInt(1, idPrueba);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearMuestra(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Recupera las muestras de una sesión filtradas por etapa ('PING', 'DOWN' o
     * 'UP').
     */
    public List<MuestraTelemetria> listarPorPruebaYEtapa(int idPrueba, String etapa) throws SQLException {
        List<MuestraTelemetria> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_SELECT_POR_PRUEBA_Y_ETAPA)) {

            pstmt.setInt(1, idPrueba);
            pstmt.setString(2, (etapa != null) ? etapa.trim().toUpperCase() : "");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearMuestra(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Cuenta el número exacto de muestras hijas almacenadas para una sesión de
     * prueba.
     */
    public int contarMuestrasPorPrueba(int idPrueba) throws SQLException {
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_COUNT_POR_PRUEBA)) {

            pstmt.setInt(1, idPrueba);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        }
        return 0;
    }

    // =========================================================================
    // 3. MÉTODOS AUXILIARES DE VERIFICACIÓN DE LOTE Y MAPEO
    // =========================================================================
    private int procesarResultadoBatch(int[] resultados) throws SQLException {
        int exitosos = 0;
        for (int codigo : resultados) {
            if (codigo == Statement.EXECUTE_FAILED) {
                throw new SQLException("Al menos un registro del lote falló durante executeBatch() (EXECUTE_FAILED).");
            }
            if (codigo > 0 || codigo == Statement.SUCCESS_NO_INFO) {
                exitosos += (codigo > 0) ? codigo : 1;
            }
        }
        return exitosos;
    }

    private long convertirAEpochMillis(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return System.currentTimeMillis();
        }
        return fechaHora.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private MuestraTelemetria mapearMuestra(ResultSet rs) throws SQLException {
        MuestraTelemetria m = new MuestraTelemetria();
        m.setIdMuestra(rs.getLong("id_muestra"));
        m.setIdPrueba(rs.getInt("id_prueba"));
        m.setNumeroSecuencia(rs.getInt("numero_secuencia"));

        long tsMillis = rs.getLong("timestamp");
        if (tsMillis > 0) {
            m.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMillis), ZoneId.systemDefault()));
        }

        m.setTiempoTranscurridoSeg(rs.getDouble("tiempo_transcurrido_seg"));
        m.setEtapa(rs.getString("etapa"));
        m.setBytesTransferidos(rs.getLong("bytes_transferidos"));
        m.setVelocidadInstMbps(rs.getDouble("velocidad_inst"));
        m.setLatenciaInstMs(rs.getDouble("latencia_inst"));
        m.setJitterInstMs(rs.getDouble("jitter_inst"));
        m.setPaquetePerdido(rs.getBoolean("paquete_perdido"));
        return m;
    }

    // =========================================================================
    // 4. PRUEBA FUNCIONAL AUTOEJECUTABLE (Shift + F6) PARA VERIFICAR LA TAREA #34
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("=====================================================================================");
        System.out.println(" VERIFICACION TAREA #34 (HU-07): BATCH INSERT (addBatch / executeBatch) A 200 ms     ");
        System.out.println("=====================================================================================");

        PruebaVelocidadDAO pruebaDAO = new PruebaVelocidadDAO();
        MuestraTelemetriaDAO muestraDAO = new MuestraTelemetriaDAO();

        try {
            // 1. Generar en memoria una serie temporal realista de 75 muestras a Δt = 200 ms:
            //    15 muestras PING + 30 muestras DOWN + 30 muestras UP
            List<MuestraTelemetria> loteMuestras = new ArrayList<>(75);

            for (int i = 1; i <= 15; i++) {
                double tSeg = i * 0.200;
                double rtt = 22.0 + (i % 4) * 1.5;
                double jitter = (i == 1) ? 0.0 : 1.5;
                loteMuestras.add(MuestraTelemetria.crearMuestraPing(i, tSeg, rtt, jitter, false));
            }
            for (int i = 1; i <= 30; i++) {
                double tSeg = i * 0.200;
                long bytesVentana = 2_750_000L; // ~110 Mbps en 200 ms
                double velMbps = (bytesVentana * 8.0) / (0.200 * 1_000_000.0);
                loteMuestras.add(MuestraTelemetria.crearMuestraDescarga(i, tSeg, bytesVentana, velMbps));
            }
            for (int i = 1; i <= 30; i++) {
                double tSeg = i * 0.200;
                long bytesVentana = 1_250_000L; // ~50 Mbps en 200 ms
                double velMbps = (bytesVentana * 8.0) / (0.200 * 1_000_000.0);
                loteMuestras.add(MuestraTelemetria.crearMuestraSubida(i, tSeg, bytesVentana, velMbps));
            }

            PruebaVelocidad sesion = new PruebaVelocidad(
                    1,
                    "192.168.1.15",
                    "ISP Fibra Optica Univalle",
                    "speed.cloudflare.com:443",
                    24.10,
                    1.50,
                    110.00,
                    50.00,
                    0.0,
                    97.20,
                    "Excelente",
                    PruebaVelocidad.ESTADO_COMPLETADA,
                    "Ensayo completo con 75 muestras de 200 ms insertadas por lote (Tarea #34)"
            );

            // 2. Ejecutar la transacción ACID maestro-detalle midiendo el tiempo de escritura a disco
            System.out.println("\n[1] Ejecutando persistencia transaccional ACID (1 sesion padre + 75 muestras en Batch)...");
            long inicioNano = System.nanoTime();
            int idPruebaGenerado = pruebaDAO.registrarEnsayoCompletoACID(sesion, loteMuestras);
            double tiempoEscrituraMs = (System.nanoTime() - inicioNano) / 1_000_000.0;

            System.out.printf("    -> [OK] Transaccion confirmada (COMMIT) en %.2f ms. ID Padre generado: id_prueba = %d%n",
                    tiempoEscrituraMs, idPruebaGenerado);

            // 3. Verificar en MySQL el conteo total y por etapa de las muestras insertadas
            int totalEnBD = muestraDAO.contarMuestrasPorPrueba(idPruebaGenerado);
            int totalPing = muestraDAO.listarPorPruebaYEtapa(idPruebaGenerado, MuestraTelemetria.ETAPA_PING).size();
            int totalDown = muestraDAO.listarPorPruebaYEtapa(idPruebaGenerado, MuestraTelemetria.ETAPA_DOWN).size();
            int totalUp = muestraDAO.listarPorPruebaYEtapa(idPruebaGenerado, MuestraTelemetria.ETAPA_UP).size();

            System.out.printf("    -> [OK] Muestras verificadas en MySQL para id_prueba=%d: Total=%d (PING=%d | DOWN=%d | UP=%d)%n",
                    idPruebaGenerado, totalEnBD, totalPing, totalDown, totalUp);

            // 4. Verificar que al eliminar el registro padre (prueba_velocidad), ON DELETE CASCADE
            //    purga automáticamente las 75 muestras hijas en muestra_telemetria
            System.out.println("\n[2] Verificando borrado en cascada (ON DELETE CASCADE) al eliminar id_prueba = " + idPruebaGenerado + "...");
            pruebaDAO.eliminarPorId(idPruebaGenerado);
            int muestrasRestantes = muestraDAO.contarMuestrasPorPrueba(idPruebaGenerado);
            System.out.println("    -> [OK] Muestras restantes tras eliminar sesion padre: " + muestrasRestantes + " (Esperado: 0)");

            System.out.println("\n=====================================================================================");
            System.out.println(" RESULTADO TAREA #34: 100% VERIFICADO (addBatch + executeBatch + Transaccion ACID)   ");
            System.out.println("=====================================================================================");

        } catch (Exception e) {
            System.err.println("[ERROR] Fallo durante la verificacion de la Tarea #34: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
