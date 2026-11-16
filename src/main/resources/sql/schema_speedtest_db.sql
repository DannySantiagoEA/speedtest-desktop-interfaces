-- =============================================================================
-- PROYECTO FINAL INTERFACES: SPEEDTEST DESKTOP (EIEE - UNIVERSIDAD DEL VALLE)
-- Autor: Danny Santiago Erazo Agredo (Cód. 202180011)
-- Tarea #32 (HU-07): Esquema Relacional DDL con Integridad Referencial y
--                    Borrado en Cascada (ON DELETE CASCADE) bajo motor InnoDB.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS speedtest_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE speedtest_db;

-- -----------------------------------------------------------------------------
-- 1. TABLA PADRE DE OPERADORES: usuario (HU-01 / RF-GUI-01, RF-BD-01)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nombre_operador VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(30) NOT NULL DEFAULT 'OPERADOR',
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Semilla del operador administrador por defecto
INSERT INTO usuario (nombre_operador, username, password_hash, rol)
VALUES ('Danny Santiago Erazo', 'admin', 'admin123', 'ADMINISTRADOR')
ON DUPLICATE KEY UPDATE
    nombre_operador = VALUES(nombre_operador),
    rol = VALUES(rol);

-- -----------------------------------------------------------------------------
-- Limpieza previa de tablas de telemetría para asegurar la estructura completa
-- de la HU-07 (se elimina primero la tabla hija por integridad referencial)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS muestra_telemetria;
DROP TABLE IF EXISTS prueba_velocidad;

-- -----------------------------------------------------------------------------
-- 2. TABLA PADRE DE SESIONES: prueba_velocidad (HU-07 / RF-BD-02, RF-BD-04 a 07)
-- Almacena las métricas agregadas y la clasificación global de QoS (SLA)
-- -----------------------------------------------------------------------------
CREATE TABLE prueba_velocidad (
    id_prueba INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    fecha_hora_ensayo TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_origen VARCHAR(45) NOT NULL DEFAULT '127.0.0.1',
    proveedor_isp VARCHAR(100) NOT NULL DEFAULT 'Red Local / ISP',
    servidor_destino VARCHAR(150) NOT NULL,
    ping_promedio DOUBLE NOT NULL DEFAULT 0.0,
    jitter_total DOUBLE NOT NULL DEFAULT 0.0,
    bajada_maxima DOUBLE NOT NULL DEFAULT 0.0,
    subida_maxima DOUBLE NOT NULL DEFAULT 0.0,
    perdida_paquetes DOUBLE NOT NULL DEFAULT 0.0,
    indice_qos DOUBLE NOT NULL DEFAULT 0.0,
    calificacion_qos VARCHAR(20) NOT NULL,
    estado_sesion VARCHAR(20) NOT NULL DEFAULT 'Completada',
    notas_auditoria TEXT,

    -- Índices para acelerar las consultas filtradas por fecha, servidor y QoS (HU-08)
    INDEX idx_prueba_usuario (id_usuario),
    INDEX idx_prueba_fecha (fecha_hora_ensayo),
    INDEX idx_prueba_qos (calificacion_qos),

    -- Restricción de integridad referencial hacia la tabla usuario
    CONSTRAINT fk_prueba_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. TABLA HIJA DE SERIE TEMPORAL: muestra_telemetria (HU-07 / RF-BD-03, RF-BD-06)
-- Almacena las muestras granulares capturadas cada Δt = 200 ms (PING, DOWN, UP)
-- -----------------------------------------------------------------------------
CREATE TABLE muestra_telemetria (
    id_muestra BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_prueba INT NOT NULL,
    numero_secuencia INT NOT NULL DEFAULT 1,
    timestamp BIGINT NOT NULL,
    tiempo_transcurrido_seg DOUBLE NOT NULL DEFAULT 0.0,
    etapa VARCHAR(10) NOT NULL,               -- 'PING', 'DOWN', 'UP'
    bytes_transferidos BIGINT NOT NULL DEFAULT 0,
    velocidad_inst DOUBLE NOT NULL DEFAULT 0.0,
    latencia_inst DOUBLE NOT NULL DEFAULT 0.0,
    jitter_inst DOUBLE NOT NULL DEFAULT 0.0,
    paquete_perdido TINYINT(1) NOT NULL DEFAULT 0,

    -- Índices compuestos para reconstrucción rápida de curvas temporales (HU-08)
    INDEX idx_muestra_prueba_etapa_seq (id_prueba, etapa, numero_secuencia),

    -- Restricción de clave foránea con BORRADO EN CASCADA (ON DELETE CASCADE)
    CONSTRAINT fk_muestra_prueba
        FOREIGN KEY (id_prueba)
        REFERENCES prueba_velocidad (id_prueba)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;