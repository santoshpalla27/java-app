package com.sysbehavior.platform.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * OpenTelemetry configuration for the System Behavior Platform.
 * 
 * Configures:
 * - Metrics export to OTel Collector
 * - Distributed tracing with W3C context propagation
 * - Structured logging with trace correlation
 */
@Configuration
public class OpenTelemetryConfig {
    
    @Value("${otel.exporter.otlp.endpoint:http://otel-collector:4317}")
    private String otlpEndpoint;
    
    @Value("${spring.application.name:system-behavior-platform}")
    private String serviceName;
    
    @Value("${otel.service.version:1.0.0}")
    private String serviceVersion;
    
    @Value("${otel.deployment.environment:production}")
    private String environment;
    
    @Bean
    public OpenTelemetry openTelemetry() {
        // Define service resource attributes
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)
                .build()));
        
        // Configure Trace Provider
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .setTimeout(Duration.ofSeconds(10))
                    .build()
            ).build())
            .setResource(resource)
            .build();
        
        // Configure Meter Provider
        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
            .registerMetricReader(PeriodicMetricReader.builder(
                OtlpGrpcMetricExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .setTimeout(Duration.ofSeconds(10))
                    .build()
            ).setInterval(Duration.ofSeconds(15)).build())
            .setResource(resource)
            .build();
        
        // Configure Logger Provider
        SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(
                OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .setTimeout(Duration.ofSeconds(10))
                    .build()
            ).build())
            .setResource(resource)
            .build();
        
        // Build and register global OpenTelemetry instance
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setMeterProvider(sdkMeterProvider)
            .setLoggerProvider(sdkLoggerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sdkTracerProvider.close();
            sdkMeterProvider.close();
            sdkLoggerProvider.close();
        }));
        
        return openTelemetry;
    }
}
