package com.speedtest.model;

/**
 * Entidad de dominio que representa al operador técnico del sistema (HU-01).
 */
public class Usuario {

    private int idUsuario;
    private String nombreOperador;
    private String username;
    private String rol;

    public Usuario() {
    }

    public Usuario(int idUsuario, String nombreOperador, String username, String rol) {
        this.idUsuario = idUsuario;
        this.nombreOperador = nombreOperador;
        this.username = username;
        this.rol = rol;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreOperador() {
        return nombreOperador;
    }

    public void setNombreOperador(String nombreOperador) {
        this.nombreOperador = nombreOperador;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    @Override
    public String toString() {
        return nombreOperador + " (" + rol + ")";
    }
}
