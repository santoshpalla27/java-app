package com.sysbehavior.platform.connectivity.core;

import com.sysbehavior.platform.connectivity.metrics.ConnectivityMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Centralized registry for tracking connection state of all dependencies.
 * 
 * WHY: A single source of truth for connection state prevents inconsistencies
 * and provides a clean API for both updating state (from managers) and
 * reading state (from observability endpoints).
 * 
 * Thread Safety: Uses ConcurrentHashMap and atomic references for lock-free reads.
 * State updates are synchronized per-dependency to ensure consistent transitions.
 */
@Service
@Slf4j
public class ConnectivityRegistry {
    
    /**
     * Holds the current state for each dependency.
     * Key: DependencyType
     * Value: DependencyState (internal mutable state holder)
     */
    private final Map<DependencyType, DependencyState> states = new ConcurrentHashMap<>();
    private final ConnectivityMetricsService metricsService;
    
    /**
     * Initialize registry with all dependencies in DISCONNECTED state.
     */
    @Autowired
    public ConnectivityRegistry(ConnectivityMetricsService metricsService) {
        this.metricsService = metricsService;
        for (DependencyType type : DependencyType.values()) {
            states.put(type, new DependencyState(type));
        }
        log.info("ConnectivityRegistry initialized with {} dependencies", DependencyType.values().length);
    }
    
    /**
     * Update the connection state for a dependency.
     * 
     * @param type The dependency type
     * @param newState The new connection state
     * @param errorMessage Optional error message (null if no error)
     */
    public void updateState(DependencyType type, ConnectionState newState, String errorMessage) {
        DependencyState depState = states.get(type);
        if (depState == null) {
            log.warn("Attempted to update state for unknown dependency: {}", type);
            return;
        }
        
        synchronized (depState) {
            ConnectionState oldState = depState.state.get();
            
            // Only log if state actually changed
            if (oldState != newState) {
                log.info("Dependency {} state transition: {} → {}", type, oldState, newState);
                depState.state.set(newState);
                
                // Update Prometheus metrics
                metricsService.updateState(type, newState);
                
                // Increment failure counter if transitioning to FAILED
                if (newState == ConnectionState.FAILED) {
                    metricsService.incrementFailure(type);
                }
                
                // Update timestamps based on new state
                if (newState == ConnectionState.CONNECTED) {
                    depState.connectedSince.set(Instant.now());
                    depState.retryCount.set(0); // Reset retry count on successful connection
                    depState.lastFailureMessage.set(null); // Clear error on success
                } else if (newState == ConnectionState.FAILED || newState == ConnectionState.RETRYING) {
                    depState.lastFailureTime.set(Instant.now());
                    depState.lastFailureMessage.set(errorMessage);
                    depState.retryCount.incrementAndGet();
                }
            } else if (errorMessage != null && !errorMessage.equals(depState.lastFailureMessage.get())) {
                // Same state but new error message (e.g., DEGRADED with different errors)
                depState.lastFailureMessage.set(errorMessage);
                depState.lastFailureTime.set(Instant.now());
            }
        }
    }
    
    /**
     * Update metadata for a dependency without changing state.
     * 
     * @param type The dependency type
     * @param metadata New metadata to merge with existing metadata
     */
    public void updateMetadata(DependencyType type, Map<String, Object> metadata) {
        DependencyState depState = states.get(type);
        if (depState == null) {
            log.warn("Attempted to update metadata for unknown dependency: {}", type);
            return;
        }
        
        synchronized (depState) {
            depState.metadata.putAll(metadata);
        }
    }
    
    /**
     * Get an immutable snapshot of a dependency's current state.
     * 
     * @param type The dependency type
     * @return Immutable ConnectionSnapshot
     */
    public ConnectionSnapshot getSnapshot(DependencyType type) {
        DependencyState depState = states.get(type);
        if (depState == null) {
            log.warn("Attempted to get snapshot for unknown dependency: {}", type);
            return null;
        }
        
        synchronized (depState) {
            return ConnectionSnapshot.builder()
                    .dependencyType(type)
                    .state(depState.state.get())
                    .retryCount(depState.retryCount.get())
                    .connectedSince(depState.connectedSince.get())
                    .lastFailureTime(depState.lastFailureTime.get())
                    .lastFailureMessage(depState.lastFailureMessage.get())
                    .metadata(Map.copyOf(depState.metadata)) // Defensive copy
                    .snapshotTime(Instant.now())
                    .build();
        }
    }
    
    /**
     * Get snapshots for all dependencies.
     * 
     * @return Map of DependencyType to ConnectionSnapshot
     */
    public Map<DependencyType, ConnectionSnapshot> getAllSnapshots() {
        Map<DependencyType, ConnectionSnapshot> snapshots = new ConcurrentHashMap<>();
        for (DependencyType type : DependencyType.values()) {
            snapshots.put(type, getSnapshot(type));
        }
        return snapshots;
    }
    
    /**
     * Increment retry count for a dependency.
     * Used when a retry attempt is made.
     * 
     * @param type The dependency type
     */
    public void incrementRetryCount(DependencyType type) {
        DependencyState depState = states.get(type);
        if (depState != null) {
            depState.retryCount.incrementAndGet();
        }
    }
    
    /**
     * Internal mutable state holder for a single dependency.
     * 
     * WHY: Encapsulates mutable state with atomic references for thread-safe updates.
     * This class is never exposed outside the registry.
     */
    private static class DependencyState {
        final DependencyType type;
        final AtomicReference<ConnectionState> state;
        final AtomicInteger retryCount;
        final AtomicReference<Instant> connectedSince;
        final AtomicReference<Instant> lastFailureTime;
        final AtomicReference<String> lastFailureMessage;
        final Map<String, Object> metadata;
        
        DependencyState(DependencyType type) {
            this.type = type;
            this.state = new AtomicReference<>(ConnectionState.DISCONNECTED);
            this.retryCount = new AtomicInteger(0);
            this.connectedSince = new AtomicReference<>(null);
            this.lastFailureTime = new AtomicReference<>(null);
            this.lastFailureMessage = new AtomicReference<>(null);
            this.metadata = new ConcurrentHashMap<>();
        }
    }
}
