package com.gateway.controllers;

import com.gateway.services.JobQueueService;
import com.gateway.services.WebhookDeliveryService;
import com.gateway.workers.PaymentWorker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@CrossOrigin(origins = "*")
public class TestController {

    private final JobQueueService jobQueueService;
    private final PaymentWorker paymentWorker;
    private final WebhookDeliveryService webhookDeliveryService;

    public TestController(JobQueueService jobQueueService,
                          PaymentWorker paymentWorker,
                          WebhookDeliveryService webhookDeliveryService) {
        this.jobQueueService = jobQueueService;
        this.paymentWorker = paymentWorker;
        this.webhookDeliveryService = webhookDeliveryService;
    }

    /**
     * Required endpoint for evaluation to monitor job queue state without direct Redis access.
     */
    @GetMapping("/jobs/status")
    public ResponseEntity<Map<String, Object>> getJobStatus() {
        Map<String, Object> status = jobQueueService.getQueueStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Helper endpoint for tests to dynamically inspect or toggle test modes.
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateTestConfig(@RequestBody Map<String, Object> body) {
        if (body.containsKey("test_mode")) {
            paymentWorker.setTestMode(Boolean.parseBoolean(body.get("test_mode").toString()));
        }
        if (body.containsKey("test_delay")) {
            paymentWorker.setTestProcessingDelay(Long.parseLong(body.get("test_delay").toString()));
        }
        if (body.containsKey("test_payment_success")) {
            paymentWorker.setTestPaymentSuccess(Boolean.parseBoolean(body.get("test_payment_success").toString()));
        }
        if (body.containsKey("test_webhook_retry")) {
            webhookDeliveryService.setTestWebhookRetry(Boolean.parseBoolean(body.get("test_webhook_retry").toString()));
        }

        return ResponseEntity.ok(Map.of(
                "test_mode", paymentWorker.isTestMode(),
                "test_webhook_retry", webhookDeliveryService.isTestWebhookRetry()
        ));
    }
}
