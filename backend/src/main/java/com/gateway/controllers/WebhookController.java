package com.gateway.controllers;

import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.models.Merchant;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.WebhookLogRepository;
import com.gateway.services.AuthService;
import com.gateway.services.JobQueueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
public class WebhookController {

    private final WebhookLogRepository webhookLogRepository;
    private final MerchantRepository merchantRepository;
    private final AuthService authService;
    private final JobQueueService jobQueueService;
    private final SecureRandom random = new SecureRandom();
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";

    public WebhookController(WebhookLogRepository webhookLogRepository,
                             MerchantRepository merchantRepository,
                             AuthService authService,
                             JobQueueService jobQueueService) {
        this.webhookLogRepository = webhookLogRepository;
        this.merchantRepository = merchantRepository;
        this.authService = authService;
        this.jobQueueService = jobQueueService;
    }

    @GetMapping("/api/v1/webhooks")
    public ResponseEntity<?> listWebhooks(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        int pageNumber = limit > 0 ? offset / limit : 0;
        int pageSize = Math.max(1, limit);
        Page<WebhookLog> page = webhookLogRepository.findByMerchantIdOrderByCreatedAtDesc(
                merchant.getId(),
                PageRequest.of(pageNumber, pageSize)
        );

        Map<String, Object> response = new HashMap<>();
        response.put("data", page.getContent());
        response.put("total", page.getTotalElements());
        response.put("limit", limit);
        response.put("offset", offset);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/webhooks/{webhookId}/retry")
    @Transactional
    public ResponseEntity<?> retryWebhook(
            @PathVariable("webhookId") UUID webhookId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        Optional<WebhookLog> logOpt = webhookLogRepository.findByIdAndMerchantId(webhookId, merchant.getId());
        if (logOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND_ERROR", "description", "Webhook log not found")
            ));
        }

        WebhookLog webhookLog = logOpt.get();
        webhookLog.setAttempts(0);
        webhookLog.setStatus("pending");
        webhookLog.setNextRetryAt(null);
        webhookLogRepository.save(webhookLog);

        // Enqueue DeliverWebhookJob
        DeliverWebhookJob job = new DeliverWebhookJob(
                merchant.getId(),
                webhookLog.getId(),
                webhookLog.getEvent(),
                webhookLog.getPayload()
        );
        jobQueueService.enqueueWebhook(job);

        return ResponseEntity.ok(Map.of(
                "id", webhookLog.getId().toString(),
                "status", "pending",
                "message", "Webhook retry scheduled"
        ));
    }

    @GetMapping("/api/v1/merchants/webhook-config")
    public ResponseEntity<?> getWebhookConfig(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        return ResponseEntity.ok(Map.of(
                "webhook_url", merchant.getWebhookUrl() != null ? merchant.getWebhookUrl() : "",
                "webhook_secret", merchant.getWebhookSecret() != null ? merchant.getWebhookSecret() : ""
        ));
    }

    @PostMapping("/api/v1/merchants/webhook-config")
    public ResponseEntity<?> updateWebhookConfig(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret,
            @RequestBody Map<String, String> body) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        String webhookUrl = body.get("webhook_url");
        merchant.setWebhookUrl(webhookUrl);
        merchantRepository.save(merchant);

        return ResponseEntity.ok(Map.of(
                "webhook_url", merchant.getWebhookUrl() != null ? merchant.getWebhookUrl() : "",
                "webhook_secret", merchant.getWebhookSecret() != null ? merchant.getWebhookSecret() : "",
                "message", "Webhook configuration saved successfully"
        ));
    }

    @PostMapping("/api/v1/merchants/webhook-config/regenerate-secret")
    public ResponseEntity<?> regenerateSecret(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        StringBuilder sb = new StringBuilder("whsec_");
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        merchant.setWebhookSecret(sb.toString());
        merchantRepository.save(merchant);

        return ResponseEntity.ok(Map.of(
                "webhook_secret", merchant.getWebhookSecret(),
                "message", "Webhook secret regenerated successfully"
        ));
    }

    @PostMapping("/api/v1/merchants/test-webhook")
    public ResponseEntity<?> sendTestWebhook(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        String event = "payment.success";
        String samplePayload = String.format(
                "{\"event\":\"payment.success\",\"timestamp\":%d,\"data\":{\"payment\":{\"id\":\"pay_test_sample123\",\"amount\":50000,\"currency\":\"INR\",\"status\":\"success\"}}}",
                Instant.now().getEpochSecond()
        );

        WebhookLog log = new WebhookLog(merchant.getId(), event, samplePayload);
        log = webhookLogRepository.save(log);

        jobQueueService.enqueueWebhook(new DeliverWebhookJob(merchant.getId(), log.getId(), event, samplePayload));

        return ResponseEntity.ok(Map.of(
                "message", "Test webhook queued for delivery",
                "webhook_id", log.getId()
        ));
    }
}
