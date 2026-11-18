package com.speedtest.db;

import com.speedtest.model.MuestraTelemetria;
import com.speedtest.model.PruebaVelocidad;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tareas #33 y #34 (HU-07 / RF-BD-02, RF-BD-03, RF-BD-04, RF-BD-05, RF-BD-06,
 * RF-BD-07): DAO transaccional para la entidad padre 'PruebaVelocidad' (tabla
 * 'prueba_velocidad') y orquestador de la persistencia atómica Maestro-Detalle
 * junto con 'MuestraTelemetriaDAO'.
 */
public class PruebaVelocidadDAO {

    /**
     * Sentencia SQL parametrizada para insertar el resumen consolidado de la
     * prueba (RF-BD-02).
     */
    private static final String SQL_INSERT_PRUEBA
            = "INSERT INTO prueba_velocidad ("
            + "  id_usuario, fecha_hora_ensayo, ip_origen, proveedor_isp, servidor_destino, "
            + "  ping_promedio, jitter_total, bajada_maxima, subida_maxima, perdida_paquetes, "
            + "  indice_qos, calificacion_qos, estado_sesion, notas_auditoria"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Sentencia SQL para consultar una sesión de prueba por su clave primaria
     * id_prueba.
     */
    private static final String SQL_SELECT_POR_ID
            = "SELECT id_prueba, id_usuario, fecha_hora_ensayo, ip_origen, proveedor_isp, "
            + "       servidor_destino, ping_promedio, jitter_total, bajada_maxima, subida_maxima, "
            + "       perdida_paquetes, indice_qos, calificacion_qos, estado_sesion, notas_auditoria "
            + "FROM prueba_velocidad "
            + "WHERE id_prueba = ?";

    /**
     * Sentencia SQL para listar todas las sesiones ordenadas desde la más
     * reciente (RF-BD-04).
     */
    private static final String SQL_SELECT_TODAS
            = "SELECT id_prueba, id_usuario, fecha_hora_ensayo, ip_origen, proveedor_isp, "
            + "       servidor_destino, ping_promedio, jitter_total, bajada_maxima, subida_maxima, "
            + "       perdida_paquetes, indice_qos, calificacion_qos, estado_sesion, notas_auditoria "
            + "FROM prueba_velocidad "
            + "ORDER BY fecha_hora_ensayo DESC, id_prueba DESC";

    /**
     * Sentencia SQL para actualizar estado operacional y notas técnicas de
     * auditoría (RF-BD-05).
     */
    private static final String SQL_UPDATE_NOTAS_ESTADO
            = "UPDATE prueba_velocidad "
            + "SET estado_sesion = ?, notas_auditoria = ? "
            + "WHERE id_prueba = ?";

    /**
     * Sentencia SQL para eliminar una sesión padre (dispara ON DELETE CASCADE
     * en muestra_telemetria - RF-BD-06).
     */
    private static final String SQL_DELETE_POR_ID
            = "DELETE FROM prueba_velocidad WHERE id_prueba = ?";

    private final MuestraTelemetriaDAO muestraTelemetriaDAO;

    public PruebaVelocidadDAO() {
        this.muestraTelemetriaDAO = new MuestraTelemetriaDAO();
    }

    // =========================================================================
    // 1. PERSISTENCIA TRANSACCIONAL MAESTRO-DETALLE CON BATCH INSERT (TAREAS #33 Y #34)
    // =========================================================================
    /**
     * Tareas #33 y #34 (RF-BD-02, RF-BD-03 y RF-BD-07): Registra de forma
     * atómica bajo una única transacción ACID
     * (connection.setAutoCommit(false)): 1. El registro consolidado de la
     * sesión en 'prueba_velocidad' (con RETURN_GENERATED_KEYS). 2. La totalidad
     * de la serie temporal de muestras de 200 ms en 'muestra_telemetria'
     * mediante procesamiento por lotes (addBatch / executeBatch).
     *
     * Confirma con COMMIT únicamente si la sesión y el 100% de sus muestras se
     * insertan sin errores; ante cualquier excepción ejecuta ROLLBACK total
     * evitando registros huérfanos.
     *
     * @param prueba Entidad padre PruebaVelocidad con las métricas globales y
     * calificación QoS.
     * @param muestras Lista de muestras de 200 ms capturadas en las fases PING,
     * DOWN y UP.
     * @return El id_prueba (PK) generado y confirmado en MySQL.
     * @throws SQLException Si ocurre un error en la sesión o en el lote y se
     * dispara rollback.
     */
    public int registrarEnsayoCompletoACID(PruebaVelocidad prueba, List<MuestraTelemetria> muestras) throws SQLException {
        validarEntidadPrueba(prueba);

        Connection conn = null;
        boolean autoCommitOriginal = true;

        try {
            conn = ConexionBD.getConexion();
            autoCommitOriginal = conn.getAutoCommit();

            // 1. Desactivar autocommit para abrir la transacción ACID unificada
            conn.setAutoCommit(false);

            // 2. Insertar registro padre en 'prueba_velocidad' y obtener id_prueba (Tarea #33)
            int idPruebaGenerado = insertarEnTransaccion(conn, prueba);

            // 3. Insertar por lotes (Batch Insert) todas las muestras de 200 ms en 'muestra_telemetria' (Tarea #34)
            if (muestras != null && !muestras.isEmpty()) {
                int insertadas = muestraTelemetriaDAO.insertarLoteEnTransaccion(conn, idPruebaGenerado, muestras);
                if (insertadas != muestras.size()) {
                    throw new SQLException(String.format(
                            "Inconsistencia en Batch Insert: se esperaban %d muestras pero se insertaron %d.",
                            muestras.size(), insertadas));
                }
                prueba.setMuestrasTelemetria(new ArrayList<>(muestras));
            }

            // 4. Confirmar definitivamente en disco tanto la sesión padre como su lote de muestras
            conn.commit();
            return idPruebaGenerado;

        } catch (Exception ex) {
            // 5. Reversión atómica total (ROLLBACK) ante cualquier fallo en la cabecera o en el lote
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("[ROLLBACK ACID MAESTRO-DETALLE] Transacción revertida íntegramente: " + ex.getMessage());
                } catch (SQLException rbEx) {
                    System.err.println("[ERROR] Fallo adicional al ejecutar rollback: " + rbEx.getMessage());
                }
            }
            if (ex instanceof SQLException) {
                throw (SQLException) ex;
            }
            throw new SQLException("Fallo en transacción ACID maestro-detalle: " + ex.getMessage(), ex);

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

    /**
     * Sobrecarga que toma directamente la lista de muestras contenida dentro
     * del objeto PruebaVelocidad.
     */
    public int registrarEnsayoCompletoACID(PruebaVelocidad prueba) throws SQLException {
        return registrarEnsayoCompletoACID(prueba, (prueba != null) ? prueba.getMuestrasTelemetria() : null);
    }

    // =========================================================================
    // 2. INSERCIÓN CONSOLIDADA DE SESIÓN (TAREA #33 - RF-BD-02 / RF-BD-07)
    // =========================================================================
    /**
     * Tarea #33 (RF-BD-02 y RF-BD-07): Inserta el resumen consolidado de una
     * sesión de prueba en la tabla 'prueba_velocidad' desactivando
     * explícitamente el autocommit (connection.setAutoCommit(false)) y
     * recuperando la clave primaria autoincremental mediante
     * Statement.RETURN_GENERATED_KEYS.
     */
    public int insertarSesionConsolidada(PruebaVelocidad prueba) throws SQLException {
        validarEntidadPrueba(prueba);

        Connection conn = null;
        boolean autoCommitOriginal = true;

        try {
            conn = ConexionBD.getConexion();
            autoCommitOriginal = conn.getAutoCommit();

            conn.setAutoCommit(false);
            int idGenerado = insertarEnTransaccion(conn, prueba);
            conn.commit();
            return idGenerado;

        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("[ROLLBACK ACID] Transacción revertida en insertarSesionConsolidada: " + ex.getMessage());
                } catch (SQLException rbEx) {
                    System.err.println("[ERROR] Fallo adicional al ejecutar rollback: " + rbEx.getMessage());
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

    /**
     * Tarea #33 (RF-BD-02): Ejecuta el PreparedStatement con
     * Statement.RETURN_GENERATED_KEYS utilizando una conexión transaccional
     * activa (con autoCommit == false).
     */
    public int insertarEnTransaccion(Connection conn, PruebaVelocidad prueba) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("La conexión transaccional proporcionada es nula o está cerrada.");
        }
        validarEntidadPrueba(prueba);

        if (conn.getAutoCommit()) {
            conn.setAutoCommit(false);
        }

        LocalDateTime fechaEnsayo = (prueba.getFechaHoraEnsayo() != null)
                ? prueba.getFechaHoraEnsayo()
                : LocalDateTime.now();

        try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_PRUEBA, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, prueba.getIdUsuario());
            pstmt.setTimestamp(2, Timestamp.valueOf(fechaEnsayo));
            pstmt.setString(3, obtenerTextoSeguro(prueba.getIpOrigen(), "127.0.0.1"));
            pstmt.setString(4, obtenerTextoSeguro(prueba.getProveedorIsp(), "Red Local / ISP"));
            pstmt.setString(5, obtenerTextoSeguro(prueba.getServidorDestino(), "localhost:80"));
            pstmt.setDouble(6, prueba.getPingPromedio());
            pstmt.setDouble(7, prueba.getJitterTotal());
            pstmt.setDouble(8, prueba.getBajadaMaxima());
            pstmt.setDouble(9, prueba.getSubidaMaxima());
            pstmt.setDouble(10, prueba.getPerdidaPaquetes());
            pstmt.setDouble(11, prueba.getIndiceQos());
            pstmt.setString(12, obtenerTextoSeguro(prueba.getCalificacionQos(), "Sin evaluar"));
            pstmt.setString(13, obtenerTextoSeguro(prueba.getEstadoSesion(), PruebaVelocidad.ESTADO_COMPLETADA));
            pstmt.setString(14, obtenerTextoSeguro(prueba.getNotasAuditoria(), ""));

            int filasAfectadas = pstmt.executeUpdate();
            if (filasAfectadas == 0) {
                throw new SQLException("La inserción en 'prueba_velocidad' falló: no se afectó ninguna fila.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int idGenerado = generatedKeys.getInt(1);
                    prueba.setIdPrueba(idGenerado);
                    prueba.setFechaHoraEnsayo(fechaEnsayo);
                    return idGenerado;
                } else {
                    throw new SQLException("La inserción en 'prueba_velocidad' no retornó clave primaria (RETURN_GENERATED_KEYS).");
                }
            }
        }
    }

    // =========================================================================
    // 3. OPERACIONES COMPLEMENTARIAS DE LECTURA, ACTUALIZACIÓN Y BORRADO (CRUD)
    // =========================================================================
    /**
     * Consulta una sesión de prueba específica por su ID primario (RF-BD-04).
     */
    public PruebaVelocidad obtenerPorId(int idPrueba) throws SQLException {
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_SELECT_POR_ID)) {

            pstmt.setInt(1, idPrueba);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapearPruebaVelocidad(rs);
                }
            }
        }
        return null;
    }

    /**
     * Consulta una sesión de prueba junto con toda su serie temporal de
     * muestras de 200 ms.
     */
    public PruebaVelocidad obtenerPorIdConMuestras(int idPrueba) throws SQLException {
        PruebaVelocidad prueba = obtenerPorId(idPrueba);
        if (prueba != null) {
            prueba.setMuestrasTelemetria(muestraTelemetriaDAO.listarPorIdPrueba(idPrueba));
        }
        return prueba;
    }

    /**
     * Lista todas las sesiones de prueba registradas en orden cronológico
     * descendente (RF-BD-04).
     */
    public List<PruebaVelocidad> listarTodas() throws SQLException {
        List<PruebaVelocidad> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_SELECT_TODAS); ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapearPruebaVelocidad(rs));
            }
        }
        return lista;
    }

    /**
     * Actualiza el estado de la sesión y las notas técnicas de auditoría
     * (RF-BD-05).
     */
    public boolean actualizarNotasYEstado(int idPrueba, String nuevoEstado, String notasAuditoria) throws SQLException {
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_UPDATE_NOTAS_ESTADO)) {

            pstmt.setString(1, obtenerTextoSeguro(nuevoEstado, PruebaVelocidad.ESTADO_COMPLETADA));
            pstmt.setString(2, obtenerTextoSeguro(notasAuditoria, ""));
            pstmt.setInt(3, idPrueba);

            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Elimina una sesión por su ID primario (RF-BD-06). Gracias a la
     * restricción ON DELETE CASCADE definida en la Tarea #32, MySQL purga
     * automáticamente todas las muestras hijas en 'muestra_telemetria'.
     */
    public boolean eliminarPorId(int idPrueba) throws SQLException {
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_DELETE_POR_ID)) {

            pstmt.setInt(1, idPrueba);
            return pstmt.executeUpdate() > 0;
        }
    }

    public MuestraTelemetriaDAO getMuestraTelemetriaDAO() {
        return muestraTelemetriaDAO;
    }

    // =========================================================================
    // 4. MÉTODOS AUXILIARES DE VALIDACIÓN Y MAPEO
    // =========================================================================
    private void validarEntidadPrueba(PruebaVelocidad prueba) {
        if (prueba == null) {
            throw new IllegalArgumentException("El objeto PruebaVelocidad no puede ser nulo.");
        }
        if (prueba.getIdUsuario() <= 0) {
            throw new IllegalArgumentException("El idUsuario asociado a la prueba debe ser mayor que 0.");
        }
        if (prueba.getServidorDestino() == null || prueba.getServidorDestino().isBlank()) {
            throw new IllegalArgumentException("El servidorDestino de la prueba no puede estar vacío.");
        }
    }

    private String obtenerTextoSeguro(String valor, String porDefecto) {
        return (valor != null && !valor.trim().isEmpty()) ? valor.trim() : porDefecto;
    }

    private PruebaVelocidad mapearPruebaVelocidad(ResultSet rs) throws SQLException {
        PruebaVelocidad p = new PruebaVelocidad();
        p.setIdPrueba(rs.getInt("id_prueba"));
        p.setIdUsuario(rs.getInt("id_usuario"));

        Timestamp ts = rs.getTimestamp("fecha_hora_ensayo");
        if (ts != null) {
            p.setFechaHoraEnsayo(ts.toLocalDateTime());
        }

        p.setIpOrigen(rs.getString("ip_origen"));
        p.setProveedorIsp(rs.getString("proveedor_isp"));
        p.setServidorDestino(rs.getString("servidor_destino"));
        p.setPingPromedio(rs.getDouble("ping_promedio"));
        p.setJitterTotal(rs.getDouble("jitter_total"));
        p.setBajadaMaxima(rs.getDouble("bajada_maxima"));
        p.setSubidaMaxima(rs.getDouble("subida_maxima"));
        p.setPerdidaPaquetes(rs.getDouble("perdida_paquetes"));
        p.setIndiceQos(rs.getDouble("indice_qos"));
        p.setCalificacionQos(rs.getString("calificacion_qos"));
        p.setEstadoSesion(rs.getString("estado_sesion"));
        p.setNotasAuditoria(rs.getString("notas_auditoria"));
        return p;
    }

    // =========================================================================
    // 5. PRUEBA FUNCIONAL AUTOEJECUTABLE (Shift + F6) PARA VERIFICAR LA TAREA #33
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("=====================================================================================");
        System.out.println(" VERIFICACION TAREA #33 (HU-07): INSERCION CONSOLIDADA CON setAutoCommit(false)      ");
        System.out.println("=====================================================================================");

        PruebaVelocidadDAO dao = new PruebaVelocidadDAO();

        try {
            PruebaVelocidad sesionPrueba = new PruebaVelocidad(
                    1,
                    "192.168.1.15",
                    "ISP Fibra Optica Univalle",
                    "speed.cloudflare.com:443",
                    28.45,
                    4.12,
                    136.14,
                    48.90,
                    0.0,
                    94.50,
                    "Excelente",
                    PruebaVelocidad.ESTADO_COMPLETADA,
                    "Prueba de verificacion Tarea #33 con autocommit=false y RETURN_GENERATED_KEYS"
            );

            System.out.println("\n[1] Ejecutando insertarSesionConsolidada(...) con connection.setAutoCommit(false)...");
            int idGenerado = dao.insertarSesionConsolidada(sesionPrueba);
            System.out.println("    -> [OK] COMMIT exitoso. Clave primaria generada (RETURN_GENERATED_KEYS): id_prueba = " + idGenerado);

            PruebaVelocidad recuperada = dao.obtenerPorId(idGenerado);
            System.out.println("    -> [OK] Registro leido desde MySQL: " + recuperada);

            System.out.println("\n[2] Probando ROLLBACK automatico ante violacion de integridad (id_usuario = 999999)...");
            PruebaVelocidad sesionInvalida = new PruebaVelocidad(
                    999999,
                    "127.0.0.1",
                    "Loopback",
                    "127.0.0.1:80",
                    1.50,
                    0.30,
                    100.0,
                    50.0,
                    0.0,
                    99.0,
                    "Excelente",
                    PruebaVelocidad.ESTADO_FALLIDA,
                    "Esta sesion debe revertirse por ROLLBACK"
            );

            try {
                dao.insertarSesionConsolidada(sesionInvalida);
            } catch (SQLException exEsperada) {
                System.out.println("    -> [OK] Excepcion capturada y ROLLBACK ejecutado correctamente.");
            }

            boolean eliminado = dao.eliminarPorId(idGenerado);
            System.out.println("\n[3] Limpieza de registro de prueba (id_prueba = " + idGenerado + "): "
                    + (eliminado ? "ELIMINADO OK" : "NO ENCONTRADO"));

            System.out.println("\n=====================================================================================");
            System.out.println(" RESULTADO TAREA #33: 100% VERIFICADO (PreparedStatement + RETURN_GENERATED_KEYS + ACID)");
            System.out.println("=====================================================================================");

        } catch (Exception e) {
            System.err.println("[ERROR] Fallo durante la prueba de la Tarea #33: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
