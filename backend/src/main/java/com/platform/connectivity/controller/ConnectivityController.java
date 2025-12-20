package com.platform.connectivity.controller;

import com.platform.connectivity.core.ConnectionSnapshot;
import com.platform.connectivity.core.ConnectivityRegistry;
import com.platform.connectivity.core.DependencyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal observability endpoint for connection state.
 * 
 * WHY: This endpoint exposes connection health in a structured format
 * that can be consumed by monitoring tools, dashboards, or health checks.
 * 
 * Design: Returns JSON with current state of all dependencies.
 * This is an INTERNAL endpoint - not meant for public consumption.
 * 
 * Path: /internal/connectivity
 */
@RestController
@RequestMapping("/internal/connectivity")
@Slf4j
public class ConnectivityController {
    
    private final ConnectivityRegistry registry;
    
    @Autowired
    public ConnectivityController(ConnectivityRegistry registry) {
        this.registry = registry;
    }
    
    /**
     * Get connection state for all dependencies.
     * 
     * Returns:
     * {
     *   "mysql": { "state": "CONNECTED", "retryCount": 0, ... },
     *   "redis": { "state": "RETRYING", "retryCount": 5, ... },
     *   "kafka": { "state": "DEGRADED", "retryCount": 0, ... }
     * }
     * 
     * @return Map of dependency states
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConnectivityStatus() {
        Map<String, Object> response = new HashMap<>();
        
        // Get snapshots for all dependencies
        Map<DependencyType, ConnectionSnapshot> snapshots = registry.getAllSnapshots();
        
        // Convert to response format
        for (Map.Entry<DependencyType, ConnectionSnapshot> entry : snapshots.entrySet()) {
            String key = entry.getKey().name().toLowerCase();
            ConnectionSnapshot snapshot = entry.getValue();
            
            Map<String, Object> snapshotData = new HashMap<>();
            snapshotData.put("state", snapshot.getState().name());
            snapshotData.put("retryCount", snapshot.getRetryCount());
            snapshotData.put("connectedSince", snapshot.getConnectedSince());
            snapshotData.put("lastFailureTime", snapshot.getLastFailureTime());
            snapshotData.put("lastFailureMessage", snapshot.getLastFailureMessage());
            snapshotData.put("metadata", snapshot.getMetadata());
            
            response.put(key, snapshotData);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get connection state for a specific dependency.
     * 
     * @param type Dependency type (mysql, redis, kafka)
     * @return Connection snapshot
     */
    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> getDependencyStatus(String type) {
        try {
            DependencyType dependencyType = DependencyType.valueOf(type.toUpperCase());
            ConnectionSnapshot snapshot = registry.getSnapshot(dependencyType);
            
            if (snapshot == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("state", snapshot.getState().name());
            response.put("retryCount", snapshot.getRetryCount());
            response.put("connectedSince", snapshot.getConnectedSince());
            response.put("lastFailureTime", snapshot.getLastFailureTime());
            response.put("lastFailureMessage", snapshot.getLastFailureMessage());
            response.put("metadata", snapshot.getMetadata());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
