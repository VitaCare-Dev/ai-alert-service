package com.grupo10.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GroqService {
    private static final Logger logger = Logger.getLogger(GroqService.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_API_KEY = System.getenv("GROQ_API_KEY");

    public static String consultarGroq(String prompt) throws Exception {
        if (GROQ_API_KEY == null || GROQ_API_KEY.isBlank()) {
            throw new IllegalStateException("La variable de entorno GROQ_API_KEY no está configurada.");
        }

        Map<String, Object> systemMessage = Map.of(
                "role", "system",
                "content", "Eres un asistente de salud proactivo. Genera alertas de máximo 150 caracteres, "
                        + "directas, sin saludos, sin introducciones ni textos de relleno.");
        Map<String, Object> userMessage = Map.of("role", "user", "content", prompt);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "llama-3.3-70b-versatile");
        payload.put("messages", List.of(systemMessage, userMessage));
        payload.put("temperature", 0.2);
        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + GROQ_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(15))
                .build();

        logger.info("Enviando petición a Groq Cloud API...");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return extraerContenidoTexto(response.body());
        } else {
            logger.severe("Error en Groq API. Código: " + response.statusCode() + " | Respuesta: " + response.body());
            throw new RuntimeException("Fallo en Groq API: HTTP " + response.statusCode());
        }
    }

    private static String extraerContenidoTexto(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            String texto = root.path("choices").path(0).path("message").path("content").asText();
            if (!texto.isBlank()) {
                return texto.trim();
            }
            logger.severe("Respuesta de Groq sin contenido utilizable: " + jsonResponse);
        } catch (Exception e) {
            logger.severe("Error al parsear la respuesta de Groq: " + e.getMessage());
        }
        return "No se pudo procesar la recomendación de la IA.";
    }
}