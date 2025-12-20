package com.sysbehavior.platform.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysbehavior.platform.connectivity.kafka.KafkaConnectionManager;
import com.sysbehavior.platform.domain.ConnectivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class KafkaEventProducer {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private KafkaConnectionManager kafkaConnectionManager;

    private static final String TOPIC = "system.connectivity.events";

    public void sendEvent(ConnectivityEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            // Send asynchronously and handle result
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(TOPIC, event.getTarget(), payload);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Sent Kafka event: {}", payload);
                    // Record successful send
                    if (kafkaConnectionManager != null) {
                        kafkaConnectionManager.recordSendSuccess();
                    }
                } else {
                    log.error("Failed to send Kafka event", ex);
                    // Record send failure
                    if (kafkaConnectionManager != null) {
                        kafkaConnectionManager.recordSendFailure(ex.getMessage());
                    }
                }
            });
            
        } catch (JsonProcessingException e) {
            log.error("Error serializing event", e);
            if (kafkaConnectionManager != null) {
                kafkaConnectionManager.recordSendFailure("Serialization error: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error sending Kafka event", e);
            if (kafkaConnectionManager != null) {
                kafkaConnectionManager.recordSendFailure(e.getMessage());
            }
        }
    }
}
