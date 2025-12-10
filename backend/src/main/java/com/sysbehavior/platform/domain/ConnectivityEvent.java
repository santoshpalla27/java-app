package com.sysbehavior.platform.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "connectivity_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectivityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String target; // MYSQL, REDIS, KAFKA

    @Column(nullable = false)
    private String status; // SUCCESS, FAILURE, DEGRADED

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
