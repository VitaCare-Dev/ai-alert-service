package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.Enfermedad;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica JDBC real de {@link EnfermedadService}.
 */
class EnfermedadServiceTest {

    @Test
    void obtenerEnfermedadesPorPaciente_mapeaCadaFilaDelResultSet() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_enfermedad")).thenReturn(3L);
        when(rs.getString("nombre_enfermedad")).thenReturn("Diabetes tipo 2");
        when(rs.getString("descripcion")).thenReturn("Enfermedad metabólica crónica.");

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            List<Enfermedad> resultado = EnfermedadService.obtenerEnfermedadesPorPaciente(1L);

            assertEquals(1, resultado.size());
            assertEquals("Diabetes tipo 2", resultado.get(0).getNombreEnfermedad());
        }
    }

    @Test
    void obtenerEnfermedadesPorPaciente_devuelveListaVaciaSinFilas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertTrue(EnfermedadService.obtenerEnfermedadesPorPaciente(1L).isEmpty());
        }
    }

    @Test
    void obtenerEnfermedadesPorPaciente_relanzaComoRuntimeExceptionSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class, () -> EnfermedadService.obtenerEnfermedadesPorPaciente(1L));
        }
    }

    @Test
    void obtenerPacientesConEnfermedades_devuelveLosIdsEncontrados() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getLong("id_paciente")).thenReturn(1L, 2L);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            List<Long> pacientes = EnfermedadService.obtenerPacientesConEnfermedades();

            assertEquals(List.of(1L, 2L), pacientes);
        }
    }

    @Test
    void obtenerPacientesConEnfermedades_relanzaComoRuntimeExceptionSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class, EnfermedadService::obtenerPacientesConEnfermedades);
        }
    }
}
