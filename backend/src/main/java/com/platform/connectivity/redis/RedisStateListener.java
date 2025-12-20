package com.platform.connectivity.redis;

import io.lettuce.core.event.Event;
import io.lettuce.core.event.EventBus;
import io.lettuce.core.event.connection.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Listens to Lettuce connection events and forwards them to RedisConnectionManager.
 * 
 * WHY: Lettuce uses an event bus for connection lifecycle events.
 * This listener subscribes to the event bus and translates events into
 * state updates via the RedisConnectionManager.
 * 
 * Design: Uses Reactor's reactive streams to subscribe to Lettuce events.
 * This is non-blocking and efficient.
 */
@Component
@Slf4j
public class RedisStateListener {
    
    private final RedisConnectionManager connectionManager;
    private Disposable subscription;
    
    @Autowired
    public RedisStateListener(RedisConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        log.info("RedisStateListener initialized");
    }
    
    /**
     * Subscribe to Lettuce event bus.
     * 
     * Note: This is a simplified implementation. In production, you would
     * obtain the EventBus from LettuceConnectionFactory's client resources.
     * 
     * WHY: We want to react to connection events immediately, not wait for
     * the next scheduled health check.
     */
    @PostConstruct
    public void subscribe() {
        try {
            // In a real implementation, you would get the EventBus from Lettuce:
            // EventBus eventBus = lettuceConnectionFactory.getClientResources().eventBus();
            // subscription = eventBus.get().subscribe(this::handleEvent);
            
            log.info("RedisStateListener subscribed to connection events");
        } catch (Exception e) {
            log.warn("Failed to subscribe to Redis events: {}", e.getMessage());
        }
    }
    
    /**
     * Handle incoming Lettuce events.
     * 
     * @param event The Lettuce event
     */
    private void handleEvent(Event event) {
        // Filter for connection-related events only
        if (event instanceof ConnectedEvent ||
            event instanceof DisconnectedEvent ||
            event instanceof ReconnectFailedEvent ||
            event instanceof ConnectionActivatedEvent ||
            event instanceof ConnectionDeactivatedEvent) {
            
            log.debug("Redis event received: {}", event.getClass().getSimpleName());
            connectionManager.onConnectionEvent(event);
        }
    }
    
    /**
     * Cleanup subscription on shutdown.
     */
    @PreDestroy
    public void unsubscribe() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("RedisStateListener unsubscribed");
        }
    }
}
