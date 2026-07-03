package com.grupo10.apifunction;

import com.grupo10.config.DatabaseConfig;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Funciones HTTP para consultar y gestionar recomendaciones alimentarias desde
 * el frontend.
 */
public class RecomendacionAPIFunction {

    private static final Logger logger = Logger.getLogger(RecomendacionAPIFunction.class.getName());
    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .create();

    /**
     * Obtiene todas las recomendaciones de un paciente.
     * GET /api/recomendaciones/{idPaciente}
     */
    @FunctionName("ObtenerRecomendaciones")
    public HttpResponseMessage obtenerRecomendaciones(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.FUNCTION, route = "recomendaciones/{idPaciente}") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Consultando recomendaciones para paciente ID: " + idPaciente);

        String sql = """
                SELECT id_recomendacion, id_paciente, titulo, contenido,
                       tipo_recomendacion, fecha_generacion, estado_notificacion, leida
                FROM tb_recomendacion_alimentaria_ia
                WHERE id_paciente = ?
                ORDER BY fecha_generacion DESC
                """;

        List<Map<String, Object>> recomendaciones = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> recomendacion = new HashMap<>();
                    recomendacion.put("idRecomendacion", rs.getLong("id_recomendacion"));
                    recomendacion.put("idPaciente", rs.getLong("id_paciente"));
                    recomendacion.put("titulo", rs.getString("titulo"));
                    recomendacion.put("contenido", rs.getString("contenido"));
                    recomendacion.put("tipoRecomendacion", rs.getString("tipo_recomendacion"));

                    Timestamp ts = rs.getTimestamp("fecha_generacion");
                    if (ts != null) {
                        recomendacion.put("fechaGeneracion",
                                ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    }

                    recomendacion.put("estadoNotificacion", rs.getInt("estado_notificacion") == 1);
                    recomendacion.put("leida", rs.getInt("leida") == 1);

                    recomendaciones.add(recomendacion);
                }
            }

            logger.info("Se encontraron " + recomendaciones.size() + " recomendaciones para el paciente");

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(recomendaciones))
                    .build();

        } catch (Exception e) {
            logger.severe("Error al obtener recomendaciones: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al consultar recomendaciones: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Obtiene solo las recomendaciones no leídas de un paciente.
     * GET /api/recomendaciones/{idPaciente}/no-leidas
     */
    @FunctionName("ObtenerRecomendacionesNoLeidas")
    public HttpResponseMessage obtenerRecomendacionesNoLeidas(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.FUNCTION, route = "recomendaciones/{idPaciente}/no-leidas") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Consultando recomendaciones no leídas para paciente ID: " + idPaciente);

        String sql = """
                SELECT id_recomendacion, id_paciente, titulo, contenido,
                       tipo_recomendacion, fecha_generacion, estado_notificacion, leida
                FROM tb_recomendacion_alimentaria_ia
                WHERE id_paciente = ? AND leida = 0
                ORDER BY fecha_generacion DESC
                """;

        List<Map<String, Object>> recomendaciones = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> recomendacion = new HashMap<>();
                    recomendacion.put("idRecomendacion", rs.getLong("id_recomendacion"));
                    recomendacion.put("idPaciente", rs.getLong("id_paciente"));
                    recomendacion.put("titulo", rs.getString("titulo"));
                    recomendacion.put("contenido", rs.getString("contenido"));
                    recomendacion.put("tipoRecomendacion", rs.getString("tipo_recomendacion"));

                    Timestamp ts = rs.getTimestamp("fecha_generacion");
                    if (ts != null) {
                        recomendacion.put("fechaGeneracion",
                                ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    }

                    recomendacion.put("estadoNotificacion", rs.getInt("estado_notificacion") == 1);
                    recomendacion.put("leida", false);

                    recomendaciones.add(recomendacion);
                }
            }

            logger.info("Se encontraron " + recomendaciones.size() + " recomendaciones no leídas");

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(recomendaciones))
                    .build();

        } catch (Exception e) {
            logger.severe("Error al obtener recomendaciones no leídas: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al consultar recomendaciones: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Marca una recomendación como leída.
     * PUT /api/recomendaciones/{idRecomendacion}/leer
     */
    @FunctionName("MarcarRecomendacionLeida")
    public HttpResponseMessage marcarRecomendacionLeida(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.PUT }, authLevel = AuthorizationLevel.FUNCTION, route = "recomendaciones/{idRecomendacion}/leer") HttpRequestMessage<String> request,
            @BindingName("idRecomendacion") Long idRecomendacion,
            final ExecutionContext context) {
        logger.info("Marcando recomendación como leída: " + idRecomendacion);

        String sql = "UPDATE tb_recomendacion_alimentaria_ia SET leida = 1 WHERE id_recomendacion = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idRecomendacion);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                logger.info("Recomendación marcada como leída exitosamente");
                return request.createResponseBuilder(HttpStatus.OK)
                        .body("{\"success\": true, \"message\": \"Recomendación marcada como leída\"}")
                        .build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"message\": \"Recomendación no encontrada\"}")
                        .build();
            }

        } catch (Exception e) {
            logger.severe("Error al marcar recomendación como leída: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al actualizar recomendación: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Marca todas las recomendaciones de un paciente como leídas.
     * PUT /api/recomendaciones/paciente/{idPaciente}/leer-todas
     */
    @FunctionName("MarcarTodasRecomendacionesLeidas")
    public HttpResponseMessage marcarTodasRecomendacionesLeidas(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.PUT }, authLevel = AuthorizationLevel.FUNCTION, route = "recomendaciones/paciente/{idPaciente}/leer-todas") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Marcando todas las recomendaciones como leídas para paciente: " + idPaciente);

        String sql = "UPDATE tb_recomendacion_alimentaria_ia SET leida = 1 WHERE id_paciente = ? AND leida = 0";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);
            int rowsAffected = stmt.executeUpdate();

            logger.info(rowsAffected + " recomendaciones marcadas como leídas");

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("{\"success\": true, \"recomendacionesMarcadas\": " + rowsAffected + "}")
                    .build();

        } catch (Exception e) {
            logger.severe("Error al marcar todas las recomendaciones: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al actualizar recomendaciones: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
