package com.sysbehavior.platform.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysbehavior.platform.domain.ConnectivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaEventProducer {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final String TOPIC = "system.connectivity.events";

    public void sendEvent(ConnectivityEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getTarget(), payload);
            // log.info("Sent Kafka event: {}", payload);
        } catch (JsonProcessingException e) {
            log.error("Error serializing event", e);
        }
    }
}
