package com.grupo10.apifunction;

import com.grupo10.HttpResponseMessageMock;
import com.grupo10.config.DatabaseConfig;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Cubre {@link RecomendacionAPIFunction}, mockeando {@link DatabaseConfig#getConnection()}
 * y la cadena JDBC completa. Mismo patrón que {@link AlertaAPIFunctionTest}.
 */
class RecomendacionAPIFunctionTest {

    private final RecomendacionAPIFunction function = new RecomendacionAPIFunction();

    @SuppressWarnings("unchecked")
    private static HttpRequestMessage<String> mockRequest() {
        HttpRequestMessage<String> request = mock(HttpRequestMessage.class);
        doAnswer((InvocationOnMock invocation) -> {
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
    void obtenerRecomendaciones_devuelveLasRecomendacionesDelPacienteComoJson() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_recomendacion")).thenReturn(20L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getString("titulo")).thenReturn("Recomendaciones para Diabetes tipo 2");
        when(rs.getString("contenido")).thenReturn("Reduce el consumo de azúcares refinados.");
        when(rs.getString("tipo_recomendacion")).thenReturn("ALIMENTARIA");
        when(rs.getTimestamp("fecha_generacion")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 7, 1, 8, 0)));
        when(rs.getInt("estado_notificacion")).thenReturn(0);
        when(rs.getInt("leida")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerRecomendaciones(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            String body = (String) response.getBody();
            assertTrue(body.contains("Recomendaciones para Diabetes tipo 2"));
            assertTrue(body.contains("\"estadoNotificacion\":false"));
        }
    }

    @Test
    void obtenerRecomendaciones_omiteLaFechaDeGeneracionCuandoEsNulaYMarcaEstadosEnTrue() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_recomendacion")).thenReturn(23L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getString("titulo")).thenReturn("Recomendaciones para Obesidad");
        when(rs.getString("contenido")).thenReturn("Controla las porciones.");
        when(rs.getString("tipo_recomendacion")).thenReturn("ALIMENTARIA");
        when(rs.getTimestamp("fecha_generacion")).thenReturn(null);
        when(rs.getInt("estado_notificacion")).thenReturn(1);
        when(rs.getInt("leida")).thenReturn(1);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerRecomendaciones(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            String body = (String) response.getBody();
            assertTrue(body.contains("\"estadoNotificacion\":true"));
            assertTrue(body.contains("\"leida\":true"));
            assertTrue(!body.contains("fechaGeneracion"));
        }
    }

    @Test
    void obtenerRecomendaciones_devuelveListaVaciaCuandoElPacienteNoTieneRecomendaciones() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerRecomendaciones(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("[]", response.getBody());
        }
    }

    @Test
    void obtenerRecomendaciones_devuelve500CuandoFallaLaConsultaALaBaseDeDatos() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.obtenerRecomendaciones(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void obtenerRecomendacionesNoLeidas_marcaTodasComoNoLeidasSinImportarElValorDeLaColumna() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_recomendacion")).thenReturn(21L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getString("titulo")).thenReturn("Recomendaciones Alimentarias Personalizadas");
        when(rs.getString("contenido")).thenReturn("Aumenta el consumo de fibra.");
        when(rs.getString("tipo_recomendacion")).thenReturn("ALIMENTARIA");
        when(rs.getTimestamp("fecha_generacion")).thenReturn(null);
        when(rs.getInt("estado_notificacion")).thenReturn(1);
        when(rs.getInt("leida")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerRecomendacionesNoLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("\"leida\":false"));
        }
    }

    @Test
    void obtenerRecomendacionesNoLeidas_incluyeLaFechaDeGeneracionCuandoNoEsNula() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_recomendacion")).thenReturn(22L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getString("titulo")).thenReturn("Recomendaciones para Hipertensión");
        when(rs.getString("contenido")).thenReturn("Reduce el sodio.");
        when(rs.getString("tipo_recomendacion")).thenReturn("ALIMENTARIA");
        when(rs.getTimestamp("fecha_generacion")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 7, 5, 9, 0)));
        when(rs.getInt("estado_notificacion")).thenReturn(0);
        when(rs.getInt("leida")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerRecomendacionesNoLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("fechaGeneracion"));
        }
    }

    @Test
    void obtenerRecomendacionesNoLeidas_devuelve500CuandoFallaLaConsultaALaBaseDeDatos() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.obtenerRecomendacionesNoLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void marcarRecomendacionLeida_devuelve500CuandoFallaLaActualizacion() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.marcarRecomendacionLeida(mockRequest(), 20L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void marcarTodasRecomendacionesLeidas_devuelve500CuandoFallaLaActualizacion() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.marcarTodasRecomendacionesLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void marcarRecomendacionLeida_devuelve500CuandoFallaLaActualizacionConRecursosYaAbiertos() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenThrow(new java.sql.SQLException("Fallo al actualizar"));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarRecomendacionLeida(mockRequest(), 20L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Fallo al actualizar"));
        }
    }

    @Test
    void marcarRecomendacionLeida_devuelveOkCuandoLaRecomendacionExiste() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarRecomendacionLeida(mockRequest(), 20L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("\"success\": true"));
        }
    }

    @Test
    void marcarRecomendacionLeida_devuelve404CuandoLaRecomendacionNoExiste() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarRecomendacionLeida(mockRequest(), 999L, mockContext());

            assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        }
    }

    @Test
    void marcarTodasRecomendacionesLeidas_informaCuantasSeMarcaron() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(2);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarTodasRecomendacionesLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("\"recomendacionesMarcadas\": 2"));
        }
    }
}
