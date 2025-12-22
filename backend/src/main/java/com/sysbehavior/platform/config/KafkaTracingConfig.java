package com.sysbehavior.platform.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka configuration with OpenTelemetry trace propagation.
 * 
 * Provides helper methods to inject and extract trace context
 * using W3C Trace Context headers in Kafka messages.
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
            List<String> keys = new ArrayList<>();
            for (Header header : headers) {
                keys.add(header.key());
            }
            return keys;
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
    
    /**
     * Helper method to extract trace context from Kafka headers.
     * Returns the extracted context that can be used as parent for new spans.
     */
    public Context extractTraceContext(Headers headers) {
        return openTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), headers, KAFKA_HEADER_GETTER);
    }
}
