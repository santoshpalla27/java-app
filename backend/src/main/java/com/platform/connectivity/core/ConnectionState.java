package com.platform.connectivity.core;

/**
 * Represents the connection state of a dependency.
 * 
 * State Transitions:
 * DISCONNECTED → CONNECTING → CONNECTED (successful connection)
 * CONNECTING → RETRYING (connection failed, will retry)
 * CONNECTED → DEGRADED (experiencing errors but still functional)
 * CONNECTED → RETRYING (connection lost)
 * DEGRADED → CONNECTED (recovered from errors)
 * DEGRADED → RETRYING (connection lost)
 * RETRYING → CONNECTING (attempting reconnection)
 * RETRYING → FAILED (max retries exceeded or persistent failure)
 * FAILED → RETRYING (background retry continues)
 * 
 * WHY: This state machine makes connection health explicit and observable.
 * It allows the application to differentiate between "never connected" (DISCONNECTED),
 * "trying to connect" (CONNECTING), "working but slow" (DEGRADED), and "down but retrying" (RETRYING).
 */
public enum ConnectionState {
    /**
     * Initial state. No connection has been established yet.
     * This is the state at application startup before any connection attempt.
     */
    DISCONNECTED,
    
    /**
     * Actively attempting to establish a connection.
     * Transition from DISCONNECTED or RETRYING.
     */
    CONNECTING,
    
    /**
     * Connection is established and fully operational.
     * All operations are succeeding.
     */
    CONNECTED,
    
    /**
     * Connection exists but is experiencing issues.
     * Examples: slow queries, intermittent timeouts, high error rate.
     * The dependency is usable but not healthy.
     */
    DEGRADED,
    
    /**
     * Connection failed and is being retried with backoff.
     * This is a transient state during reconnection attempts.
     */
    RETRYING,
    
    /**
     * Connection has failed.
     * Background retries continue indefinitely, but current state is failed.
     * This indicates persistent inability to connect.
     */
    FAILED
}
