package com.sysbehavior.platform.controller;

import com.sysbehavior.platform.service.FailureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private FailureService failureService;

    @PostMapping("/failure/db/exhaust")
    public ResponseEntity<?> exhaustDbPool() {
        failureService.exhaustDbPool();
        return ResponseEntity.ok("DB Pool Exhausted initiated");
    }

    @PostMapping("/failure/db/release")
    public ResponseEntity<?> releaseDbPool() {
        failureService.releaseDbPool();
        return ResponseEntity.ok("DB Pool Released");
    }
    
    @PostMapping("/failure/db/kill")
    public ResponseEntity<?> killDbConnections() {
        failureService.killDbConnections();
        return ResponseEntity.ok("DB Connections Killed");
    }

    @PostMapping("/failure/latency/{ms}")
    public ResponseEntity<?> setLatency(@PathVariable Long ms) {
        failureService.setLatency(ms);
        return ResponseEntity.ok("Latency set to " + ms + "ms");
    }
    
    @PostMapping("/failure/redis/flush")
    public ResponseEntity<?> flushRedis() {
        failureService.flushRedis();
        return ResponseEntity.ok("Redis Flushed");
    }
}
