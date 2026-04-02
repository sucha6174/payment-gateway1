package com.gateway.workers;

import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.models.Merchant;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.WebhookLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class WebhookWorker {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private WebhookLogRepository webhookLogRepository;
    @Autowired
    private MerchantRepository merchantRepository;

    @Value("${WEBHOOK_RETRY_INTERVALS_TEST:false}")
    private boolean useTestIntervals;

    private boolean active = true;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Async
    public void start() {
        System.out.println("✅ Webhook Worker Started...");
        while (active) {
            try {
                DeliverWebhookJob job = (DeliverWebhookJob) redisTemplate.opsForList().rightPop("queue:webhooks", 5, TimeUnit.SECONDS);
                if (job != null) {
                    processWebhook(job);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processWebhook(DeliverWebhookJob job) {
        WebhookLog log = webhookLogRepository.findById(job.getWebhookLogId()).orElse(null);
        if (log == null || "success".equals(log.getStatus()) || "failed".equals(log.getStatus())) return;

        // Check if it is time to retry
        if (log.getNextRetryAt() != null && log.getNextRetryAt().isAfter(LocalDateTime.now())) {
            // Not ready yet, push back to queue (or handling via scheduled task is better, but for simplicity:)
            // In a real production system, we wouldn't pop it yet. 
            // For this project, we rely on the RetryScheduler (which we will build next) to push it back.
            return; 
        }

        Merchant merchant = merchantRepository.findById(log.getMerchantId()).orElse(null);
        if (merchant == null || merchant.getWebhookUrl() == null) {
            log.setStatus("failed"); // No URL to send to
            webhookLogRepository.save(log);
            return;
        }

        try {
            // 1. Generate Signature
            String signature = generateHmac(log.getPayload(), merchant.getWebhookSecret());

            // 2. Send Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(merchant.getWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(log.getPayload()))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 3. Handle Response
            log.setLastAttemptAt(LocalDateTime.now());
            log.setResponseCode(response.statusCode());
            log.setResponseBody(response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.setStatus("success");
            } else {
                handleFailure(log);
            }
        } catch (Exception e) {
            log.setResponseBody("Error: " + e.getMessage());
            handleFailure(log);
        }
        
        webhookLogRepository.save(log);
    }

    private void handleFailure(WebhookLog log) {
        int attempts = log.getAttempts() + 1;
        log.setAttempts(attempts);

        if (attempts >= 5) {
            log.setStatus("failed");
        } else {
            log.setStatus("pending");
            log.setNextRetryAt(LocalDateTime.now().plusSeconds(getRetryDelay(attempts)));
        }
    }

    private long getRetryDelay(int attempt) {
        if (useTestIntervals) {
            return switch (attempt) {
                case 1 -> 5;
                case 2 -> 10;
                case 3 -> 15;
                case 4 -> 20;
                default -> 0;
            };
        } else {
            // Production intervals: 1m, 5m, 30m, 2h
            return switch (attempt) {
                case 1 -> 60;
                case 2 -> 300;
                case 3 -> 1800;
                case 4 -> 7200;
                default -> 0;
            };
        }
    }

    private String generateHmac(String data, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] rawHmac = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // Convert to Hex
        StringBuilder hexString = new StringBuilder();
        for (byte b : rawHmac) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}