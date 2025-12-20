package com.sysbehavior.platform.connectivity.config;

import com.sysbehavior.platform.connectivity.mysql.MySqlStateListener;
import com.sysbehavior.platform.connectivity.core.ConnectivityRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration for connection blueprints.
 * 
 * WHY: This configuration enables lazy initialization and custom connection monitoring
 * for all dependencies. It ensures the app can start even when dependencies are unavailable.
 * 
 * Design:
 * - Configures DataSource with lazy initialization
 * - Registers MySqlStateListener as HikariCP metrics tracker
 * - Configures retry/backoff properties
 */
@Configuration
@Slf4j
public class ConnectivityConfiguration {
    
    @Autowired
    private ConnectivityRegistry registry;
    
    /**
     * Configure HikariCP DataSource with custom metrics tracker.
     * 
     * WHY: We override the default DataSource bean to:
     * 1. Enable lazy initialization (app starts even if DB is down)
     * 2. Register our MySqlStateListener for connection event tracking
     * 3. Configure non-blocking initialization
     * 
     * @param properties DataSource properties from application.yml
     * @return Configured HikariDataSource
     */
    @Bean
    @Primary
    @Lazy
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        log.info("Configuring HikariCP DataSource with connectivity monitoring");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        
        // Non-blocking initialization
        // WHY: -1 means "don't wait for pool initialization"
        // The app will start even if MySQL is unavailable
        config.setInitializationFailTimeout(-1);
        
        // Register metrics tracker for connection event monitoring
        config.setMetricsTrackerFactory((poolName, poolStats) -> 
            new MySqlStateListener(registry)
        );
        
        try {
            return new HikariDataSource(config);
        } catch (Exception e) {
            log.error("Failed to create HikariDataSource: {}", e.getMessage());
            // Return a DataSource anyway - it will fail when used, but app will start
            return new HikariDataSource(config);
        }
    }
}
