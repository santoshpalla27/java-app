package com.sysbehavior.platform.events;

import com.platform.connectivity.kafka.KafkaStateListener;
import com.sysbehavior.platform.domain.ConnectivityEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaEventConsumer {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private KafkaStateListener kafkaStateListener;

    @KafkaListener(
        topics = "system.connectivity.events", 
        groupId = "sys-platform-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String payload) {
        log.info("Consumed Kafka event: {}", payload);
        try {
            // Forward directly to WebSocket subscribers
            // We could parse it to validate, but forwarding raw JSON is faster for now
            // But let's parse to Object to ensure structure if needed, or just pass calls.
            // Client expects structured JSON.
            ConnectivityEvent event = objectMapper.readValue(payload, ConnectivityEvent.class);
            messagingTemplate.convertAndSend("/topic/events", event);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse event", e);
        }
    }
}
