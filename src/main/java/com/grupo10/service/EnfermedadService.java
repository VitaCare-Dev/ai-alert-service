package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.Enfermedad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Servicio para consultar enfermedades crónicas de los pacientes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EnfermedadService {

    private static final Logger logger = Logger.getLogger(EnfermedadService.class.getName());

    /**
     * Obtiene todas las enfermedades asociadas a un paciente.
     * 
     * @param idPaciente ID del paciente
     * @return Lista de enfermedades del paciente
     */
    public static List<Enfermedad> obtenerEnfermedadesPorPaciente(Long idPaciente) {
        String sql = """
                SELECT e.id_enfermedad, e.nombre_enfermedad, e.descripcion
                FROM tb_enfermedad e
                INNER JOIN tb_paciente_enfermedad pe ON e.id_enfermedad = pe.id_enfermedad
                WHERE pe.id_paciente = ?
                ORDER BY e.nombre_enfermedad
                """;

        List<Enfermedad> enfermedades = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    enfermedades.add(Enfermedad.builder()
                            .idEnfermedad(rs.getLong("id_enfermedad"))
                            .nombreEnfermedad(rs.getString("nombre_enfermedad"))
                            .descripcion(rs.getString("descripcion"))
                            .build());
                }
            }

            logger.info("Se encontraron " + enfermedades.size() + " enfermedades para el paciente ID: " + idPaciente);
            return enfermedades;

        } catch (Exception e) {
            logger.severe("Error al obtener enfermedades del paciente: " + e.getMessage());
            throw new RuntimeException("Error al consultar enfermedades", e);
        }
    }

    /**
     * Obtiene todos los pacientes que tienen al menos una enfermedad registrada.
     * 
     * @return Lista de IDs de pacientes con enfermedades
     */
    public static List<Long> obtenerPacientesConEnfermedades() {
        String sql = """
                SELECT DISTINCT id_paciente
                FROM tb_paciente_enfermedad
                ORDER BY id_paciente
                """;

        List<Long> pacientes = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pacientes.add(rs.getLong("id_paciente"));
                }
            }

            logger.info("Se encontraron " + pacientes.size() + " pacientes con enfermedades registradas");
            return pacientes;

        } catch (Exception e) {
            logger.severe("Error al obtener pacientes con enfermedades: " + e.getMessage());
            throw new RuntimeException("Error al consultar pacientes", e);
        }
    }
}
