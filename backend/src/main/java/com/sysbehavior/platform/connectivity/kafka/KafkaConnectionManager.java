package com.sysbehavior.platform.connectivity.kafka;

import com.sysbehavior.platform.connectivity.core.ConnectionState;
import com.sysbehavior.platform.connectivity.core.ConnectivityRegistry;
import com.sysbehavior.platform.connectivity.core.DependencyType;
import com.sysbehavior.platform.connectivity.metrics.ConnectivityMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages Kafka connection state by monitoring producer/consumer health.
 * 
 * WHY: Kafka doesn't have a simple "ping" mechanism like Redis or MySQL.
 * We infer connection state from:
 * - Producer send success/failure callbacks
 * - Consumer rebalance events
 * - Admin client cluster metadata queries
 * 
 * Design:
 * - Uses AdminClient to query cluster metadata (broker availability)
 * - Tracks producer send failures (updated by KafkaEventProducer)
 * - Tracks consumer rebalance events (updated by KafkaStateListener)
 * - Classifies state based on failure patterns
 */
@Component
@Slf4j
public class KafkaConnectionManager {
    
    private final ConnectivityRegistry registry;
    private final KafkaAdmin kafkaAdmin;
    private final ConnectivityMetricsService metricsService;
    
    // Track send failures and rebalances
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger rebalanceCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastRebalance = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastSuccessfulSend = new AtomicReference<>(null);
    
    private static final int DEGRADED_THRESHOLD = 3;
    private static final int FAILED_THRESHOLD = 7;
    private static final int REBALANCE_STORM_THRESHOLD = 5; // 5 rebalances in short time = DEGRADED
    
    @Autowired
    public KafkaConnectionManager(ConnectivityRegistry registry, KafkaAdmin kafkaAdmin,
                                   ConnectivityMetricsService metricsService) {
        this.registry = registry;
        this.kafkaAdmin = kafkaAdmin;
        this.metricsService = metricsService;
        log.info("KafkaConnectionManager initialized");
    }
    
    /**
     * Scheduled health check for Kafka connection.
     * Uses AdminClient to query cluster metadata.
     * 
     * WHY: AdminClient can query broker availability without producing/consuming messages.
     * This is a reliable way to check if Kafka is reachable.
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public void checkHealth() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // Query cluster metadata with timeout
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            
            // Wait for result with timeout
            String clusterId = clusterResult.clusterId().get(5, TimeUnit.SECONDS);
            int nodeCount = clusterResult.nodes().get(5, TimeUnit.SECONDS).size();
            
            log.debug("Kafka cluster accessible: {} ({} nodes)", clusterId, nodeCount);
            handleSuccess(nodeCount);
            
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            handleFailure(e.getMessage());
        }
        
        // Update metadata
        updateMetadata();
    }
    
    /**
     * Handle successful connection to Kafka.
     */
    private void handleSuccess(int nodeCount) {
        int failures = consecutiveFailures.getAndSet(0);
        if (failures > 0) {
            log.info("Kafka connection recovered after {} failures", failures);
        }
        
        // Check for rebalance storm (indicates DEGRADED state)
        if (isRebalanceStorm()) {
            registry.updateState(
                DependencyType.KAFKA, 
                ConnectionState.DEGRADED, 
                String.format("Frequent rebalances detected (%d recent)", rebalanceCount.get())
            );
        } else {
            registry.updateState(DependencyType.KAFKA, ConnectionState.CONNECTED, null);
        }
        
        // Update metadata with node count
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("brokerCount", nodeCount);
        registry.updateMetadata(DependencyType.KAFKA, metadata);
    }
    
    /**
     * Handle Kafka connection failure.
     */
    private void handleFailure(String errorMessage) {
        int failures = consecutiveFailures.incrementAndGet();
        
        // Increment retry counter for Prometheus
        metricsService.incrementRetry(DependencyType.KAFKA);
        
        ConnectionState newState;
        if (failures >= FAILED_THRESHOLD) {
            newState = ConnectionState.FAILED;
        } else if (failures >= DEGRADED_THRESHOLD) {
            newState = ConnectionState.DEGRADED;
        } else {
            newState = ConnectionState.RETRYING;
        }
        
        log.warn("Kafka connection failure #{}: {}", failures, errorMessage);
        registry.updateState(DependencyType.KAFKA, newState, errorMessage);
    }
    
    /**
     * Check if we're experiencing a rebalance storm.
     * 
     * WHY: Frequent rebalances indicate instability (consumer group issues, network problems).
     * This is a DEGRADED state - Kafka is up but not healthy.
     */
    private boolean isRebalanceStorm() {
        int rebalances = rebalanceCount.get();
        Instant lastRebalanceTime = lastRebalance.get();
        
        if (rebalances >= REBALANCE_STORM_THRESHOLD && lastRebalanceTime != null) {
            // Check if rebalances happened recently (within last 5 minutes)
            long secondsSinceLastRebalance = Instant.now().getEpochSecond() - lastRebalanceTime.getEpochSecond();
            return secondsSinceLastRebalance < 300; // 5 minutes
        }
        
        return false;
    }
    
    /**
     * Record a producer send success.
     * Called by KafkaEventProducer after successful send.
     */
    public void recordSendSuccess() {
        lastSuccessfulSend.set(Instant.now());
        consecutiveFailures.set(0);
    }
    
    /**
     * Record a producer send failure.
     * Called by KafkaEventProducer after failed send.
     */
    public void recordSendFailure(String errorMessage) {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("Kafka producer send failure #{}: {}", failures, errorMessage);
        
        // Update state if failures exceed threshold
        if (failures >= DEGRADED_THRESHOLD) {
            ConnectionState newState = failures >= FAILED_THRESHOLD 
                ? ConnectionState.FAILED 
                : ConnectionState.DEGRADED;
            registry.updateState(DependencyType.KAFKA, newState, errorMessage);
        }
    }
    
    /**
     * Record a consumer rebalance event.
     * Called by KafkaStateListener when rebalance occurs.
     */
    public void recordRebalance() {
        int rebalances = rebalanceCount.incrementAndGet();
        lastRebalance.set(Instant.now());
        log.info("Kafka consumer rebalance #{}", rebalances);
        
        // Check for rebalance storm
        if (isRebalanceStorm()) {
            registry.updateState(
                DependencyType.KAFKA, 
                ConnectionState.DEGRADED, 
                String.format("Rebalance storm detected (%d rebalances)", rebalances)
            );
        }
    }
    
    /**
     * Reset rebalance counter.
     * Called periodically to prevent old rebalances from affecting current state.
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void resetRebalanceCounter() {
        int oldCount = rebalanceCount.getAndSet(0);
        if (oldCount > 0) {
            log.debug("Reset Kafka rebalance counter (was {})", oldCount);
        }
    }
    
    /**
     * Update Kafka metadata in registry.
     */
    private void updateMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("rebalances", rebalanceCount.get());
        
        Instant lastRebalanceTime = lastRebalance.get();
        if (lastRebalanceTime != null) {
            metadata.put("lastRebalanceTime", lastRebalanceTime.toString());
        }
        
        Instant lastSendTime = lastSuccessfulSend.get();
        if (lastSendTime != null) {
            metadata.put("lastSuccessfulSendTime", lastSendTime.toString());
        }
        
        registry.updateMetadata(DependencyType.KAFKA, metadata);
    }
    
    /**
     * Manually trigger a health check.
     */
    public void triggerHealthCheck() {
        checkHealth();
    }
}
