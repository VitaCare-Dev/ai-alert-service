package com.grupo10.service;

import com.grupo10.config.DatabaseConfig;
import com.grupo10.model.MedicionGlucosa;
import com.grupo10.model.MedicionVitales;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica JDBC real de {@link MedicionService}, mockeando
 * {@link DatabaseConfig#getConnection()} y la cadena Connection/PreparedStatement/
 * ResultSet. Antes de esto, esta clase solo se "cubría" indirectamente porque
 * otros tests la mockeaban por completo (nunca se ejecutaba su código real).
 */
class MedicionServiceTest {

    @Test
    void obtenerMedicionesGlucosaRecientes_mapeaCadaFilaDelResultSet() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_control")).thenReturn(1L);
        when(rs.getInt("glucosa")).thenReturn(180);
        when(rs.getString("periodo")).thenReturn("AYUNAS");

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            List<MedicionGlucosa> resultado = MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3);

            assertEquals(1, resultado.size());
            assertEquals(180, resultado.get(0).getGlucosa());
            assertEquals("AYUNAS", resultado.get(0).getPeriodo());
        }
    }

    @Test
    void obtenerMedicionesGlucosaRecientes_devuelveListaVaciaSinFilas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertTrue(MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3).isEmpty());
        }
    }

    @Test
    void obtenerMedicionesGlucosaRecientes_relanzaComoRuntimeExceptionSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> MedicionService.obtenerMedicionesGlucosaRecientes(1L, 3));
            assertTrue(ex.getMessage().contains("mediciones de glucosa"));
        }
    }

    @Test
    void obtenerMedicionesVitalesRecientes_mapeaCadaFilaDelResultSet() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_control")).thenReturn(2L);
        when(rs.getObject("presion_sistolica", Integer.class)).thenReturn(150);
        when(rs.getObject("presion_diastolica", Integer.class)).thenReturn(95);
        when(rs.getBigDecimal("temperatura")).thenReturn(new BigDecimal("38.2"));
        when(rs.getBigDecimal("peso")).thenReturn(new BigDecimal("70.5"));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            List<MedicionVitales> resultado = MedicionService.obtenerMedicionesVitalesRecientes(1L, 3);

            assertEquals(1, resultado.size());
            assertEquals(150, resultado.get(0).getPresionSistolica());
            assertEquals(new BigDecimal("38.2"), resultado.get(0).getTemperatura());
        }
    }

    @Test
    void obtenerMedicionesVitalesRecientes_relanzaComoRuntimeExceptionSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class,
                    () -> MedicionService.obtenerMedicionesVitalesRecientes(1L, 3));
        }
    }

    @Test
    void verificarMedicionesConsecutivas_devuelveLasFechasEncontradas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getTimestamp("fecha")).thenReturn(
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 10, 0, 0)),
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 0, 0)));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            List<LocalDateTime> fechas = MedicionService.verificarMedicionesConsecutivas(1L, 3);

            assertEquals(2, fechas.size());
        }
    }

    @Test
    void verificarMedicionesConsecutivas_ignoraFilasConFechaNula() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getTimestamp("fecha")).thenReturn(null);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            assertTrue(MedicionService.verificarMedicionesConsecutivas(1L, 3).isEmpty());
        }
    }

    @Test
    void verificarMedicionesConsecutivas_relanzaComoRuntimeExceptionSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class,
                    () -> MedicionService.verificarMedicionesConsecutivas(1L, 3));
        }
    }

    @Test
    void obtenerPacientesConMediciones_devuelveLosIdsEncontrados() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getLong("id_paciente")).thenReturn(1L, 2L);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            List<Long> pacientes = MedicionService.obtenerPacientesConMediciones();

            assertEquals(List.of(1L, 2L), pacientes);
        }
    }

    @Test
    void obtenerPacientesConMediciones_relanzaComoRuntimeExceptionSiFallaLaConsulta() {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new SQLException("sin conexión"));

            assertThrows(RuntimeException.class, MedicionService::obtenerPacientesConMediciones);
        }
    }
}
