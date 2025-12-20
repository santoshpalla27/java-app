package com.sysbehavior.platform.connectivity.mysql;

import com.sysbehavior.platform.connectivity.core.ConnectionState;
import com.sysbehavior.platform.connectivity.core.ConnectivityRegistry;
import com.sysbehavior.platform.connectivity.core.DependencyType;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import lombok.extern.slf4j.Slf4j;

/**
 * HikariCP metrics tracker that listens to connection pool events.
 * 
 * WHY: HikariCP provides hooks for monitoring connection lifecycle events.
 * This listener captures connection acquisition timeouts and failures in real-time,
 * allowing us to detect issues immediately rather than waiting for scheduled health checks.
 * 
 * Design: Implements HikariCP's IMetricsTracker interface to receive pool events.
 * Updates ConnectivityRegistry when significant events occur (timeouts, errors).
 * 
 * Note: This is complementary to MySqlConnectionManager's scheduled health checks.
 * The health check provides periodic state verification; this listener provides
 * real-time event detection.
 */
@Slf4j
public class MySqlStateListener implements IMetricsTracker {
    
    private final ConnectivityRegistry registry;
    
    // Track timeout events to detect degraded state
    private int recentTimeouts = 0;
    private static final int TIMEOUT_THRESHOLD = 3; // 3 timeouts in window = DEGRADED
    
    public MySqlStateListener(ConnectivityRegistry registry) {
        this.registry = registry;
        log.info("MySqlStateListener initialized");
    }
    
    /**
     * Called when a connection is successfully acquired from the pool.
     * 
     * @param elapsedAcquiredNanos Time taken to acquire connection in nanoseconds
     */
    @Override
    public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
        long elapsedMs = elapsedAcquiredNanos / 1_000_000;
        
        // Log slow connection acquisition (potential pool exhaustion or network issues)
        if (elapsedMs > 1000) {
            log.warn("Slow MySQL connection acquisition: {}ms", elapsedMs);
        }
        
        // Reset timeout counter on successful acquisition
        if (recentTimeouts > 0) {
            recentTimeouts = 0;
        }
    }
    
    /**
     * Called when connection usage is complete (returned to pool).
     * 
     * @param elapsedBorrowedNanos Time the connection was borrowed in nanoseconds
     */
    @Override
    public void recordConnectionUsageMillis(long elapsedBorrowedNanos) {
        // We don't currently use this, but it could be useful for detecting
        // long-running queries or connection leaks
    }
    
    /**
     * Called when a connection acquisition times out.
     * 
     * WHY: This is a critical event indicating pool exhaustion or connection unavailability.
     * Multiple timeouts indicate DEGRADED state.
     * 
     * @param elapsedAcquiredNanos Time spent waiting before timeout
     */
    @Override
    public void recordConnectionTimeout() {
        recentTimeouts++;
        log.error("MySQL connection acquisition timeout (#{} recent)", recentTimeouts);
        
        // Update state based on timeout frequency
        if (recentTimeouts >= TIMEOUT_THRESHOLD) {
            registry.updateState(
                DependencyType.MYSQL, 
                ConnectionState.DEGRADED, 
                String.format("Connection pool experiencing timeouts (%d recent)", recentTimeouts)
            );
        }
    }
    
    /**
     * Called when a connection is created (not acquired from pool, but newly created).
     * 
     * @param elapsedNanos Time taken to create the connection
     */
    @Override
    public void recordConnectionCreatedMillis(long elapsedNanos) {
        long elapsedMs = elapsedNanos / 1_000_000;
        
        // Log slow connection creation (network issues or DB overload)
        if (elapsedMs > 5000) {
            log.warn("Slow MySQL connection creation: {}ms", elapsedMs);
        }
    }
    
    /**
     * Close the metrics tracker (cleanup).
     */
    @Override
    public void close() {
        log.info("MySqlStateListener closed");
    }
}
