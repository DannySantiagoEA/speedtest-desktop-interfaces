package com.speedtest.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Gestor centralizado de conectividad relacional JDBC para MySQL/MariaDB.
 * Cumple con los requerimientos RF-BD-01, RF-BD-02, RF-BD-03 y RF-BD-06 (HU-01
 * y HU-07).
 */
public class ConexionBD {

    private static String url;
    private static String user;
    private static String password;
    private static String driver;
    private static boolean esquemaVerificado = false;

    // Bloque estático: se ejecuta una sola vez al cargar la clase en memoria
    static {
        cargarConfiguracion();
    }

    /**
     * Carga los parámetros de conexión desde src/main/resources/db.properties
     */
    private static void cargarConfiguracion() {
        Properties props = new Properties();
        try (InputStream input = ConexionBD.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (input == null) {
                System.err.println("[WARN] No se encontró db.properties en classpath. Usando valores por defecto de XAMPP.");
                url = "jdbc:mysql://localhost:3306/speedtest_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&rewriteBatchedStatements=true";
                user = "root";
                password = "";
                driver = "com.mysql.cj.jdbc.Driver";
            } else {
                props.load(input);
                url = props.getProperty("db.url");
                // Asegurar el parámetro rewriteBatchedStatements=true para maximizar el Batch Insert (HU-07)
                if (url != null && !url.contains("rewriteBatchedStatements")) {
                    url += (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
                }
                user = props.getProperty("db.user");
                password = props.getProperty("db.password", "");
                driver = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
            }

            Class.forName(driver);

        } catch (IOException e) {
            System.err.println("[ERROR] Error al leer el archivo db.properties: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] No se encontró el driver MySQL Connector/J: " + e.getMessage());
        }
    }

    /**
     * Obtiene una conexión física activa con la base de datos MySQL. En la
     * primera conexión exitosa verifica que el esquema DDL de la HU-07 esté
     * completo.
     *
     * @return Connection objeto de conexión JDBC listo para ejecutar consultas.
     * @throws SQLException si las credenciales fallan o el servidor MySQL está
     * apagado.
     */
    public static Connection getConexion() throws SQLException {
        Connection conn = DriverManager.getConnection(url, user, password);
        if (!esquemaVerificado) {
            esquemaVerificado = true;
            asegurarEsquemaRelacionalHU07(conn);
        }
        return conn;
    }

    /**
     * Alias de compatibilidad para obtener una conexión activa.
     */
    public static Connection obtenerConexion() throws SQLException {
        return getConexion();
    }

    /**
     * Comprueba si el servicio MySQL está activo y responde correctamente.
     *
     * @return true si la conexión fue exitosa; false si ocurrió algún fallo.
     */
    public static boolean probarConexion() {
        try (Connection conn = getConexion()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[ERROR] Fallo de enlace con MySQL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tarea #32 (HU-07): Verifica y despliega automáticamente las tablas
     * 'usuario', 'prueba_velocidad' y 'muestra_telemetria' con sus columnas
     * completas, índices y restricción FOREIGN KEY ... ON DELETE CASCADE en
     * caso de que falten columnas del esquema preliminar.
     */
    public static synchronized void asegurarEsquemaRelacionalHU07(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // 1. Asegurar tabla usuario
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS usuario ("
                    + "  id_usuario INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  nombre_operador VARCHAR(100) NOT NULL,"
                    + "  username VARCHAR(50) NOT NULL UNIQUE,"
                    + "  password_hash VARCHAR(255) NOT NULL,"
                    + "  rol VARCHAR(30) NOT NULL DEFAULT 'OPERADOR',"
                    + "  fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // 2. Asegurar tabla padre prueba_velocidad
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS prueba_velocidad ("
                    + "  id_prueba INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  id_usuario INT NOT NULL,"
                    + "  fecha_hora_ensayo TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "  ip_origen VARCHAR(45) NOT NULL DEFAULT '127.0.0.1',"
                    + "  proveedor_isp VARCHAR(100) NOT NULL DEFAULT 'Red Local / ISP',"
                    + "  servidor_destino VARCHAR(150) NOT NULL,"
                    + "  ping_promedio DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  jitter_total DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  bajada_maxima DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  subida_maxima DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  perdida_paquetes DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  indice_qos DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  calificacion_qos VARCHAR(20) NOT NULL,"
                    + "  estado_sesion VARCHAR(20) NOT NULL DEFAULT 'Completada',"
                    + "  notas_auditoria TEXT,"
                    + "  INDEX idx_prueba_usuario (id_usuario),"
                    + "  INDEX idx_prueba_fecha (fecha_hora_ensayo),"
                    + "  INDEX idx_prueba_qos (calificacion_qos),"
                    + "  CONSTRAINT fk_prueba_usuario FOREIGN KEY (id_usuario) "
                    + "    REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Si la tabla prueba_velocidad venía del Sprint 1 sin la columna indice_qos, se añade
            if (!existeColumna(conn, "prueba_velocidad", "indice_qos")) {
                stmt.executeUpdate("ALTER TABLE prueba_velocidad ADD COLUMN indice_qos DOUBLE NOT NULL DEFAULT 0.0 AFTER perdida_paquetes");
            }

            // 3. Asegurar tabla hija muestra_telemetria con todas las columnas de MuestraTelemetria.java
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS muestra_telemetria ("
                    + "  id_muestra BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "  id_prueba INT NOT NULL,"
                    + "  numero_secuencia INT NOT NULL DEFAULT 1,"
                    + "  timestamp BIGINT NOT NULL,"
                    + "  tiempo_transcurrido_seg DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  etapa VARCHAR(10) NOT NULL,"
                    + "  bytes_transferidos BIGINT NOT NULL DEFAULT 0,"
                    + "  velocidad_inst DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  latencia_inst DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  jitter_inst DOUBLE NOT NULL DEFAULT 0.0,"
                    + "  paquete_perdido TINYINT(1) NOT NULL DEFAULT 0,"
                    + "  INDEX idx_muestra_prueba_etapa_seq (id_prueba, etapa, numero_secuencia),"
                    + "  CONSTRAINT fk_muestra_prueba FOREIGN KEY (id_prueba) "
                    + "    REFERENCES prueba_velocidad(id_prueba) ON DELETE CASCADE ON UPDATE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Si muestra_telemetria venía del Sprint 1 sin las columnas detalladas de serie temporal, las agregamos
            if (!existeColumna(conn, "muestra_telemetria", "numero_secuencia")) {
                stmt.executeUpdate("ALTER TABLE muestra_telemetria ADD COLUMN numero_secuencia INT NOT NULL DEFAULT 1 AFTER id_prueba");
            }
            if (!existeColumna(conn, "muestra_telemetria", "tiempo_transcurrido_seg")) {
                stmt.executeUpdate("ALTER TABLE muestra_telemetria ADD COLUMN tiempo_transcurrido_seg DOUBLE NOT NULL DEFAULT 0.0 AFTER timestamp");
            }
            if (!existeColumna(conn, "muestra_telemetria", "jitter_inst")) {
                stmt.executeUpdate("ALTER TABLE muestra_telemetria ADD COLUMN jitter_inst DOUBLE NOT NULL DEFAULT 0.0 AFTER latencia_inst");
            }
            if (!existeColumna(conn, "muestra_telemetria", "paquete_perdido")) {
                stmt.executeUpdate("ALTER TABLE muestra_telemetria ADD COLUMN paquete_perdido TINYINT(1) NOT NULL DEFAULT 0 AFTER jitter_inst");
            }

        } catch (SQLException ex) {
            System.err.println("[WARN] Aviso durante verificación del esquema DDL HU-07: " + ex.getMessage());
        }
    }

    private static boolean existeColumna(Connection conn, String tabla, String columna) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tabla, columna)) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Tarea #32 (HU-07): Consulta en el diccionario de datos de MySQL
     * (information_schema) la regla de integridad referencial de
     * 'fk_muestra_prueba' para certificar que DELETE_RULE = 'CASCADE'.
     */
    public static String verificarIntegridadReferencialDDL() {
        String sql = "SELECT rc.CONSTRAINT_NAME, rc.TABLE_NAME, rc.REFERENCED_TABLE_NAME, "
                + "       rc.DELETE_RULE, rc.UPDATE_RULE "
                + "FROM information_schema.REFERENTIAL_CONSTRAINTS rc "
                + "WHERE rc.CONSTRAINT_SCHEMA = DATABASE() "
                + "  AND rc.TABLE_NAME IN ('prueba_velocidad', 'muestra_telemetria') "
                + "ORDER BY rc.TABLE_NAME";

        StringBuilder reporte = new StringBuilder();
        try (Connection conn = getConexion(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                reporte.append(String.format(
                        "Constraint: %-20s | Hija: %-20s -> Padre: %-18s | ON DELETE: %-8s | ON UPDATE: %-8s%n",
                        rs.getString("CONSTRAINT_NAME"),
                        rs.getString("TABLE_NAME"),
                        rs.getString("REFERENCED_TABLE_NAME"),
                        rs.getString("DELETE_RULE"),
                        rs.getString("UPDATE_RULE")
                ));
            }
        } catch (SQLException ex) {
            return "[ERROR] No se pudo verificar information_schema: " + ex.getMessage();
        }
        return reporte.toString().trim();
    }
}
