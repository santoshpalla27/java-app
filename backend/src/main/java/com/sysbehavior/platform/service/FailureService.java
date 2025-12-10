package com.sysbehavior.platform.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FailureService {

    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private boolean latencyEnabled = false;
    private long latencyMs = 0;

    // Maintain a list of held connections to simulate pool exhaustion
    private final List<Connection> heldConnections = new ArrayList<>();

    public void exhaustDbPool() {
        log.warn("Simulating DB Pool Exhaustion...");
        try {
            // Usually pool size is 20 (from app.yml)
            for (int i = 0; i < 25; i++) {
                try {
                    Connection conn = dataSource.getConnection();
                    heldConnections.add(conn);
                } catch (SQLException e) {
                    log.error("Pool likely exhausted already: {}", e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error exhausting pool", e);
        }
    }

    public void releaseDbPool() {
        log.info("Releasing DB connections...");
        for (Connection conn : heldConnections) {
            try {
                conn.close();
            } catch (SQLException e) {
                // ignore
            }
        }
        heldConnections.clear();
    }

    public void killDbConnections() {
        log.warn("Killing DB Connections...");
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();
        }
    }

    public void setLatency(long ms) {
        this.latencyEnabled = ms > 0;
        this.latencyMs = ms;
        log.warn("Synthentic Latency Set to {} ms", ms);
    }
    
    public void checkLatency() {
        if (latencyEnabled) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void flushRedis() {
        log.warn("Flushing Redis...");
        try {
            redisConnectionFactory.getConnection().serverCommands().flushAll();
        } catch (Exception e) {
            log.error("Failed to flush redis", e);
        }
    }
}
