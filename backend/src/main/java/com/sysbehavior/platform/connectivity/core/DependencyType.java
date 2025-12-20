package com.sysbehavior.platform.connectivity.core;

/**
 * Identifies the type of external dependency.
 * 
 * WHY: This enum provides type-safe identification of dependencies
 * and allows the ConnectivityRegistry to maintain separate state for each.
 */
public enum DependencyType {
    /**
     * MySQL database connection (via HikariCP).
     * Includes RDS, Aurora, local MySQL, or any MySQL-compatible database.
     */
    MYSQL,
    
    /**
     * Redis cache/pub-sub connection (via Lettuce).
     * Includes standalone Redis, Redis Cluster, ElastiCache, or any Redis-compatible service.
     */
    REDIS,
    
    /**
     * Kafka event streaming connection.
     * Includes local Kafka, MSK, Confluent Cloud, or any Kafka-compatible broker.
     */
    KAFKA
}
