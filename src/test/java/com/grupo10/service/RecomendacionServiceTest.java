package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.RecomendacionAlimentariaIA;

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
 * Cubre la lógica JDBC real de {@link RecomendacionService}.
 */
class RecomendacionServiceTest {

    private static RecomendacionAlimentariaIA recomendacion() {
        return RecomendacionAlimentariaIA.builder()
                .idPaciente(1L)
                .titulo("Recomendaciones para Diabetes tipo 2")
                .contenido("Reduce el consumo de azúcares refinados.")
                .tipoRecomendacion("ALIMENTARIA")
                .fechaGeneracion(LocalDateTime.of(2026, 7, 10, 8, 0))
                .estadoNotificacion(false)
                .leida(false)
                .build();
    }

    @Test
    void guardarRecomendacion_devuelveElIdGenerado() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString(), any(String[].class))).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);
        when(stmt.getGeneratedKeys()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong(1)).thenReturn(88L);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            Long id = RecomendacionService.guardarRecomendacion(recomendacion());

            assertEquals(88L, id);
        }
    }

    @Test
    void guardarRecomendacion_lanzaExcepcionSiSeAfectaronFilasPeroNoHayClaveGenerada() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString(), any(String[].class))).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);
        when(stmt.getGeneratedKeys()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertThrows(RuntimeException.class, () -> RecomendacionService.guardarRecomendacion(recomendacion()));
        }
    }

    @Test
    void guardarRecomendacion_lanzaExcepcionSiNoSeAfectaronFilas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString(), any(String[].class))).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertThrows(RuntimeException.class, () -> RecomendacionService.guardarRecomendacion(recomendacion()));
        }
    }

    @Test
    void guardarRecomendacion_relanzaComoRuntimeExceptionSiFallaLaConexion() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class, () -> RecomendacionService.guardarRecomendacion(recomendacion()));
        }
    }

    @Test
    void existeRecomendacionReciente_devuelveTrueCuandoHayRecomendacionesEnElRango() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("total")).thenReturn(1);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertTrue(RecomendacionService.existeRecomendacionReciente(1L, 2));
        }
    }

    @Test
    void existeRecomendacionReciente_devuelveFalseCuandoNoHayRecomendacionesEnElRango() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("total")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertFalse(RecomendacionService.existeRecomendacionReciente(1L, 2));
        }
    }

    @Test
    void existeRecomendacionReciente_devuelveFalseCuandoElResultSetNoDevuelveFilas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertFalse(RecomendacionService.existeRecomendacionReciente(1L, 2));
        }
    }

    @Test
    void existeRecomendacionReciente_devuelveFalseSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertFalse(RecomendacionService.existeRecomendacionReciente(1L, 2));
        }
    }

    @Test
    void existeRecomendacionReciente_devuelveFalseSiFallaLaConsultaConRecursosYaAbiertos() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenThrow(new SQLException("fallo en la consulta"));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertFalse(RecomendacionService.existeRecomendacionReciente(1L, 2));
        }
    }
}
