package com.sysbehavior.platform.connectivity.core;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of a dependency's connection state at a point in time.
 * 
 * WHY: Immutability ensures thread-safe reads without locking.
 * This class is the primary data structure exposed via the /internal/connectivity endpoint.
 * 
 * Design: Uses Lombok @Value for immutability and @Builder for flexible construction.
 */
@Value
@Builder
public class ConnectionSnapshot {
    /**
     * The type of dependency (MYSQL, REDIS, KAFKA).
     */
    DependencyType dependencyType;
    
    /**
     * Current connection state.
     */
    ConnectionState state;
    
    /**
     * Number of retry attempts since last successful connection.
     * Resets to 0 when connection is established.
     */
    int retryCount;
    
    /**
     * Timestamp of the last successful connection.
     * Null if never connected.
     */
    Instant connectedSince;
    
    /**
     * Timestamp of the last connection failure.
     * Null if no failures have occurred.
     */
    Instant lastFailureTime;
    
    /**
     * Error message from the last failure.
     * Null if no failures have occurred or if connection is healthy.
     */
    String lastFailureMessage;
    
    /**
     * Timestamp when this snapshot was created.
     */
    @Builder.Default
    Instant snapshotTime = Instant.now();
    
    /**
     * Additional metadata specific to the dependency type.
     * 
     * Examples:
     * - MySQL: {"activeConnections": 5, "idleConnections": 15, "totalConnections": 20}
     * - Redis: {"mode": "standalone", "lastPingMs": 2}
     * - Kafka: {"rebalances": 3, "lastRebalanceTime": "2025-12-20T10:24:00Z"}
     * 
     * WHY: Different dependencies have different relevant metrics.
     * Using a flexible Map allows each manager to provide context-specific data
     * without coupling the core model to dependency-specific details.
     */
    Map<String, Object> metadata;
    
    /**
     * Returns true if the dependency is in a healthy state (CONNECTED).
     */
    public boolean isHealthy() {
        return state == ConnectionState.CONNECTED;
    }
    
    /**
     * Returns true if the dependency is in a degraded state (DEGRADED).
     */
    public boolean isDegraded() {
        return state == ConnectionState.DEGRADED;
    }
    
    /**
     * Returns true if the dependency is currently unavailable (FAILED, RETRYING, DISCONNECTED).
     */
    public boolean isUnavailable() {
        return state == ConnectionState.FAILED 
            || state == ConnectionState.RETRYING 
            || state == ConnectionState.DISCONNECTED;
    }
}
