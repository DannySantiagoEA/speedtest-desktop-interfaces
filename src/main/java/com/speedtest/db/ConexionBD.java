package com.speedtest.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gestor centralizado de conectividad relacional JDBC para MySQL/MariaDB.
 * Cumple con el requerimiento RF-BD-01 para la HU-01 del proyecto SpeedTest
 * Desktop.
 */
public class ConexionBD {

    private static String url;
    private static String user;
    private static String password;
    private static String driver;

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
                // Valores de respaldo si no encuentra el archivo
                url = "jdbc:mysql://localhost:3306/speedtest_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                user = "root";
                password = "";
                driver = "com.mysql.cj.jdbc.Driver";
            } else {
                props.load(input);
                url = props.getProperty("db.url");
                user = props.getProperty("db.user");
                password = props.getProperty("db.password", "");
                driver = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
            }

            // Registrar el driver JDBC en la máquina virtual de Java (JVM)
            Class.forName(driver);

        } catch (IOException e) {
            System.err.println("[ERROR] Error al leer el archivo db.properties: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] No se encontró el driver MySQL Connector/J: " + e.getMessage());
        }
    }

    /**
     * Obtiene una conexión física activa con la base de datos MySQL.
     *
     * @return Connection objeto de conexión JDBC listo para ejecutar consultas.
     * @throws SQLException si las credenciales fallan o el servidor MySQL está
     * apagado.
     */
    public static Connection getConexion() throws SQLException {
        return DriverManager.getConnection(url, user, password);
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
}
