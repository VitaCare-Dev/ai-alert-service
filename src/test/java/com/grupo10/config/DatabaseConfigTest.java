package com.grupo10.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica real de {@link DatabaseConfig}, interceptando la
 * construcción de {@link HikariDataSource} con {@code mockConstruction} para
 * no depender de una conexión real a Oracle.
 */
class DatabaseConfigTest {

    @BeforeEach
    @AfterEach
    void resetDataSourceEstatico() throws Exception {
        Field field = DatabaseConfig.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void getConnection_inicializaElPoolLaPrimeraVezYDevuelveUnaConexion() throws Exception {
        Connection conexionEsperada = mock(Connection.class);

        try (MockedConstruction<HikariDataSource> construccion = mockConstruction(HikariDataSource.class,
                (mockDs, context) -> when(mockDs.getConnection()).thenReturn(conexionEsperada))) {

            Connection resultado = DatabaseConfig.getConnection();

            assertEquals(conexionEsperada, resultado);
            assertEquals(1, construccion.constructed().size());
        }
    }

    @Test
    void initializeDataSource_relanzaComoRuntimeExceptionSiFallaLaVerificacion() {
        try (MockedConstruction<HikariDataSource> construccion = mockConstruction(HikariDataSource.class,
                (mockDs, context) -> when(mockDs.getConnection()).thenThrow(new SQLException("sin red")))) {

            assertThrows(RuntimeException.class, DatabaseConfig::getConnection);
        }
    }

    @Test
    void getDataSource_reinicializaElPoolSiElExistenteEstaCerrado() throws Exception {
        Connection conexion = mock(Connection.class);

        try (MockedConstruction<HikariDataSource> construccion = mockConstruction(HikariDataSource.class,
                (mockDs, context) -> {
                    when(mockDs.getConnection()).thenReturn(conexion);
                    when(mockDs.isClosed()).thenReturn(false);
                })) {

            DatabaseConfig.getDataSource();
            assertEquals(1, construccion.constructed().size());

            HikariDataSource primero = construccion.constructed().get(0);
            when(primero.isClosed()).thenReturn(true);

            DatabaseConfig.getDataSource();

            assertEquals(2, construccion.constructed().size());
        }
    }

    @Test
    void getDataSource_reutilizaElMismoPoolSiYaEstaAbiertoYNoFueCerrado() throws Exception {
        Connection conexion = mock(Connection.class);

        try (MockedConstruction<HikariDataSource> construccion = mockConstruction(HikariDataSource.class,
                (mockDs, context) -> {
                    when(mockDs.getConnection()).thenReturn(conexion);
                    when(mockDs.isClosed()).thenReturn(false);
                })) {

            DataSource primeraLlamada = DatabaseConfig.getDataSource();
            DataSource segundaLlamada = DatabaseConfig.getDataSource();

            assertEquals(1, construccion.constructed().size());
            assertEquals(primeraLlamada, segundaLlamada);
        }
    }

    @Test
    void closeDataSource_cierraElPoolCuandoEstaAbierto() throws Exception {
        try (MockedConstruction<HikariDataSource> construccion = mockConstruction(HikariDataSource.class,
                (mockDs, context) -> {
                    when(mockDs.getConnection()).thenReturn(mock(Connection.class));
                    when(mockDs.isClosed()).thenReturn(false);
                })) {

            DatabaseConfig.getConnection();
            HikariDataSource creado = construccion.constructed().get(0);

            DatabaseConfig.closeDataSource();

            verify(creado, times(1)).close();
        }
    }

    @Test
    void closeDataSource_noHaceNadaSiNoHayPoolInicializado() {
        DatabaseConfig.closeDataSource();

        assertEquals("DataSource no inicializado", DatabaseConfig.getPoolStats());
    }

    @Test
    void closeDataSource_noVuelveACerrarSiYaEstaCerrado() throws Exception {
        try (MockedConstruction<HikariDataSource> construccion = mockConstruction(HikariDataSource.class,
                (mockDs, context) -> {
                    when(mockDs.getConnection()).thenReturn(mock(Connection.class));
                    when(mockDs.isClosed()).thenReturn(true);
                })) {

            DatabaseConfig.getConnection();
            HikariDataSource creado = construccion.constructed().get(0);

            DatabaseConfig.closeDataSource();

            verify(creado, never()).close();
        }
    }

    @Test
    void getPoolStats_devuelveMensajeCuandoNoHayDataSourceInicializado() {
        assertEquals("DataSource no inicializado", DatabaseConfig.getPoolStats());
    }

    @Test
    void getPoolStats_devuelveElResumenFormateadoCuandoHayDataSource() throws Exception {
        HikariPoolMXBean poolMXBean = mock(HikariPoolMXBean.class);
        when(poolMXBean.getTotalConnections()).thenReturn(2);
        when(poolMXBean.getActiveConnections()).thenReturn(1);
        when(poolMXBean.getIdleConnections()).thenReturn(1);
        when(poolMXBean.getThreadsAwaitingConnection()).thenReturn(0);

        try (MockedConstruction<HikariDataSource> construccion = mockConstruction(HikariDataSource.class,
                (mockDs, context) -> {
                    when(mockDs.getConnection()).thenReturn(mock(Connection.class));
                    when(mockDs.getHikariPoolMXBean()).thenReturn(poolMXBean);
                })) {

            DatabaseConfig.getConnection();

            String stats = DatabaseConfig.getPoolStats();

            assertEquals("Pool Stats - Total: 2, Activas: 1, Inactivas: 1, Esperando: 0", stats);
        }
    }
}
