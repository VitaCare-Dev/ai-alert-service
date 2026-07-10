package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.AlertaIA;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica JDBC real de {@link AlertaService}.
 */
class AlertaServiceTest {

    private static AlertaIA alerta() {
        return AlertaIA.builder()
                .idPaciente(1L)
                .fechaDisparo(LocalDateTime.of(2026, 7, 10, 8, 0))
                .motivoAlerta("Mediciones alteradas")
                .recomendacionIa("Consulta a tu médico.")
                .leida(false)
                .build();
    }

    @Test
    void guardarAlerta_devuelveElIdGenerado() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString(), any(String[].class))).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);
        when(stmt.getGeneratedKeys()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong(1)).thenReturn(77L);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            Long id = AlertaService.guardarAlerta(alerta());

            assertEquals(77L, id);
        }
    }

    @Test
    void guardarAlerta_lanzaExcepcionSiSeAfectaronFilasPeroNoHayClaveGenerada() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString(), any(String[].class))).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);
        when(stmt.getGeneratedKeys()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertThrows(RuntimeException.class, () -> AlertaService.guardarAlerta(alerta()));
        }
    }

    @Test
    void guardarAlerta_lanzaExcepcionSiNoSeAfectaronFilas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString(), any(String[].class))).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertThrows(RuntimeException.class, () -> AlertaService.guardarAlerta(alerta()));
        }
    }

    @Test
    void guardarAlerta_relanzaComoRuntimeExceptionSiFallaLaConexion() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class, () -> AlertaService.guardarAlerta(alerta()));
        }
    }

    @Test
    void existeAlertaReciente_devuelveTrueCuandoHayAlertasEnElRango() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("total")).thenReturn(2);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertTrue(AlertaService.existeAlertaReciente(1L, 24));
        }
    }

    @Test
    void existeAlertaReciente_devuelveFalseCuandoNoHayAlertasEnElRango() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("total")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertFalse(AlertaService.existeAlertaReciente(1L, 24));
        }
    }

    @Test
    void existeAlertaReciente_devuelveFalseCuandoElResultSetNoDevuelveFilas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertFalse(AlertaService.existeAlertaReciente(1L, 24));
        }
    }

    @Test
    void existeAlertaReciente_devuelveFalseSiFallaLaConsulta() {
        // A diferencia de guardarAlerta, este método atrapa la excepción y
        // devuelve false en vez de relanzarla: evita bloquear el análisis de
        // umbrales completo por un problema transitorio al verificar duplicados.
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertFalse(AlertaService.existeAlertaReciente(1L, 24));
        }
    }

    @Test
    void existeAlertaReciente_devuelveFalseSiFallaLaConsultaConRecursosYaAbiertos() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenThrow(new SQLException("fallo en la consulta"));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertFalse(AlertaService.existeAlertaReciente(1L, 24));
        }
    }
}
