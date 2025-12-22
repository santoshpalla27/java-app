package com.sysbehavior.platform.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Kafka configuration with OpenTelemetry trace propagation.
 * 
 * Ensures trace context is propagated across Kafka producers and consumers
 * using W3C Trace Context headers.
 */
@Configuration
public class KafkaTracingConfig {
    
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    
    public KafkaTracingConfig(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("kafka-tracing");
    }
    
    /**
     * TextMapSetter for injecting trace context into Kafka headers.
     */
    private static final TextMapSetter<Headers> KAFKA_HEADER_SETTER = (headers, key, value) -> {
        if (headers != null) {
            headers.remove(key);
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    };
    
    /**
     * TextMapGetter for extracting trace context from Kafka headers.
     */
    private static final TextMapGetter<Headers> KAFKA_HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers headers) {
            return () -> headers.spliterator().asIterator().stream()
                .map(Header::key)
                .iterator();
        }
        
        @Override
        public String get(Headers headers, String key) {
            Header header = headers.lastHeader(key);
            if (header == null) {
                return null;
            }
            return new String(header.value(), StandardCharsets.UTF_8);
        }
    };
    
    /**
     * Configure Kafka listener container factory with trace propagation.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Add record interceptor for trace extraction
        factory.setRecordInterceptor(new RecordInterceptor<String, String>() {
            @Override
            public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record) {
                // Extract trace context from Kafka headers
                Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                    .extract(Context.current(), record.headers(), KAFKA_HEADER_GETTER);
                
                // Create consumer span
                Span span = tracer.spanBuilder("kafka.consume")
                    .setParent(extractedContext)
                    .setSpanKind(SpanKind.CONSUMER)
                    .setAttribute("messaging.system", "kafka")
                    .setAttribute("messaging.destination", record.topic())
                    .setAttribute("messaging.operation", "receive")
                    .setAttribute("messaging.kafka.partition", record.partition())
                    .setAttribute("messaging.kafka.offset", record.offset())
                    .startSpan();
                
                try {
                    // Make span current for downstream processing
                    return record;
                } finally {
                    span.end();
                }
            }
        });
        
        return factory;
    }
    
    /**
     * Helper method to inject trace context into producer records.
     * Call this before sending Kafka messages.
     */
    public <K, V> void injectTraceContext(ProducerRecord<K, V> record) {
        Span span = tracer.spanBuilder("kafka.send")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute("messaging.system", "kafka")
            .setAttribute("messaging.destination", record.topic())
            .setAttribute("messaging.operation", "send")
            .startSpan();
        
        try {
            // Inject current trace context into Kafka headers
            openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current().with(span), record.headers(), KAFKA_HEADER_SETTER);
        } finally {
            span.end();
        }
    }
}
