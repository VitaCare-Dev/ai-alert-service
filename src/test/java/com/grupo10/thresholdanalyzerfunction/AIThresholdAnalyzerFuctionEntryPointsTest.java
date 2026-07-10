package com.grupo10.thresholdanalyzerfunction;

import com.grupo10.HttpResponseMessageMock;
import com.grupo10.service.MedicionService;

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
 * Cubre {@link AIThresholdAnalyzerFuction#run} y {@code runManual}, los dos
 * puntos de entrada de la Function (Timer y HTTP), usando {@code spy()} sobre
 * {@code analizarPaciente} (ya cubierto exhaustivamente en
 * {@link AIThresholdAnalyzerFuctionTest}) para no repetir esa lógica interna.
 */
class AIThresholdAnalyzerFuctionEntryPointsTest {

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
    void run_terminaTempranamenteCuandoNoHayPacientesConMediciones() {
        AIThresholdAnalyzerFuction function = spy(new AIThresholdAnalyzerFuction());

        try (MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class)) {
            medicionMock.when(MedicionService::obtenerPacientesConMediciones).thenReturn(List.of());

            function.run("timer", mockContext());

            verify(function, never()).analizarPaciente(anyLong());
        }
    }

    @Test
    void run_procesaCadaPacienteYSigueAunqueUnoFalle() {
        AIThresholdAnalyzerFuction function = spy(new AIThresholdAnalyzerFuction());

        try (MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class)) {
            medicionMock.when(MedicionService::obtenerPacientesConMediciones).thenReturn(List.of(1L, 2L, 3L));
            doReturn(true).when(function).analizarPaciente(1L);
            doReturn(false).when(function).analizarPaciente(2L);
            doThrow(new RuntimeException("fallo transitorio")).when(function).analizarPaciente(3L);

            function.run("timer", mockContext());

            verify(function).analizarPaciente(1L);
            verify(function).analizarPaciente(2L);
            verify(function).analizarPaciente(3L);
        }
    }

    @Test
    void run_relanzaComoRuntimeExceptionSiFallaObtenerPacientes() {
        AIThresholdAnalyzerFuction function = spy(new AIThresholdAnalyzerFuction());

        try (MockedStatic<MedicionService> medicionMock = mockStatic(MedicionService.class)) {
            medicionMock.when(MedicionService::obtenerPacientesConMediciones)
                    .thenThrow(new RuntimeException("sin conexión"));

            assertThrows(RuntimeException.class, () -> function.run("timer", mockContext()));
        }
    }

    @Test
    void runManual_devuelveOkConMensajeDeExitoCuandoSeGeneraUnaAlerta() {
        AIThresholdAnalyzerFuction function = spy(new AIThresholdAnalyzerFuction());
        doReturn(true).when(function).analizarPaciente(1L);

        HttpResponseMessage response = function.runManual(mockRequest(), 1L, mockContext());

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(((String) response.getBody()).contains("Alerta generada exitosamente"));
    }

    @Test
    void runManual_devuelveOkConMensajeInformativoCuandoNoSeGeneraAlerta() {
        AIThresholdAnalyzerFuction function = spy(new AIThresholdAnalyzerFuction());
        doReturn(false).when(function).analizarPaciente(1L);

        HttpResponseMessage response = function.runManual(mockRequest(), 1L, mockContext());

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(((String) response.getBody()).contains("No se detectaron anomalías"));
    }

    @Test
    void runManual_devuelve500SiFallaElAnalisis() {
        AIThresholdAnalyzerFuction function = spy(new AIThresholdAnalyzerFuction());
        doThrow(new RuntimeException("fallo inesperado")).when(function).analizarPaciente(1L);

        HttpResponseMessage response = function.runManual(mockRequest(), 1L, mockContext());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(((String) response.getBody()).contains("fallo inesperado"));
    }
}
