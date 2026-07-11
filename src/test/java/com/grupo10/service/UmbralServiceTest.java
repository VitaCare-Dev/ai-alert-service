package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.UmbralMedico;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica JDBC real de {@link UmbralService}.
 */
class UmbralServiceTest {

    @Test
    void obtenerUmbralesPorPaciente_devuelveElUmbralEncontrado() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("id_umbral")).thenReturn(5L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getObject("glucosa_max", Integer.class)).thenReturn(180);
        when(rs.getObject("glucosa_min", Integer.class)).thenReturn(70);
        when(rs.getObject("sistolica_max", Integer.class)).thenReturn(140);
        when(rs.getObject("diastolica_max", Integer.class)).thenReturn(90);
        when(rs.getBigDecimal("temperatura_max")).thenReturn(new BigDecimal("38.0"));
        when(rs.getObject("sistolica_min", Integer.class)).thenReturn(90);
        when(rs.getObject("diastolica_min", Integer.class)).thenReturn(60);
        when(rs.getBigDecimal("temperatura_min")).thenReturn(new BigDecimal("35.0"));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            UmbralMedico umbral = UmbralService.obtenerUmbralesPorPaciente(1L);

            assertEquals(180, umbral.getGlucosaMax());
            assertEquals(70, umbral.getGlucosaMin());
            assertEquals(new BigDecimal("38.0"), umbral.getTemperaturaMax());
            assertEquals(90, umbral.getSistolicaMin());
            assertEquals(60, umbral.getDiastolicaMin());
            assertEquals(new BigDecimal("35.0"), umbral.getTemperaturaMin());
        }
    }

    @Test
    void obtenerUmbralesPorPaciente_devuelveNullCuandoElPacienteNoTieneUmbralesConfigurados() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertNull(UmbralService.obtenerUmbralesPorPaciente(1L));
        }
    }

    @Test
    void obtenerUmbralesPorPaciente_relanzaComoRuntimeExceptionSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class, () -> UmbralService.obtenerUmbralesPorPaciente(1L));
        }
    }

    @Test
    void obtenerUmbralesPorPaciente_relanzaComoRuntimeExceptionSiFallaLaConsultaConRecursosYaAbiertos()
            throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenThrow(new SQLException("fallo en la consulta"));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertThrows(RuntimeException.class, () -> UmbralService.obtenerUmbralesPorPaciente(1L));
        }
    }
}
