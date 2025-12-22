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
import java.util.concurrent.atomic.AtomicLong;

/**
 * SRE-grade metrics service for dependency health monitoring.
 * 
 * Follows RED (Rate, Errors, Duration) methodology:
 * - dependency_state: Current health (1=UP, 0=DOWN) - USE metric
 * - dependency_latency_ms: Connection latency - Duration metric
 * - dependency_retry_total: Retry attempts - Error metric
 * - dependency_failure_total: State transitions to FAILED - Error metric
 * - dependency_recovery_seconds: Time to recover from failure - Duration metric
 * 
 * WHY Micrometer instead of OpenTelemetry Meter API:
 * - Native Spring Boot integration
 * - Proven compatibility with Micrometer OTLP registry
 * - Industry standard for JVM applications
 * - Better cardinality control
 */
@Service
public class ConnectivityMetricsService {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectivityMetricsService.class);
    
    private final MeterRegistry registry;
    
    // State values for gauges (1=UP, 0=DOWN)
    private final Map<DependencyType, AtomicLong> stateValues = new ConcurrentHashMap<>();
    
    // Latency tracking (milliseconds)
    private final Map<DependencyType, AtomicLong> latencyValues = new ConcurrentHashMap<>();
    
    // Retry counters
    private final Map<DependencyType, Counter> retryCounters = new ConcurrentHashMap<>();
    
    // Failure counters
    private final Map<DependencyType, Counter> failureCounters = new ConcurrentHashMap<>();
    
    // Recovery timers
    private final Map<DependencyType, Timer> recoveryTimers = new ConcurrentHashMap<>();
    
    // Track failure start times for recovery duration
    private final Map<DependencyType, Instant> failureStartTimes = new ConcurrentHashMap<>();
    
    public ConnectivityMetricsService(MeterRegistry registry) {
        this.registry = registry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        for (DependencyType type : DependencyType.values()) {
            String typeName = type.name().toLowerCase();
            
            // Initialize state value (default to DOWN until first health check)
            AtomicLong stateValue = new AtomicLong(0);
            stateValues.put(type, stateValue);
            
            // Register gauge for dependency state
            Gauge.builder("dependency_state", stateValue, AtomicLong::get)
                .description("Dependency health state (1=UP, 0=DOWN)")
                .tag("dependency", typeName)
                .tag("type", typeName) // Backward compatibility
                .register(registry);
            
            // Initialize latency value
            AtomicLong latencyValue = new AtomicLong(0);
            latencyValues.put(type, latencyValue);
            
            // Register gauge for dependency latency
            Gauge.builder("dependency_latency_ms", latencyValue, AtomicLong::get)
                .description("Dependency connection latency in milliseconds")
                .tag("dependency", typeName)
                .baseUnit("milliseconds")
                .register(registry);
            
            // Register retry counter
            Counter retryCounter = Counter.builder("dependency_retry_total")
                .description("Total number of connection retry attempts")
                .tag("dependency", typeName)
                .register(registry);
            retryCounters.put(type, retryCounter);
            
            // Register failure counter
            Counter failureCounter = Counter.builder("dependency_failure_total")
                .description("Total number of transitions to FAILED state")
                .tag("dependency", typeName)
                .register(registry);
            failureCounters.put(type, failureCounter);
            
            // Register recovery timer
            Timer recoveryTimer = Timer.builder("dependency_recovery_seconds")
                .description("Time taken to recover from FAILED to CONNECTED state")
                .tag("dependency", typeName)
                .register(registry);
            recoveryTimers.put(type, recoveryTimer);
            
            log.info("Initialized Micrometer metrics for dependency: {}", typeName);
        }
    }
    
    /**
     * Update dependency state metric.
     * WHY: Provides instant visibility into dependency health.
     */
    public void updateState(DependencyType type, ConnectionState state) {
        long value = stateToValue(state);
        stateValues.get(type).set(value);
        
        // Track failure start time
        if (state == ConnectionState.FAILED || state == ConnectionState.RETRYING) {
            failureStartTimes.putIfAbsent(type, Instant.now());
        }
        
        // Record recovery duration
        if (state == ConnectionState.CONNECTED && failureStartTimes.containsKey(type)) {
            Instant failureStart = failureStartTimes.remove(type);
            Duration recoveryDuration = Duration.between(failureStart, Instant.now());
            recoveryTimers.get(type).record(recoveryDuration);
            log.info("Dependency {} recovered in {} seconds", type, recoveryDuration.getSeconds());
        }
    }
    
    /**
     * Record connection latency.
     * WHY: Leading indicator for dependency degradation.
     */
    public void recordLatency(DependencyType type, long latencyMs) {
        latencyValues.get(type).set(latencyMs);
    }
    
    /**
     * Increment retry counter.
     * WHY: Tracks connection instability and retry storms.
     */
    public void incrementRetry(DependencyType type) {
        retryCounters.get(type).increment();
    }
    
    /**
     * Increment failure counter.
     * WHY: Tracks state transitions to FAILED for alerting.
     */
    public void incrementFailure(DependencyType type) {
        failureCounters.get(type).increment();
        log.warn("Failure count incremented for {}", type);
    }
    
    /**
     * Convert ConnectionState to numeric value for Prometheus gauge.
     * WHY: Numeric values enable threshold-based alerting.
     */
    private long stateToValue(ConnectionState state) {
        return switch (state) {
            case CONNECTED -> 1;
            case DEGRADED -> 0; // Treat degraded as down for alerting
            case RETRYING, FAILED, DISCONNECTED -> 0;
            default -> 0;
        };
    }
}
