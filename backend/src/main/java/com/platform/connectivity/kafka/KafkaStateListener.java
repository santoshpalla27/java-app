package com.platform.connectivity.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Kafka consumer rebalance listener that tracks rebalance events.
 * 
 * WHY: Consumer rebalances are a key indicator of Kafka health.
 * Frequent rebalances indicate:
 * - Consumer group instability
 * - Network issues
 * - Broker problems
 * - Consumer processing too slow
 * 
 * Design: Implements Kafka's ConsumerRebalanceListener interface.
 * Reports rebalance events to KafkaConnectionManager for state classification.
 */
@Component
@Slf4j
public class KafkaStateListener implements ConsumerRebalanceListener {
    
    private final KafkaConnectionManager connectionManager;
    
    @Autowired
    public KafkaStateListener(KafkaConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        log.info("KafkaStateListener initialized");
    }
    
    /**
     * Called when partitions are revoked from this consumer.
     * This happens at the start of a rebalance.
     * 
     * @param partitions The partitions being revoked
     */
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        log.info("Kafka partitions revoked: {} partitions", partitions.size());
        
        // Record rebalance event
        connectionManager.recordRebalance();
    }
    
    /**
     * Called when partitions are assigned to this consumer.
     * This happens at the end of a rebalance.
     * 
     * @param partitions The partitions being assigned
     */
    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        log.info("Kafka partitions assigned: {} partitions", partitions.size());
        
        // Rebalance completed successfully
        // The rebalance was already recorded in onPartitionsRevoked
    }
    
    /**
     * Called when partitions are lost (e.g., due to consumer failure).
     * This is a more severe event than normal revocation.
     * 
     * @param partitions The partitions that were lost
     */
    @Override
    public void onPartitionsLost(Collection<TopicPartition> partitions) {
        log.error("Kafka partitions lost: {} partitions", partitions.size());
        
        // Record as rebalance (indicates instability)
        connectionManager.recordRebalance();
    }
}
