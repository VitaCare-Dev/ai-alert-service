package com.grupo10.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * Configuración de conexión a base de datos.
 * Gestiona el pool de conexiones HikariCP optimizado para entorno Serverless.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DatabaseConfig {

    private static final Logger logger = Logger.getLogger(DatabaseConfig.class.getName());
    private static HikariDataSource dataSource;



    static {
        try {
            initializeDataSource();
        } catch (Exception e) {
            logger.severe("Error al inicializar el pool de conexiones: " + e.getMessage());
            throw new RuntimeException("Error al inicializar el pool de conexiones", e);
        }
    }

    /**
     * Inicializa el pool de conexiones HikariCP.
     * Configura la conexión estándar utilizando variables de entorno.
     */
    private static void initializeDataSource() {
        HikariConfig config = new HikariConfig();

        // Carga de credenciales desde las variables de entorno de Azure
        String jdbcUrl = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        logger.info("Configurando conexión a la base de datos...");

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("oracle.jdbc.OracleDriver");

        // Optimización estricta para Azure Functions y la capa gratuita de Oracle
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(30000); // Retira conexiones inactivas a los 30 segundos
        config.setMaxLifetime(120000); // Destruye conexiones tras 2 minutos
        config.setConnectionTestQuery("SELECT 1 FROM DUAL");
        config.setPoolName("ServerlessOraclePool");

        // Optimizaciones de rendimiento de HikariCP
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection()) {
            logger.info("✓ Conexión exitosa a la base de datos");
        } catch (SQLException e) {
            logger.severe("✗ Error al verificar conexión: " + e.getMessage());
            throw new RuntimeException("No se pudo conectar a la base de datos", e);
        }
    }

    /**
     * Obtiene el DataSource configurado con el pool de conexiones.
     * Re-inicializa el pool si fue cerrado al final de la ejecución anterior.
     * * @return DataSource con pool de conexiones HikariCP
     */
    public static DataSource getDataSource() {
        if (dataSource == null || dataSource.isClosed()) {
            logger.info("Re-inicializando el pool de conexiones...");
            initializeDataSource();
        }
        return dataSource;
    }

    /**
     * Obtiene una conexión del pool de conexiones.
     * * @return conexión activa desde el pool
     * 
     * @throws SQLException si hay error al obtener la conexión
     */
    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Cierra el pool de conexiones.
     * Indispensable llamar a este método en el bloque finally de cada Function.
     */
    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Cerrando pool para liberar sesiones en la base de datos...");
            dataSource.close();
        }
    }

    /**
     * Obtiene estadísticas actuales del pool de conexiones.
     * * @return String con información del estado del pool
     */
    public static String getPoolStats() {
        if (dataSource == null) {
            return "DataSource no inicializado";
        }
        return String.format(
                "Pool Stats - Total: %d, Activas: %d, Inactivas: %d, Esperando: %d",
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }
}
