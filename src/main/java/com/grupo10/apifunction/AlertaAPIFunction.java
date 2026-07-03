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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Funciones HTTP para consultar y gestionar alertas desde el frontend.
 */
public class AlertaAPIFunction {

    private static final Logger logger = Logger.getLogger(AlertaAPIFunction.class.getName());
    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .create();

    /**
     * Obtiene todas las alertas de un paciente.
     * GET /api/alertas/{idPaciente}
     */
    @FunctionName("ObtenerAlertas")
    public HttpResponseMessage obtenerAlertas(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.FUNCTION, route = "alertas/{idPaciente}") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Consultando alertas para paciente ID: " + idPaciente);

        String sql = """
                SELECT id_alerta_ia, id_paciente, fecha_disparo, motivo_alerta,
                       recomendacion_ia, leida
                FROM tb_alerta_ia
                WHERE id_paciente = ?
                ORDER BY fecha_disparo DESC
                """;

        List<Map<String, Object>> alertas = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> alerta = new HashMap<>();
                    alerta.put("idAlertaIa", rs.getLong("id_alerta_ia"));
                    alerta.put("idPaciente", rs.getLong("id_paciente"));

                    Timestamp ts = rs.getTimestamp("fecha_disparo");
                    if (ts != null) {
                        alerta.put("fechaDisparo", ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    }

                    alerta.put("motivoAlerta", rs.getString("motivo_alerta"));
                    alerta.put("recomendacionIa", rs.getString("recomendacion_ia"));
                    alerta.put("leida", rs.getInt("leida") == 1);

                    alertas.add(alerta);
                }
            }

            logger.info("Se encontraron " + alertas.size() + " alertas para el paciente");

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(alertas))
                    .build();

        } catch (Exception e) {
            logger.severe("Error al obtener alertas: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al consultar alertas: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Obtiene solo las alertas no leídas de un paciente.
     * GET /api/alertas/{idPaciente}/no-leidas
     */
    @FunctionName("ObtenerAlertasNoLeidas")
    public HttpResponseMessage obtenerAlertasNoLeidas(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.FUNCTION, route = "alertas/{idPaciente}/no-leidas") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Consultando alertas no leídas para paciente ID: " + idPaciente);

        String sql = """
                SELECT id_alerta_ia, id_paciente, fecha_disparo, motivo_alerta,
                       recomendacion_ia, leida
                FROM tb_alerta_ia
                WHERE id_paciente = ? AND leida = 0
                ORDER BY fecha_disparo DESC
                """;

        List<Map<String, Object>> alertas = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> alerta = new HashMap<>();
                    alerta.put("idAlertaIa", rs.getLong("id_alerta_ia"));
                    alerta.put("idPaciente", rs.getLong("id_paciente"));

                    Timestamp ts = rs.getTimestamp("fecha_disparo");
                    if (ts != null) {
                        alerta.put("fechaDisparo", ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    }

                    alerta.put("motivoAlerta", rs.getString("motivo_alerta"));
                    alerta.put("recomendacionIa", rs.getString("recomendacion_ia"));
                    alerta.put("leida", false);

                    alertas.add(alerta);
                }
            }

            logger.info("Se encontraron " + alertas.size() + " alertas no leídas");

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(alertas))
                    .build();

        } catch (Exception e) {
            logger.severe("Error al obtener alertas no leídas: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al consultar alertas: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Marca una alerta como leída.
     * PUT /api/alertas/{idAlerta}/leer
     */
    @FunctionName("MarcarAlertaLeida")
    public HttpResponseMessage marcarAlertaLeida(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.PUT }, authLevel = AuthorizationLevel.FUNCTION, route = "alertas/{idAlerta}/leer") HttpRequestMessage<String> request,
            @BindingName("idAlerta") Long idAlerta,
            final ExecutionContext context) {
        logger.info("Marcando alerta como leída: " + idAlerta);

        String sql = "UPDATE tb_alerta_ia SET leida = 1 WHERE id_alerta_ia = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idAlerta);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                logger.info("Alerta marcada como leída exitosamente");
                return request.createResponseBuilder(HttpStatus.OK)
                        .body("{\"success\": true, \"message\": \"Alerta marcada como leída\"}")
                        .build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"message\": \"Alerta no encontrada\"}")
                        .build();
            }

        } catch (Exception e) {
            logger.severe("Error al marcar alerta como leída: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al actualizar alerta: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Marca todas las alertas de un paciente como leídas.
     * PUT /api/alertas/paciente/{idPaciente}/leer-todas
     */
    @FunctionName("MarcarTodasAlertasLeidas")
    public HttpResponseMessage marcarTodasAlertasLeidas(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.PUT }, authLevel = AuthorizationLevel.FUNCTION, route = "alertas/paciente/{idPaciente}/leer-todas") HttpRequestMessage<String> request,
            @BindingName("idPaciente") Long idPaciente,
            final ExecutionContext context) {
        logger.info("Marcando todas las alertas como leídas para paciente: " + idPaciente);

        String sql = "UPDATE tb_alerta_ia SET leida = 1 WHERE id_paciente = ? AND leida = 0";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idPaciente);
            int rowsAffected = stmt.executeUpdate();

            logger.info(rowsAffected + " alertas marcadas como leídas");

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("{\"success\": true, \"alertasMarcadas\": " + rowsAffected + "}")
                    .build();

        } catch (Exception e) {
            logger.severe("Error al marcar todas las alertas: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error al actualizar alertas: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
