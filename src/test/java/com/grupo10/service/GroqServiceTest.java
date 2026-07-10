package com.grupo10.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre {@link GroqService#consultarGroq}, reemplazando el HttpClient real
 * por uno mockeado (ver {@code setHttpClientForTesting}/{@code
 * setApiKeyForTesting}, agregados solo para permitir este test) para no
 * depender de una llamada de red real a la API de Groq.
 */
class GroqServiceTest {

    @AfterEach
    void restaurarEstadoOriginal() {
        // GROQ_API_KEY nunca está seteada en el entorno de test, así que
        // "restaurar" equivale a dejarlo en null, igual que al cargar la clase.
        GroqService.setApiKeyForTesting(null);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void consultarGroq_lanzaExcepcionSiNoHayApiKeyConfigurada() {
        GroqService.setApiKeyForTesting(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GroqService.consultarGroq("hola"));
        assertEquals("La variable de entorno GROQ_API_KEY no está configurada.", ex.getMessage());
    }

    @Test
    void consultarGroq_lanzaExcepcionSiLaApiKeyEstaEnBlanco() {
        GroqService.setApiKeyForTesting("   ");

        assertThrows(IllegalStateException.class, () -> GroqService.consultarGroq("hola"));
    }

    @Test
    void consultarGroq_devuelveElContenidoDeLaRespuestaExitosa() throws Exception {
        GroqService.setApiKeyForTesting("fake-key");
        HttpClient httpClientMock = mock(HttpClient.class);
        String cuerpoRespuesta = """
                {"choices": [{"message": {"content": "  Consulta a tu médico pronto.  "}}]}
                """;
        HttpResponse<String> respuesta = mockResponse(200, cuerpoRespuesta);
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(respuesta);
        GroqService.setHttpClientForTesting(httpClientMock);

        String resultado = GroqService.consultarGroq("Genera una alerta médica corta.");

        assertEquals("Consulta a tu médico pronto.", resultado);
    }

    @Test
    void consultarGroq_devuelveMensajeGenericoSiLaRespuestaNoTraeChoices() throws Exception {
        GroqService.setApiKeyForTesting("fake-key");
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpResponse<String> respuesta = mockResponse(200, "{}");
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(respuesta);
        GroqService.setHttpClientForTesting(httpClientMock);

        String resultado = GroqService.consultarGroq("hola");

        assertEquals("No se pudo procesar la recomendación de la IA.", resultado);
    }

    @Test
    void consultarGroq_devuelveMensajeGenericoSiElCuerpoNoEsJsonValido() throws Exception {
        GroqService.setApiKeyForTesting("fake-key");
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpResponse<String> respuesta = mockResponse(200, "esto no es json");
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(respuesta);
        GroqService.setHttpClientForTesting(httpClientMock);

        String resultado = GroqService.consultarGroq("hola");

        assertEquals("No se pudo procesar la recomendación de la IA.", resultado);
    }

    @Test
    void consultarGroq_lanzaRuntimeExceptionCuandoLaApiRespondeConError() throws Exception {
        GroqService.setApiKeyForTesting("fake-key");
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpResponse<String> respuesta = mockResponse(429, "{\"error\": \"rate limited\"}");
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(respuesta);
        GroqService.setHttpClientForTesting(httpClientMock);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> GroqService.consultarGroq("hola"));
        assertEquals("Fallo en Groq API: HTTP 429", ex.getMessage());
    }
}
