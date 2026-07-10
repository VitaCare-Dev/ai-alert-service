package com.grupo10.medicalrecommendationgeneratorfunction;

import com.grupo10.HttpResponseMessageMock;
import com.grupo10.service.EnfermedadService;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre {@link AIMedicalRecommendationGeneratorFuction#run} y {@code runManual},
 * usando {@code spy()} sobre {@code generarRecomendacionPaciente} (ya cubierto
 * en {@link AIMedicalRecommendationGeneratorFuctionTest}) para no repetir esa
 * lógica interna.
 */
class AIMedicalRecommendationGeneratorFuctionEntryPointsTest {

    @SuppressWarnings("unchecked")
    private static HttpRequestMessage<String> mockRequest() {
        HttpRequestMessage<String> request = mock(HttpRequestMessage.class);
        org.mockito.Mockito.doAnswer((InvocationOnMock invocation) -> {
            HttpStatus status = invocation.getArgument(0);
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));
        return request;
    }

    private static ExecutionContext mockContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());
        return context;
    }

    @Test
    void run_terminaTempranamenteCuandoNoHayPacientesConEnfermedades() {
        AIMedicalRecommendationGeneratorFuction function = spy(new AIMedicalRecommendationGeneratorFuction());

        try (MockedStatic<EnfermedadService> enfermedadMock = mockStatic(EnfermedadService.class)) {
            enfermedadMock.when(EnfermedadService::obtenerPacientesConEnfermedades).thenReturn(List.of());

            function.run("timer", mockContext());

            verify(function, never()).generarRecomendacionPaciente(anyLong());
        }
    }

    @Test
    void run_procesaCadaPacienteYSigueAunqueUnoFalle() {
        AIMedicalRecommendationGeneratorFuction function = spy(new AIMedicalRecommendationGeneratorFuction());

        try (MockedStatic<EnfermedadService> enfermedadMock = mockStatic(EnfermedadService.class)) {
            enfermedadMock.when(EnfermedadService::obtenerPacientesConEnfermedades).thenReturn(List.of(1L, 2L, 3L));
            doReturn(true).when(function).generarRecomendacionPaciente(1L);
            doReturn(false).when(function).generarRecomendacionPaciente(2L);
            doThrow(new RuntimeException("fallo transitorio")).when(function).generarRecomendacionPaciente(3L);

            function.run("timer", mockContext());

            verify(function).generarRecomendacionPaciente(1L);
            verify(function).generarRecomendacionPaciente(2L);
            verify(function).generarRecomendacionPaciente(3L);
        }
    }

    @Test
    void run_relanzaComoRuntimeExceptionSiFallaObtenerPacientes() {
        AIMedicalRecommendationGeneratorFuction function = spy(new AIMedicalRecommendationGeneratorFuction());

        try (MockedStatic<EnfermedadService> enfermedadMock = mockStatic(EnfermedadService.class)) {
            enfermedadMock.when(EnfermedadService::obtenerPacientesConEnfermedades)
                    .thenThrow(new RuntimeException("sin conexión"));

            assertThrows(RuntimeException.class, () -> function.run("timer", mockContext()));
        }
    }

    @Test
    void runManual_devuelveOkConMensajeDeExitoCuandoSeGeneraUnaRecomendacion() {
        AIMedicalRecommendationGeneratorFuction function = spy(new AIMedicalRecommendationGeneratorFuction());
        doReturn(true).when(function).generarRecomendacionPaciente(1L);

        HttpResponseMessage response = function.runManual(mockRequest(), 1L, mockContext());

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(((String) response.getBody()).contains("Recomendación generada exitosamente"));
    }

    @Test
    void runManual_devuelveOkConMensajeInformativoCuandoNoSeGeneraRecomendacion() {
        AIMedicalRecommendationGeneratorFuction function = spy(new AIMedicalRecommendationGeneratorFuction());
        doReturn(false).when(function).generarRecomendacionPaciente(1L);

        HttpResponseMessage response = function.runManual(mockRequest(), 1L, mockContext());

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(((String) response.getBody()).contains("No fue necesario generar"));
    }

    @Test
    void runManual_devuelve500SiFallaLaGeneracion() {
        AIMedicalRecommendationGeneratorFuction function = spy(new AIMedicalRecommendationGeneratorFuction());
        doThrow(new RuntimeException("fallo inesperado")).when(function).generarRecomendacionPaciente(1L);

        HttpResponseMessage response = function.runManual(mockRequest(), 1L, mockContext());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(((String) response.getBody()).contains("fallo inesperado"));
    }
}
