package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.UmbralMedico;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Logger;

/**
 * Servicio para gestionar los umbrales médicos personalizados de los pacientes.
 */
public class UmbralService {

    private static final Logger logger = Logger.getLogger(UmbralService.class.getName());

    /**
     * Obtiene los umbrales médicos configurados para un paciente.
     * 
     * @param idPaciente ID del paciente
     * @return UmbralMedico con los valores configurados, o null si no existe
     */
    public static UmbralMedico obtenerUmbralesPorPaciente(Long idPaciente) {
        String sql = """
                SELECT id_umbral, id_paciente, glucosa_max, glucosa_min,
                       sistolica_max, diastolica_max, temperatura_max
                FROM tb_umbral_medico
                WHERE id_paciente = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UmbralMedico.builder()
                            .idUmbral(rs.getLong("id_umbral"))
                            .idPaciente(rs.getLong("id_paciente"))
                            .glucosaMax(rs.getObject("glucosa_max", Integer.class))
                            .glucosaMin(rs.getObject("glucosa_min", Integer.class))
                            .sistolicaMax(rs.getObject("sistolica_max", Integer.class))
                            .diastolicaMax(rs.getObject("diastolica_max", Integer.class))
                            .temperaturaMax(rs.getBigDecimal("temperatura_max"))
                            .build();
                }
            }

            logger.warning("No se encontraron umbrales para el paciente ID: " + idPaciente);
            return null;

        } catch (Exception e) {
            logger.severe("Error al obtener umbrales del paciente: " + e.getMessage());
            throw new RuntimeException("Error al consultar umbrales médicos", e);
        }
    }
}
