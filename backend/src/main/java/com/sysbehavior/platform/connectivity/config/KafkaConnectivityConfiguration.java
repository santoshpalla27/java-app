package com.sysbehavior.platform.connectivity.config;

import com.sysbehavior.platform.connectivity.kafka.KafkaStateListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Kafka configuration for connection monitoring.
 * 
 * WHY: We need to register our KafkaStateListener as a rebalance listener
 * so it can track consumer rebalance events.
 */
@Configuration
@Slf4j
public class KafkaConnectivityConfiguration {
    
    @Autowired(required = false)
    private KafkaStateListener kafkaStateListener;
    
    /**
     * Configure Kafka listener container factory with rebalance listener.
     * 
     * @param consumerFactory The consumer factory from Spring Boot auto-configuration
     * @return Configured container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Register rebalance listener if available
        if (kafkaStateListener != null) {
            factory.getContainerProperties().setConsumerRebalanceListener(kafkaStateListener);
            log.info("Registered KafkaStateListener as rebalance listener");
        }
        
        return factory;
    }
}
