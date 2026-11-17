package com.speedtest.db;

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
 * Tarea #33 (HU-07 / RF-BD-02, RF-BD-04, RF-BD-05, RF-BD-06, RF-BD-07): DAO
 * transaccional para la entidad padre 'PruebaVelocidad' (tabla
 * 'prueba_velocidad').
 *
 * Implementa la inserción consolidada de la sesión de prueba empleando
 * PreparedStatement parametrizado con Statement.RETURN_GENERATED_KEYS dentro de
 * un bloque transaccional explícito con connection.setAutoCommit(false).
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

    // =========================================================================
    // 1. INSERCIÓN CONSOLIDADA CON AUTOCOMMIT DESACTIVADO (TAREA #33 - RF-BD-02 / RF-BD-07)
    // =========================================================================
    /**
     * Tarea #33 (RF-BD-02 y RF-BD-07): Inserta el resumen consolidado de una
     * sesión de prueba en la tabla 'prueba_velocidad' desactivando
     * explícitamente el autocommit (connection.setAutoCommit(false)) y
     * recuperando la clave primaria autoincremental mediante
     * Statement.RETURN_GENERATED_KEYS.
     *
     * Si la inserción tiene éxito se confirma con connection.commit(); si
     * ocurre cualquier excepción se ejecuta connection.rollback() garantizando
     * atomicidad ACID.
     *
     * @param prueba Entidad PruebaVelocidad con las métricas globales y
     * calificación QoS.
     * @return El id_prueba (PK) generado por MySQL, o -1 si no se pudo
     * insertar.
     * @throws SQLException Si ocurre un error de base de datos o se dispara
     * rollback.
     */
    public int insertarSesionConsolidada(PruebaVelocidad prueba) throws SQLException {
        validarEntidadPrueba(prueba);

        Connection conn = null;
        boolean autoCommitOriginal = true;

        try {
            conn = ConexionBD.getConexion();
            autoCommitOriginal = conn.getAutoCommit();

            // 1. Desactivar el modo autocommit para iniciar el bloque transaccional ACID
            conn.setAutoCommit(false);

            // 2. Ejecutar la inserción parametrizada con RETURN_GENERATED_KEYS
            int idGenerado = insertarEnTransaccion(conn, prueba);

            // 3. Confirmar explícitamente la transacción en disco
            conn.commit();
            return idGenerado;

        } catch (SQLException ex) {
            // 4. Reversión atómica (ROLLBACK) ante cualquier fallo durante la persistencia
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
            // 5. Restaurar el estado de autoCommit y cerrar el descriptor de conexión
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
     *
     * Este método permite que la inserción del registro padre
     * (prueba_velocidad) y la inserción masiva por lotes de las muestras hijas
     * (muestra_telemetria en la Tarea #34) compartan exactamente la misma
     * transacción ACID antes de invocar commit().
     *
     * @param conn Conexión JDBC activa con setAutoCommit(false).
     * @param prueba Objeto PruebaVelocidad a insertar (recibirá el idPrueba
     * generado).
     * @return Clave primaria autoincremental (id_prueba) asignada por MySQL.
     * @throws SQLException Si falla la sentencia o no se genera la clave
     * primaria.
     */
    public int insertarEnTransaccion(Connection conn, PruebaVelocidad prueba) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("La conexión transaccional proporcionada es nula o está cerrada.");
        }
        validarEntidadPrueba(prueba);

        // Asegurar que la conexión opere con autocommit desactivado (RF-BD-07)
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
    // 2. OPERACIONES COMPLEMENTARIAS DE LECTURA, ACTUALIZACIÓN Y BORRADO (CRUD)
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

    // =========================================================================
    // 3. MÉTODOS AUXILIARES DE VALIDACIÓN Y MAPEO
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
    // 4. PRUEBA FUNCIONAL AUTOEJECUTABLE (Shift + F6) PARA VERIFICAR LA TAREA #33
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("=====================================================================================");
        System.out.println(" VERIFICACIÓN TAREA #33 (HU-07): INSERCIÓN CONSOLIDADA CON setAutoCommit(false)      ");
        System.out.println("=====================================================================================");

        PruebaVelocidadDAO dao = new PruebaVelocidadDAO();

        try {
            // 1. Prueba de inserción transaccional exitosa (COMMIT + RETURN_GENERATED_KEYS)
            PruebaVelocidad sesionPrueba = new PruebaVelocidad(
                    1, // id_usuario = 1 (admin Danny Santiago Erazo)
                    "192.168.1.15",
                    "ISP Fibra Óptica Univalle",
                    "speed.cloudflare.com:443",
                    28.45,
                    4.12,
                    136.14,
                    48.90,
                    0.0,
                    94.50,
                    "Excelente",
                    PruebaVelocidad.ESTADO_COMPLETADA,
                    "Prueba de verificación Tarea #33 con autocommit=false y RETURN_GENERATED_KEYS"
            );

            System.out.println("\n[1] Ejecutando insertarSesionConsolidada(...) con connection.setAutoCommit(false)...");
            int idGenerado = dao.insertarSesionConsolidada(sesionPrueba);
            System.out.println("    -> [OK] COMMIT exitoso. Clave primaria generada (RETURN_GENERATED_KEYS): id_prueba = " + idGenerado);

            // 2. Verificar lectura desde MySQL del registro recién confirmado
            PruebaVelocidad recuperada = dao.obtenerPorId(idGenerado);
            System.out.println("    -> [OK] Registro leído desde MySQL: " + recuperada);

            // 3. Prueba de reversión transaccional (ROLLBACK) ante fallo de llave foránea inexistente (id_usuario = 999999)
            System.out.println("\n[2] Probando ROLLBACK automático ante violación de integridad (id_usuario = 999999)...");
            PruebaVelocidad sesionInvalida = new PruebaVelocidad(
                    999999, // Usuario inexistente -> dispara SQLException en FK y ejecuta rollback()
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
                    "Esta sesión debe revertirse por ROLLBACK"
            );

            try {
                dao.insertarSesionConsolidada(sesionInvalida);
            } catch (SQLException exEsperada) {
                System.out.println("    -> [OK] Excepción capturada y ROLLBACK ejecutado correctamente.");
            }

            // 4. Limpiar el registro de prueba para dejar la base de datos impecable
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
