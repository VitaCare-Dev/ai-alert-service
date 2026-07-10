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
 * Cubre {@link AlertaAPIFunction}, mockeando {@link DatabaseConfig#getConnection()}
 * (llamado estático) y la cadena JDBC completa (Connection/PreparedStatement/
 * ResultSet), para no depender de una base de datos Oracle real.
 */
class AlertaAPIFunctionTest {

    private final AlertaAPIFunction function = new AlertaAPIFunction();

    @SuppressWarnings("unchecked")
    private static HttpRequestMessage<String> mockRequest() {
        HttpRequestMessage<String> request = mock(HttpRequestMessage.class);
        // El builder mock necesita conocer el status recibido en cada llamada
        // (createResponseBuilder(HttpStatus.OK), ...NOT_FOUND, etc.); si
        // solo devolviéramos una instancia fija sin propagarlo, .build()
        // produciría siempre un status nulo. Mismo patrón que usa el
        // FunctionTest.java de ejemplo del arquetipo.
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
    void obtenerAlertas_devuelveLasAlertasDelPacienteComoJson() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        // Una sola fila, luego se acaba el cursor.
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_alerta_ia")).thenReturn(10L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getTimestamp("fecha_disparo")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 7, 1, 8, 0)));
        when(rs.getString("motivo_alerta")).thenReturn("Glucosa alterada");
        when(rs.getString("recomendacion_ia")).thenReturn("Consulta a tu médico.");
        when(rs.getInt("leida")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerAlertas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            String body = (String) response.getBody();
            assertTrue(body.contains("Glucosa alterada"));
            assertTrue(body.contains("\"leida\":false"));
        }
    }

    @Test
    void obtenerAlertas_omiteLaFechaDeDisparoCuandoEsNulaYMarcaLeidaEnTrue() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_alerta_ia")).thenReturn(13L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getTimestamp("fecha_disparo")).thenReturn(null);
        when(rs.getString("motivo_alerta")).thenReturn("Glucosa alterada");
        when(rs.getString("recomendacion_ia")).thenReturn("Consulta a tu médico.");
        when(rs.getInt("leida")).thenReturn(1);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerAlertas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            String body = (String) response.getBody();
            assertTrue(body.contains("\"leida\":true"));
            assertTrue(!body.contains("fechaDisparo"));
        }
    }

    @Test
    void obtenerAlertas_devuelveListaVaciaCuandoElPacienteNoTieneAlertas() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerAlertas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("[]", response.getBody());
        }
    }

    @Test
    void obtenerAlertas_devuelve500CuandoFallaLaConsultaALaBaseDeDatos() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.obtenerAlertas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void obtenerAlertasNoLeidas_marcaTodasComoNoLeidasSinImportarElValorDeLaColumna() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_alerta_ia")).thenReturn(11L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getTimestamp("fecha_disparo")).thenReturn(null);
        when(rs.getString("motivo_alerta")).thenReturn("Presión alta");
        when(rs.getString("recomendacion_ia")).thenReturn("Reposo.");
        when(rs.getInt("leida")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerAlertasNoLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("\"leida\":false"));
        }
    }

    @Test
    void obtenerAlertasNoLeidas_incluyeLaFechaDeDisparoCuandoNoEsNula() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id_alerta_ia")).thenReturn(12L);
        when(rs.getLong("id_paciente")).thenReturn(1L);
        when(rs.getTimestamp("fecha_disparo")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 7, 5, 10, 0)));
        when(rs.getString("motivo_alerta")).thenReturn("Temperatura alterada");
        when(rs.getString("recomendacion_ia")).thenReturn("Consulta a tu médico.");
        when(rs.getInt("leida")).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.obtenerAlertasNoLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("fechaDisparo"));
        }
    }

    @Test
    void obtenerAlertasNoLeidas_devuelve500CuandoFallaLaConsultaALaBaseDeDatos() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.obtenerAlertasNoLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void marcarAlertaLeida_devuelve500CuandoFallaLaActualizacion() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.marcarAlertaLeida(mockRequest(), 10L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void marcarTodasAlertasLeidas_devuelve500CuandoFallaLaActualizacion() throws Exception {
        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenThrow(new java.sql.SQLException("Conexión rechazada"));

            HttpResponseMessage response = function.marcarTodasAlertasLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Conexión rechazada"));
        }
    }

    @Test
    void marcarAlertaLeida_devuelve500CuandoFallaLaActualizacionConRecursosYaAbiertos() throws Exception {
        // A diferencia de marcarAlertaLeida_devuelve500CuandoFallaLaActualizacion
        // (falla al abrir la conexión), aquí conn/stmt se abren con éxito y es
        // executeUpdate() quien falla: ejercita la rama del try-with-resources
        // donde sí hay recursos que cerrar antes de propagar la excepción.
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenThrow(new java.sql.SQLException("Fallo al actualizar"));

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarAlertaLeida(mockRequest(), 10L, mockContext());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getBody()).contains("Fallo al actualizar"));
        }
    }

    @Test
    void marcarAlertaLeida_devuelveOkCuandoLaAlertaExiste() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarAlertaLeida(mockRequest(), 10L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("\"success\": true"));
        }
    }

    @Test
    void marcarAlertaLeida_devuelve404CuandoLaAlertaNoExiste() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarAlertaLeida(mockRequest(), 999L, mockContext());

            assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        }
    }

    @Test
    void marcarTodasAlertasLeidas_informaCuantasAlertasSeMarcaron() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(3);

        try (MockedStatic<DatabaseConfig> dbMock = mockStatic(DatabaseConfig.class)) {
            dbMock.when(DatabaseConfig::getConnection).thenReturn(conn);

            HttpResponseMessage response = function.marcarTodasAlertasLeidas(mockRequest(), 1L, mockContext());

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(((String) response.getBody()).contains("\"alertasMarcadas\": 3"));
        }
    }
}
