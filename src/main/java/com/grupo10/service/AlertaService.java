package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.AlertaIA;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.logging.Logger;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Servicio para gestionar alertas de IA generadas para los pacientes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AlertaService {

    private static final Logger logger = Logger.getLogger(AlertaService.class.getName());

    /**
     * Guarda una nueva alerta de IA en la base de datos.
     * 
     * @param alerta Alerta a guardar
     * @return ID de la alerta generada
     */
    public static Long guardarAlerta(AlertaIA alerta) {
        String sql = """
            INSERT INTO tb_alerta_ia (id_paciente, fecha_disparo, motivo_alerta, recomendacion_ia, leida)
            VALUES (?, ?, ?, ?, 0)
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, new String[] { "id_alerta_ia" })) {

            stmt.setLong(1, alerta.getIdPaciente());
            stmt.setTimestamp(2, Timestamp.valueOf(alerta.getFechaDisparo()));
            stmt.setString(3, alerta.getMotivoAlerta());
            stmt.setString(4, alerta.getRecomendacionIa());

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        Long idGenerado = rs.getLong(1);
                        logger.info("Alerta guardada exitosamente con ID: " + idGenerado);
                        return idGenerado;
                    }
                }
            }

            throw new RuntimeException("No se pudo obtener el ID de la alerta generada");

        } catch (Exception e) {
            logger.severe("Error al guardar alerta: " + e.getMessage());
            throw new RuntimeException("Error al guardar alerta en la base de datos", e);
        }
    }

    /**
     * Verifica si ya existe una alerta similar reciente para evitar duplicados.
     * 
     * @param idPaciente ID del paciente
     * @param horasAtras Horas hacia atrás para buscar alertas similares
     * @return true si existe una alerta reciente, false en caso contrario
     */
    public static boolean existeAlertaReciente(Long idPaciente, int horasAtras) {
        String sql = """
            SELECT COUNT(*) as total
            FROM tb_alerta_ia
            WHERE id_paciente = ?
              AND fecha_disparo >= SYSTIMESTAMP - NUMTODSINTERVAL(?, 'HOUR')
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, idPaciente);
            stmt.setInt(2, horasAtras);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    return total > 0;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.severe("Error al verificar alertas recientes: " + e.getMessage());
            return false;
        }
    }
}
