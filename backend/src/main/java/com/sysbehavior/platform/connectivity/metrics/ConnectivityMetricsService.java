package com.sysbehavior.platform.connectivity.metrics;

import com.sysbehavior.platform.connectivity.core.ConnectionState;
import com.sysbehavior.platform.connectivity.core.DependencyType;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for managing OpenTelemetry metrics related to dependency connectivity.
 * 
 * Exposes:
 * - dependency.state{type} - Gauge (CONNECTED=1, DEGRADED=0.5, FAILED=0)
 * - dependency.retries{type} - Counter
 * - dependency.failures{type} - Counter
 * - dependency.recovery.duration{type} - Histogram
 */
@Service
public class ConnectivityMetricsService {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectivityMetricsService.class);
    
    private final Meter meter;
    
    // State values for each dependency (for gauge callbacks)
    private final Map<DependencyType, AtomicReference<Double>> stateValues = new ConcurrentHashMap<>();
    
    // Retry counters
    private final Map<DependencyType, LongCounter> retryCounters = new ConcurrentHashMap<>();
    
    // Failure counters
    private final Map<DependencyType, LongCounter> failureCounters = new ConcurrentHashMap<>();
    
    // Recovery histograms
    private final Map<DependencyType, DoubleHistogram> recoveryHistograms = new ConcurrentHashMap<>();
    
    // Track failure start times for recovery duration calculation
    private final Map<DependencyType, Instant> failureStartTimes = new ConcurrentHashMap<>();
    
    public ConnectivityMetricsService(OpenTelemetry openTelemetry) {
        this.meter = openTelemetry.getMeter("connectivity");
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        for (DependencyType type : DependencyType.values()) {
            String typeName = type.name().toLowerCase();
            Attributes attributes = Attributes.of(AttributeKey.stringKey("type"), typeName);
            
            // Initialize state value
            AtomicReference<Double> stateValue = new AtomicReference<>(0.0);
            stateValues.put(type, stateValue);
            
            // Register state gauge with callback
            meter.gaugeBuilder("dependency.state")
                .setDescription("Current state of dependency (CONNECTED=1, DEGRADED=0.5, FAILED=0)")
                .buildWithCallback(measurement -> {
                    measurement.record(stateValue.get(), attributes);
                });
            
            // Initialize retry counter
            LongCounter retryCounter = meter.counterBuilder("dependency.retries")
                .setDescription("Total number of retry attempts for dependency")
                .build();
            retryCounters.put(type, retryCounter);
            
            // Initialize failure counter
            LongCounter failureCounter = meter.counterBuilder("dependency.failures")
                .setDescription("Total number of failures for dependency")
                .build();
            failureCounters.put(type, failureCounter);
            
            // Initialize recovery histogram
            DoubleHistogram recoveryHistogram = meter.histogramBuilder("dependency.recovery.duration")
                .setDescription("Time taken to recover from failure to connected state")
                .setUnit("s")
                .build();
            recoveryHistograms.put(type, recoveryHistogram);
            
            log.info("Initialized OpenTelemetry metrics for dependency type: {}", typeName);
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
            
            Attributes attributes = Attributes.of(AttributeKey.stringKey("type"), type.name().toLowerCase());
            recoveryHistograms.get(type).record(recoveryDuration.toMillis() / 1000.0, attributes);
            
            log.info("Recorded recovery time for {}: {} seconds", type, recoveryDuration.getSeconds());
        }
    }
    
    /**
     * Increment retry counter for a dependency.
     * 
     * @param type Dependency type
     */
    public void incrementRetry(DependencyType type) {
        Attributes attributes = Attributes.of(AttributeKey.stringKey("type"), type.name().toLowerCase());
        retryCounters.get(type).add(1, attributes);
    }
    
    /**
     * Increment failure counter for a dependency.
     * Called only when transitioning to FAILED state.
     * 
     * @param type Dependency type
     */
    public void incrementFailure(DependencyType type) {
        Attributes attributes = Attributes.of(AttributeKey.stringKey("type"), type.name().toLowerCase());
        failureCounters.get(type).add(1, attributes);
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
