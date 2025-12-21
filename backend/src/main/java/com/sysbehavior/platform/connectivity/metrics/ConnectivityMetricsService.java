package com.sysbehavior.platform.connectivity.metrics;

import com.sysbehavior.platform.connectivity.core.ConnectionState;
import com.sysbehavior.platform.connectivity.core.DependencyType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for managing Prometheus metrics related to dependency connectivity.
 * 
 * Exposes:
 * - dependency_state{type} - Gauge (CONNECTED=1, DEGRADED=0.5, FAILED=0)
 * - dependency_retries_total{type} - Counter
 * - dependency_failures_total{type} - Counter
 * - dependency_recovery_seconds{type} - Timer
 */
@Service
public class ConnectivityMetricsService {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectivityMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    
    // State gauges for each dependency
    private final Map<DependencyType, AtomicReference<Double>> stateValues = new ConcurrentHashMap<>();
    
    // Retry counters
    private final Map<DependencyType, Counter> retryCounters = new ConcurrentHashMap<>();
    
    // Failure counters
    private final Map<DependencyType, Counter> failureCounters = new ConcurrentHashMap<>();
    
    // Recovery timers
    private final Map<DependencyType, Timer> recoveryTimers = new ConcurrentHashMap<>();
    
    // Track failure start times for recovery duration calculation
    private final Map<DependencyType, Instant> failureStartTimes = new ConcurrentHashMap<>();
    
    public ConnectivityMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        for (DependencyType type : DependencyType.values()) {
            String typeName = type.name().toLowerCase();
            
            // Initialize state gauge
            AtomicReference<Double> stateValue = new AtomicReference<>(0.0);
            stateValues.put(type, stateValue);
            
            Gauge.builder("dependency_state", stateValue, AtomicReference::get)
                    .tag("type", typeName)
                    .description("Current state of dependency (CONNECTED=1, DEGRADED=0.5, FAILED=0)")
                    .register(meterRegistry);
            
            // Initialize retry counter
            Counter retryCounter = Counter.builder("dependency_retries_total")
                    .tag("type", typeName)
                    .description("Total number of retry attempts for dependency")
                    .register(meterRegistry);
            retryCounters.put(type, retryCounter);
            
            // Initialize failure counter
            Counter failureCounter = Counter.builder("dependency_failures_total")
                    .tag("type", typeName)
                    .description("Total number of failures for dependency")
                    .register(meterRegistry);
            failureCounters.put(type, failureCounter);
            
            // Initialize recovery timer
            Timer recoveryTimer = Timer.builder("dependency_recovery_seconds")
                    .tag("type", typeName)
                    .description("Time taken to recover from failure to connected state")
                    .register(meterRegistry);
            recoveryTimers.put(type, recoveryTimer);
            
            log.info("Initialized metrics for dependency type: {}", typeName);
        }
    }
    
    /**
     * Update the state gauge for a dependency.
     * 
     * @param type Dependency type
     * @param state Current connection state
     */
    public void updateState(DependencyType type, ConnectionState state) {
        double value = stateToValue(state);
        stateValues.get(type).set(value);
        
        // Track failure start time
        if (state == ConnectionState.FAILED || state == ConnectionState.RETRYING) {
            failureStartTimes.putIfAbsent(type, Instant.now());
        }
        
        // Record recovery time if transitioning to CONNECTED from failure
        if (state == ConnectionState.CONNECTED && failureStartTimes.containsKey(type)) {
            Instant failureStart = failureStartTimes.remove(type);
            Duration recoveryDuration = Duration.between(failureStart, Instant.now());
            recoveryTimers.get(type).record(recoveryDuration);
            log.info("Recorded recovery time for {}: {} seconds", type, recoveryDuration.getSeconds());
        }
    }
    
    /**
     * Increment retry counter for a dependency.
     * 
     * @param type Dependency type
     */
    public void incrementRetry(DependencyType type) {
        retryCounters.get(type).increment();
    }
    
    /**
     * Increment failure counter for a dependency.
     * Called only when transitioning to FAILED state.
     * 
     * @param type Dependency type
     */
    public void incrementFailure(DependencyType type) {
        failureCounters.get(type).increment();
        log.warn("Failure count incremented for {}", type);
    }
    
    /**
     * Convert ConnectionState to numeric value for Prometheus gauge.
     * 
     * @param state Connection state
     * @return Numeric value (CONNECTED=1.0, DEGRADED=0.5, others=0.0)
     */
    private double stateToValue(ConnectionState state) {
        return switch (state) {
            case CONNECTED -> 1.0;
            case DEGRADED -> 0.5;
            case RETRYING, FAILED, DISCONNECTED -> 0.0;
            default -> 0.0;
        };
    }
}
