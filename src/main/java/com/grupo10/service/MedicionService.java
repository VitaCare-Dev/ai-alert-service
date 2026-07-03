package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.MedicionGlucosa;
import com.grupo10.model.MedicionVitales;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Servicio para consultar mediciones de salud de los pacientes.
 */
public class MedicionService {
    
    private static final Logger logger = Logger.getLogger(MedicionService.class.getName());

    /**
     * Obtiene las mediciones de glucosa de los últimos N días para un paciente.
     * 
     * @param idPaciente ID del paciente
     * @param dias Número de días hacia atrás
     * @return Lista de mediciones de glucosa
     */
    public static List<MedicionGlucosa> obtenerMedicionesGlucosaRecientes(Long idPaciente, int dias) {
        String sql = """
            SELECT mg.id_control, mg.glucosa, mg.periodo, cs.fecha_hora
            FROM tb_medicion_glucosa mg
            INNER JOIN tb_control_salud cs ON mg.id_control = cs.id_control
            WHERE cs.id_paciente = ?
              AND cs.fecha_hora >= SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY')
            ORDER BY cs.fecha_hora DESC
            """;

        List<MedicionGlucosa> mediciones = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, idPaciente);
            stmt.setInt(2, dias);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    mediciones.add(MedicionGlucosa.builder()
                        .idControl(rs.getLong("id_control"))
                        .glucosa(rs.getInt("glucosa"))
                        .periodo(rs.getString("periodo"))
                        .build());
                }
            }
            
            logger.info("Se encontraron " + mediciones.size() + " mediciones de glucosa para el paciente ID: " + idPaciente);
            return mediciones;
            
        } catch (Exception e) {
            logger.severe("Error al obtener mediciones de glucosa: " + e.getMessage());
            throw new RuntimeException("Error al consultar mediciones de glucosa", e);
        }
    }

    /**
     * Obtiene las mediciones de signos vitales de los últimos N días para un paciente.
     * 
     * @param idPaciente ID del paciente
     * @param dias Número de días hacia atrás
     * @return Lista de mediciones vitales
     */
    public static List<MedicionVitales> obtenerMedicionesVitalesRecientes(Long idPaciente, int dias) {
        String sql = """
            SELECT mv.id_control, mv.presion_sistolica, mv.presion_diastolica, 
                   mv.temperatura, mv.peso, cs.fecha_hora
            FROM tb_medicion_vitales mv
            INNER JOIN tb_control_salud cs ON mv.id_control = cs.id_control
            WHERE cs.id_paciente = ?
              AND cs.fecha_hora >= SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY')
            ORDER BY cs.fecha_hora DESC
            """;

        List<MedicionVitales> mediciones = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, idPaciente);
            stmt.setInt(2, dias);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    mediciones.add(MedicionVitales.builder()
                        .idControl(rs.getLong("id_control"))
                        .presionSistolica(rs.getObject("presion_sistolica", Integer.class))
                        .presionDiastolica(rs.getObject("presion_diastolica", Integer.class))
                        .temperatura(rs.getBigDecimal("temperatura"))
                        .peso(rs.getBigDecimal("peso"))
                        .build());
                }
            }
            
            logger.info("Se encontraron " + mediciones.size() + " mediciones vitales para el paciente ID: " + idPaciente);
            return mediciones;
            
        } catch (Exception e) {
            logger.severe("Error al obtener mediciones vitales: " + e.getMessage());
            throw new RuntimeException("Error al consultar mediciones vitales", e);
        }
    }

    /**
     * Verifica si existen mediciones en días consecutivos.
     * 
     * @param idPaciente ID del paciente
     * @param diasConsecutivos Número de días consecutivos a verificar
     * @return Lista de fechas en las que hubo mediciones consecutivas
     */
    public static List<LocalDateTime> verificarMedicionesConsecutivas(Long idPaciente, int diasConsecutivos) {
        String sql = """
            SELECT DISTINCT TRUNC(cs.fecha_hora) as fecha
            FROM tb_control_salud cs
            WHERE cs.id_paciente = ?
              AND cs.fecha_hora >= SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY')
            ORDER BY fecha DESC
            """;

        List<LocalDateTime> fechas = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, idPaciente);
            stmt.setInt(2, diasConsecutivos + 1);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("fecha");
                    if (ts != null) {
                        fechas.add(ts.toLocalDateTime());
                    }
                }
            }
            
            return fechas;
            
        } catch (Exception e) {
            logger.severe("Error al verificar mediciones consecutivas: " + e.getMessage());
            throw new RuntimeException("Error al verificar mediciones consecutivas", e);
        }
    }

    /**
     * Obtiene todos los IDs de pacientes que tienen mediciones registradas.
     * 
     * @return Lista de IDs de pacientes
     */
    public static List<Long> obtenerPacientesConMediciones() {
        String sql = """
            SELECT DISTINCT id_paciente
            FROM tb_control_salud
            WHERE fecha_hora >= SYSTIMESTAMP - INTERVAL '5' DAY
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
            
            logger.info("Se encontraron " + pacientes.size() + " pacientes con mediciones recientes");
            return pacientes;
            
        } catch (Exception e) {
            logger.severe("Error al obtener pacientes con mediciones: " + e.getMessage());
            throw new RuntimeException("Error al consultar pacientes", e);
        }
    }
}
