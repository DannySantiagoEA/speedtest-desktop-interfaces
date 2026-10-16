package com.speedtest.db;

import com.speedtest.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO para la gestión y autenticación de operadores contra la tabla 'usuario'
 * (RF-BD-01). Emplea sentencias preparadas (PreparedStatement) para prevenir
 * inyecciones SQL.
 */
public class UsuarioDAO {

    private static final String SQL_AUTENTICAR
            = "SELECT id_usuario, nombre_operador, username, rol "
            + "FROM usuario "
            + "WHERE username = ? AND password_hash = ? "
            + "LIMIT 1";

    /**
     * Valida las credenciales del operador contra MySQL.
     *
     * @param username Nombre de usuario ingresado.
     * @param password Contraseña ingresada.
     * @return Objeto Usuario si las credenciales son válidas; null si no
     * coinciden.
     * @throws SQLException Si ocurre un fallo de conexión con el servidor
     * MySQL.
     */
    public Usuario autenticar(String username, String password) throws SQLException {
        try (Connection conn = ConexionBD.getConexion(); PreparedStatement pstmt = conn.prepareStatement(SQL_AUTENTICAR)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Usuario(
                            rs.getInt("id_usuario"),
                            rs.getString("nombre_operador"),
                            rs.getString("username"),
                            rs.getString("rol")
                    );
                }
            }
        }
        return null; // Credenciales inválidas
    }
}
