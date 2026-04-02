package com.gateway.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/jobs/status")
    public ResponseEntity<?> getJobStatus() {
        // Approximate counts
        Long payments = redisTemplate.opsForList().size("queue:payments");
        Long refunds = redisTemplate.opsForList().size("queue:refunds");
        Long webhooks = redisTemplate.opsForList().size("queue:webhooks");

        return ResponseEntity.ok(Map.of(
            "pending", (payments != null ? payments : 0) + (refunds != null ? refunds : 0) + (webhooks != null ? webhooks : 0),
            "processing", 0, // Simplified
            "completed", 0,  // Simplified
            "worker_status", "running"
        ));
    }
}