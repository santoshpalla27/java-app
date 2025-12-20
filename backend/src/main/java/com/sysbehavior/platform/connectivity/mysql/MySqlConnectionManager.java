package com.sysbehavior.platform.connectivity.mysql;

import com.sysbehavior.platform.connectivity.core.ConnectionState;
import com.sysbehavior.platform.connectivity.core.ConnectivityRegistry;
import com.sysbehavior.platform.connectivity.core.DependencyType;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages MySQL connection state by monitoring HikariCP pool health.
 * 
 * WHY: HikariCP handles reconnection internally, but doesn't expose connection state
 * in a way that's useful for observability. This manager classifies pool health
 * into our standard state model (CONNECTED, DEGRADED, FAILED) and tracks it centrally.
 * 
 * Design:
 * - Uses scheduled health checks to probe the pool
 * - Classifies state based on connection acquisition success/failure
 * - Tracks pool metrics (active, idle, total connections)
 * - Does NOT manually manage connections (HikariCP does that)
 */
@Component
@Slf4j
public class MySqlConnectionManager {
    
    private final ConnectivityRegistry registry;
    private final DataSource dataSource;
    
    // Track consecutive failures to distinguish transient errors from persistent failures
    private int consecutiveFailures = 0;
    private static final int DEGRADED_THRESHOLD = 2; // 2 failures = DEGRADED
    private static final int FAILED_THRESHOLD = 5;   // 5 failures = FAILED
    
    @Autowired
    public MySqlConnectionManager(ConnectivityRegistry registry, DataSource dataSource) {
        this.registry = registry;
        this.dataSource = dataSource;
        log.info("MySqlConnectionManager initialized");
    }
    
    /**
     * Scheduled health check for MySQL connection.
     * Runs every 5 seconds to probe connection health.
     * 
     * WHY: We can't rely solely on application usage to detect failures.
     * A scheduled probe ensures we detect issues even during idle periods.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void checkHealth() {
        try {
            // Attempt to get a connection and execute a simple query
            try (Connection conn = dataSource.getConnection()) {
                boolean isValid = conn.isValid(5); // 5 second timeout
                
                if (isValid) {
                    // Connection successful - update state
                    handleSuccess();
                } else {
                    // Connection invalid
                    handleFailure("Connection validation failed");
                }
            }
        } catch (SQLException e) {
            // Connection acquisition or validation failed
            handleFailure(e.getMessage());
        } catch (Exception e) {
            // Unexpected error (e.g., DataSource not initialized yet)
            log.debug("MySQL health check skipped: {}", e.getMessage());
            // Don't count as failure - might be during startup
        }
        
        // Update pool metrics regardless of health check result
        updatePoolMetrics();
    }
    
    /**
     * Handle successful connection.
     * Resets failure counter and updates state to CONNECTED.
     */
    private void handleSuccess() {
        if (consecutiveFailures > 0) {
            log.info("MySQL connection recovered after {} failures", consecutiveFailures);
        }
        consecutiveFailures = 0;
        registry.updateState(DependencyType.MYSQL, ConnectionState.CONNECTED, null);
    }
    
    /**
     * Handle connection failure.
     * Increments failure counter and updates state based on failure count.
     * 
     * WHY: We distinguish between transient failures (DEGRADED) and persistent failures (FAILED).
     * A single timeout might be network blip; 5 consecutive failures indicate a real problem.
     */
    private void handleFailure(String errorMessage) {
        consecutiveFailures++;
        
        ConnectionState newState;
        if (consecutiveFailures >= FAILED_THRESHOLD) {
            newState = ConnectionState.FAILED;
        } else if (consecutiveFailures >= DEGRADED_THRESHOLD) {
            newState = ConnectionState.DEGRADED;
        } else {
            newState = ConnectionState.RETRYING;
        }
        
        log.warn("MySQL connection failure #{}: {}", consecutiveFailures, errorMessage);
        registry.updateState(DependencyType.MYSQL, newState, errorMessage);
    }
    
    /**
     * Update pool metrics in the registry metadata.
     * 
     * WHY: Pool metrics help diagnose issues. For example:
     * - All connections active = pool exhaustion
     * - Zero idle connections = high load
     * - Connection acquisition time high = slow queries or network issues
     */
    private void updatePoolMetrics() {
        if (!(dataSource instanceof HikariDataSource)) {
            return; // Not HikariCP, can't get metrics
        }
        
        try {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDS.getHikariPoolMXBean();
            
            if (poolMXBean != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("activeConnections", poolMXBean.getActiveConnections());
                metadata.put("idleConnections", poolMXBean.getIdleConnections());
                metadata.put("totalConnections", poolMXBean.getTotalConnections());
                metadata.put("threadsAwaitingConnection", poolMXBean.getThreadsAwaitingConnection());
                
                registry.updateMetadata(DependencyType.MYSQL, metadata);
            }
        } catch (Exception e) {
            log.debug("Failed to update MySQL pool metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Manually trigger a health check.
     * Useful for testing or immediate state verification.
     */
    public void triggerHealthCheck() {
        checkHealth();
    }
}
