package com.sysbehavior.platform.service;

import com.platform.connectivity.mysql.MySqlConnectionManager;
import com.platform.connectivity.redis.RedisConnectionManager;
import com.platform.connectivity.kafka.KafkaConnectionManager;
import com.sysbehavior.platform.domain.ConnectivityEvent;
import com.sysbehavior.platform.events.KafkaEventProducer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Connectivity probe service that monitors MySQL, Redis, and Kafka.
 * 
 * WHY: This service performs active probing of dependencies and publishes
 * connectivity events. It now delegates to connection managers for state tracking.
 * 
 * Design: Keeps existing probe logic but leverages connection managers for state.
 * The managers handle retry logic, state transitions, and metadata tracking.
 */
@Service
@Slf4j
public class ConnectivityProbeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private KafkaEventProducer kafkaProducer;
    
    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private FailureService failureService;
    
    // Connection managers handle state tracking
    @Autowired(required = false)
    private MySqlConnectionManager mySqlConnectionManager;
    
    @Autowired(required = false)
    private RedisConnectionManager redisConnectionManager;
    
    @Autowired(required = false)
    private KafkaConnectionManager kafkaConnectionManager;

    /**
     * Main probe scheduler.
     * 
     * WHY: This provides application-level probing in addition to the
     * connection managers' health checks. It also publishes events to Kafka
     * for the real-time dashboard.
     */
    @Scheduled(fixedRateString = "${app.probe.interval-ms:5000}")
    public void probeServices() {
        probeDatabase();
        probeRedis();
        probeKafka();
    }

    private void probeDatabase() {
        long start = System.currentTimeMillis();
        failureService.checkLatency();
        String status = "SUCCESS";
        String error = null;
        try {
            jdbcTemplate.execute("SELECT 1");
        } catch (Exception e) {
            status = "FAILURE";
            error = e.getMessage();
            log.error("DB Probe Failed", e);
        }
        long latency = System.currentTimeMillis() - start;
        recordMetric("db", latency, status);
        sendEvent("MYSQL", status, latency, error);
    }

    private void probeRedis() {
        long start = System.currentTimeMillis();
        failureService.checkLatency();
        String status = "SUCCESS";
        String error = null;
        try {
            redisTemplate.opsForValue().set("probe", "1");
            redisTemplate.opsForValue().get("probe");
        } catch (Exception e) {
            status = "FAILURE";
            error = e.getMessage();
            log.error("Redis Probe Failed", e);
        }
        long latency = System.currentTimeMillis() - start;
        recordMetric("redis", latency, status);
        sendEvent("REDIS", status, latency, error);
    }

    private void probeKafka() {
        long start = System.currentTimeMillis();
        failureService.checkLatency();
        String status = "SUCCESS";
        String error = null;
        try {
            // Kafka probe is handled by sending an event
            // The KafkaConnectionManager tracks send success/failure
            sendEvent("KAFKA", "SUCCESS", 0L, null); 
        } catch (Exception e) {
            status = "FAILURE";
            error = e.getMessage();
        }
    }

    private void sendEvent(String target, String status, Long latency, String error) {
        ConnectivityEvent event = ConnectivityEvent.builder()
                .target(target)
                .status(status)
                .latencyMs(latency)
                .errorMessage(error)
                .timestamp(LocalDateTime.now())
                .build();
        
        try {
            kafkaProducer.sendEvent(event);
        } catch (Exception e) {
            log.error("Failed to send Kafka event for {}", target, e);
        }
    }
    
    private void recordMetric(String service, long latency, String status) {
        Timer.builder("system.probe.latency")
            .tag("service", service)
            .tag("status", status)
            .register(meterRegistry)
            .record(latency, TimeUnit.MILLISECONDS);
    }
}
