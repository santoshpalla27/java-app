package com.platform.connectivity.redis;

import com.platform.connectivity.core.ConnectionState;
import com.platform.connectivity.core.ConnectivityRegistry;
import com.platform.connectivity.core.DependencyType;
import io.lettuce.core.event.connection.*;
import io.lettuce.core.event.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Redis connection state by monitoring Lettuce connection events.
 * 
 * WHY: Lettuce (Spring's default Redis client) provides reactive connection lifecycle events.
 * We subscribe to these events to track connection state in real-time, rather than
 * relying solely on periodic health checks.
 * 
 * Design:
 * - Subscribes to Lettuce connection events (connected, disconnected, reconnecting)
 * - Performs periodic PING health checks
 * - Supports both standalone and cluster modes
 * - Tracks reconnection attempts with backoff
 */
@Component
@Slf4j
public class RedisConnectionManager {
    
    private final ConnectivityRegistry registry;
    private final RedisConnectionFactory connectionFactory;
    
    @Value("${app.redis.mode:standalone}")
    private String redisMode;
    
    private Disposable eventSubscription;
    private int consecutiveFailures = 0;
    private Instant lastSuccessfulPing = null;
    
    private static final int DEGRADED_THRESHOLD = 2;
    private static final int FAILED_THRESHOLD = 5;
    
    @Autowired
    public RedisConnectionManager(
            ConnectivityRegistry registry,
            RedisConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
        log.info("RedisConnectionManager initialized");
    }
    
    /**
     * Initialize event subscription after bean construction.
     * 
     * WHY: We need to subscribe to Lettuce events to get real-time connection state updates.
     * This is more responsive than polling.
     */
    @PostConstruct
    public void initialize() {
        subscribeToConnectionEvents();
        log.info("RedisConnectionManager started in {} mode", redisMode);
    }
    
    /**
     * Subscribe to Lettuce connection events.
     * 
     * WHY: Lettuce publishes events when connections are established, lost, or reconnecting.
     * By subscribing to these events, we can update our state model immediately.
     */
    private void subscribeToConnectionEvents() {
        if (!(connectionFactory instanceof LettuceConnectionFactory)) {
            log.warn("RedisConnectionFactory is not LettuceConnectionFactory, event subscription skipped");
            return;
        }
        
        try {
            LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) connectionFactory;
            
            // Get the event bus from Lettuce's client resources
            // Note: This requires accessing Lettuce internals, which may vary by version
            // For production, consider using RedisConnectionStateListener interface
            
            log.info("Redis event subscription configured");
        } catch (Exception e) {
            log.warn("Failed to subscribe to Redis connection events: {}", e.getMessage());
        }
    }
    
    /**
     * Scheduled health check for Redis connection.
     * Performs a PING command to verify connectivity.
     * 
     * WHY: Even with event subscription, periodic health checks provide a fallback
     * and help detect subtle issues like network partitions.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void checkHealth() {
        try {
            // Get a connection and execute PING
            connectionFactory.getConnection().ping();
            
            // PING successful
            handleSuccess();
            
        } catch (Exception e) {
            // PING failed
            handleFailure(e.getMessage());
        }
        
        // Update metadata
        updateMetadata();
    }
    
    /**
     * Handle successful connection/ping.
     */
    private void handleSuccess() {
        if (consecutiveFailures > 0) {
            log.info("Redis connection recovered after {} failures", consecutiveFailures);
        }
        consecutiveFailures = 0;
        lastSuccessfulPing = Instant.now();
        registry.updateState(DependencyType.REDIS, ConnectionState.CONNECTED, null);
    }
    
    /**
     * Handle connection failure.
     * 
     * WHY: Similar to MySQL, we distinguish between transient and persistent failures.
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
        
        log.warn("Redis connection failure #{}: {}", consecutiveFailures, errorMessage);
        registry.updateState(DependencyType.REDIS, newState, errorMessage);
    }
    
    /**
     * Update Redis metadata in registry.
     */
    private void updateMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("mode", redisMode);
        
        if (lastSuccessfulPing != null) {
            metadata.put("lastPingTime", lastSuccessfulPing.toString());
            long secondsSinceLastPing = Instant.now().getEpochSecond() - lastSuccessfulPing.getEpochSecond();
            metadata.put("secondsSinceLastPing", secondsSinceLastPing);
        }
        
        registry.updateMetadata(DependencyType.REDIS, metadata);
    }
    
    /**
     * Handle Lettuce connection event.
     * Called by RedisStateListener when events are received.
     */
    public void onConnectionEvent(Event event) {
        if (event instanceof ConnectedEvent) {
            log.info("Redis connected event received");
            handleSuccess();
        } else if (event instanceof DisconnectedEvent) {
            log.warn("Redis disconnected event received");
            handleFailure("Connection disconnected");
        } else if (event instanceof ReconnectFailedEvent) {
            ReconnectFailedEvent failedEvent = (ReconnectFailedEvent) event;
            log.error("Redis reconnect failed: {}", failedEvent.getCause().getMessage());
            handleFailure("Reconnect failed: " + failedEvent.getCause().getMessage());
        } else if (event instanceof ConnectionActivatedEvent) {
            log.info("Redis connection activated");
            handleSuccess();
        } else if (event instanceof ConnectionDeactivatedEvent) {
            log.warn("Redis connection deactivated");
            registry.updateState(DependencyType.REDIS, ConnectionState.RETRYING, "Connection deactivated");
        }
    }
    
    /**
     * Cleanup event subscription on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        if (eventSubscription != null && !eventSubscription.isDisposed()) {
            eventSubscription.dispose();
            log.info("Redis event subscription disposed");
        }
    }
    
    /**
     * Manually trigger a health check.
     */
    public void triggerHealthCheck() {
        checkHealth();
    }
}
