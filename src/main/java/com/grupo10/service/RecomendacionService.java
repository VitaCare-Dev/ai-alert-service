package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.RecomendacionAlimentariaIA;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.logging.Logger;

/**
 * Servicio para gestionar recomendaciones alimentarias generadas por IA.
 */
public class RecomendacionService {
    
    private static final Logger logger = Logger.getLogger(RecomendacionService.class.getName());

    /**
     * Guarda una nueva recomendación alimentaria en la base de datos.
     * 
     * @param recomendacion Recomendación a guardar
     * @return ID de la recomendación generada
     */
    public static Long guardarRecomendacion(RecomendacionAlimentariaIA recomendacion) {
        String sql = """
            INSERT INTO tb_recomendacion_alimentaria_ia
            (id_paciente, titulo, contenido, tipo_recomendacion, fecha_generacion, estado_notificacion, leida)
            VALUES (?, ?, ?, ?, ?, 0, 0)
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, new String[] { "id_recomendacion" })) {

            stmt.setLong(1, recomendacion.getIdPaciente());
            stmt.setString(2, recomendacion.getTitulo());
            stmt.setString(3, recomendacion.getContenido());
            stmt.setString(4, recomendacion.getTipoRecomendacion());
            stmt.setTimestamp(5, Timestamp.valueOf(recomendacion.getFechaGeneracion()));

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        Long idGenerado = rs.getLong(1);
                        logger.info("Recomendación guardada exitosamente con ID: " + idGenerado);
                        return idGenerado;
                    }
                }
            }

            throw new RuntimeException("No se pudo obtener el ID de la recomendación generada");

        } catch (Exception e) {
            logger.severe("Error al guardar recomendación: " + e.getMessage());
            throw new RuntimeException("Error al guardar recomendación en la base de datos", e);
        }
    }

    /**
     * Verifica si existe una recomendación reciente para evitar duplicados.
     * 
     * @param idPaciente ID del paciente
     * @param diasAtras Días hacia atrás para buscar recomendaciones
     * @return true si existe una recomendación reciente, false en caso contrario
     */
    public static boolean existeRecomendacionReciente(Long idPaciente, int diasAtras) {
        String sql = """
            SELECT COUNT(*) as total
            FROM tb_recomendacion_alimentaria_ia
            WHERE id_paciente = ?
              AND fecha_generacion >= SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY')
            """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, idPaciente);
            stmt.setInt(2, diasAtras);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    return total > 0;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.severe("Error al verificar recomendaciones recientes: " + e.getMessage());
            return false;
        }
    }
}
