package com.sysbehavior.platform.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to expose /metrics as an alias for /actuator/prometheus.
 * 
 * WHY: Reduces cognitive friction for developers and DevOps teams who expect
 * metrics at /metrics rather than /actuator/prometheus.
 * 
 * DESIGN: This is a thin forwarding layer that delegates to the actual
 * Prometheus endpoint. No metrics logic is duplicated.
 */
@RestController
public class MetricsAliasController {
    
    private final PrometheusScrapeEndpoint prometheusScrapeEndpoint;
    
    @Autowired
    public MetricsAliasController(PrometheusScrapeEndpoint prometheusScrapeEndpoint) {
        this.prometheusScrapeEndpoint = prometheusScrapeEndpoint;
    }
    
    /**
     * Expose /metrics as an alias for /actuator/prometheus.
     * 
     * This endpoint returns exactly the same content as /actuator/prometheus
     * by delegating directly to the PrometheusScrapeEndpoint.
     * 
     * @return Prometheus-formatted metrics
     */
    @GetMapping(value = "/metrics", produces = "text/plain; version=0.0.4; charset=utf-8")
    public ResponseEntity<String> metrics(HttpServletRequest request) {
        // Delegate to the actual Prometheus endpoint
        String metricsOutput = prometheusScrapeEndpoint.scrape();
        
        // Return with proper Prometheus content type
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8")
                .body(metricsOutput);
    }
}
