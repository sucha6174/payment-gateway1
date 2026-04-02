package com.gateway.controllers;

import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.models.Merchant;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.WebhookLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@CrossOrigin
public class WebhookController {

    @Autowired private WebhookLogRepository webhookLogRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @GetMapping
    public ResponseEntity<?> listWebhooks(
            @RequestHeader("X-Api-Key") String apiKey,
            @RequestHeader("X-Api-Secret") String apiSecret) {

        Merchant merchant = merchantRepository.findByApiKey(apiKey).orElse(null);
        if (merchant == null || !merchant.getApiSecret().equals(apiSecret)) {
            return ResponseEntity.status(401).build();
        }

        List<WebhookLog> logs = webhookLogRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.getId());
        return ResponseEntity.ok(Map.of("data", logs, "total", logs.size()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryWebhook(
            @PathVariable UUID id,
            @RequestHeader("X-Api-Key") String apiKey,
            @RequestHeader("X-Api-Secret") String apiSecret) {

        // Validate auth... (Simplified for brevity)
        WebhookLog log = webhookLogRepository.findById(id).orElse(null);
        if (log == null) return ResponseEntity.status(404).build();

        log.setStatus("pending");
        log.setAttempts(0); // Reset attempts
        webhookLogRepository.save(log);

        redisTemplate.opsForList().leftPush("queue:webhooks", new DeliverWebhookJob(log.getId()));

        return ResponseEntity.ok(Map.of("status", "pending", "message", "Retry scheduled"));
    }
}