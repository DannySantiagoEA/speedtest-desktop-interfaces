package com.speedtest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidad que representa un nodo de destino (Target Host) para las pruebas de
 * red (HU-02 / RF-GUI-02). Soporta endpoints predefinidos en Internet (WAN),
 * servidores locales (LAN) y configuración manual.
 */
public class ServidorDestino {

    private String nombre;
    private String host;
    private int puerto;
    private String ubicacion;
    private String tipoEnlace; // "WAN / Internet" o "LAN / Local"
    private boolean editable;

    public ServidorDestino(String nombre, String host, int puerto, String ubicacion, String tipoEnlace, boolean editable) {
        this.nombre = nombre;
        this.host = host;
        this.puerto = puerto;
        this.ubicacion = ubicacion;
        this.tipoEnlace = tipoEnlace;
        this.editable = editable;
    }

    /**
     * Catálogo oficial de servidores predeterminados para SpeedTest Desktop
     * (RF-GUI-02).
     */
    public static List<ServidorDestino> obtenerCatalogoPredeterminado() {
        List<ServidorDestino> catalogo = new ArrayList<>();

        catalogo.add(new ServidorDestino(
                "Cloudflare CDN (SpeedTest)",
                "speed.cloudflare.com",
                443,
                "Anycast Global (Nodo Bogotá / Miami)",
                "WAN / Internet",
                false
        ));

        catalogo.add(new ServidorDestino(
                "Cloudflare Public DNS",
                "1.1.1.1",
                53,
                "Anycast Global (Baja Latencia)",
                "WAN / Internet",
                false
        ));

        catalogo.add(new ServidorDestino(
                "Google Public DNS",
                "8.8.8.8",
                53,
                "Estados Unidos (Google LLC)",
                "WAN / Internet",
                false
        ));

        catalogo.add(new ServidorDestino(
                "Servidor Local XAMPP (Localhost)",
                "127.0.0.1",
                80,
                "Loopback Local (Laboratorio EIEE)",
                "LAN / Local",
                false
        ));

        catalogo.add(new ServidorDestino(
                "Personalizado / Manual (IP o Dominio)",
                "192.168.1.1",
                80,
                "Definido manualmente por el operador",
                "LAN / WAN Configurable",
                true
        ));

        return catalogo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPuerto() {
        return puerto;
    }

    public void setPuerto(int puerto) {
        this.puerto = puerto;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getTipoEnlace() {
        return tipoEnlace;
    }

    public void setTipoEnlace(String tipoEnlace) {
        this.tipoEnlace = tipoEnlace;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    @Override
    public String toString() {
        return nombre + " [" + host + ":" + puerto + "]";
    }
}
