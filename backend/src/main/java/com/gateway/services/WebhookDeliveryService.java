package com.gateway.services;

import com.gateway.models.Merchant;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.WebhookLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final MerchantRepository merchantRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final WebhookSignerService webhookSignerService;

    @Value("${gateway.test-webhook-retry:false}")
    private boolean testWebhookRetry;

    public WebhookDeliveryService(MerchantRepository merchantRepository,
                                  WebhookLogRepository webhookLogRepository,
                                  WebhookSignerService webhookSignerService) {
        this.merchantRepository = merchantRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.webhookSignerService = webhookSignerService;
    }

    public void setTestWebhookRetry(boolean testWebhookRetry) {
        this.testWebhookRetry = testWebhookRetry;
    }

    public boolean isTestWebhookRetry() {
        return testWebhookRetry;
    }

    public void deliverWebhook(UUID merchantId, UUID webhookLogId, String event, String payload) {
        Optional<Merchant> merchantOpt = merchantRepository.findById(merchantId);
        if (merchantOpt.isEmpty()) {
            log.warn("Merchant not found for webhook delivery: {}", merchantId);
            return;
        }

        Merchant merchant = merchantOpt.get();
        if (merchant.getWebhookUrl() == null || merchant.getWebhookUrl().trim().isEmpty()) {
            log.info("No webhook URL configured for merchant {}. Skipping delivery.", merchantId);
            return;
        }

        WebhookLog logEntry;
        if (webhookLogId != null) {
            logEntry = webhookLogRepository.findById(webhookLogId)
                    .orElseGet(() -> new WebhookLog(merchantId, event, payload));
        } else {
            logEntry = new WebhookLog(merchantId, event, payload);
        }

        int currentAttempt = logEntry.getAttempts() + 1;
        logEntry.setAttempts(currentAttempt);
        logEntry.setLastAttemptAt(Instant.now());

        String secret = merchant.getWebhookSecret();
        if (secret == null || secret.isEmpty()) {
            secret = "whsec_default";
        }

        String signature = webhookSignerService.generateSignature(payload, secret);

        int responseCode = 0;
        String responseBody = null;
        boolean success = false;

        try {
            URL url = new URI(merchant.getWebhookUrl()).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Webhook-Signature", signature);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            responseCode = connection.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 400) 
                    ? connection.getInputStream() 
                    : connection.getErrorStream();

            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    responseBody = response.toString();
                }
            }

            success = (responseCode >= 200 && responseCode < 300);
        } catch (Exception e) {
            log.warn("Webhook delivery failed for merchant {}: {}", merchantId, e.getMessage());
            responseBody = "Error: " + e.getMessage();
        }

        logEntry.setResponseCode(responseCode);
        logEntry.setResponseBody(responseBody != null && responseBody.length() > 2000 
                ? responseBody.substring(0, 2000) 
                : responseBody);

        if (success) {
            logEntry.setStatus("success");
            logEntry.setNextRetryAt(null);
            log.info("Webhook delivered successfully to merchant {}. Event: {}", merchantId, event);
        } else {
            if (currentAttempt < 5) {
                logEntry.setStatus("pending");
                long delaySeconds = getRetryDelaySeconds(currentAttempt + 1);
                logEntry.setNextRetryAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
                log.info("Webhook attempt {} failed for merchant {}. Next retry scheduled at {}",
                        currentAttempt, merchantId, logEntry.getNextRetryAt());
            } else {
                logEntry.setStatus("failed");
                logEntry.setNextRetryAt(null);
                log.warn("Webhook permanently failed after {} attempts for merchant {}", currentAttempt, merchantId);
            }
        }

        webhookLogRepository.save(logEntry);
    }

    /**
     * Calculates the retry delay based on attempt number and mode.
     * Attempt 1: Immediate (0s)
     * Attempt 2: 1m (prod) / 5s (test)
     * Attempt 3: 5m (prod) / 10s (test)
     * Attempt 4: 30m (prod) / 15s (test)
     * Attempt 5: 2h (prod) / 20s (test)
     */
    public long getRetryDelaySeconds(int nextAttempt) {
        if (testWebhookRetry) {
            return switch (nextAttempt) {
                case 1 -> 0L;
                case 2 -> 5L;
                case 3 -> 10L;
                case 4 -> 15L;
                case 5 -> 20L;
                default -> 30L;
            };
        } else {
            return switch (nextAttempt) {
                case 1 -> 0L;
                case 2 -> 60L;       // 1 minute
                case 3 -> 300L;      // 5 minutes
                case 4 -> 1800L;     // 30 minutes
                case 5 -> 7200L;     // 2 hours
                default -> 14400L;
            };
        }
    }
}
