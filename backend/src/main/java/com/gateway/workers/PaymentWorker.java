package com.gateway.workers;

import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.models.Payment;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.PaymentRepository;
import com.gateway.repositories.WebhookLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentWorker {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WebhookLogRepository webhookLogRepository;

    // We use a flag to keep the loop running
    private boolean active = true;

    @Async // Runs in a separate thread
    public void start() {
        System.out.println("✅ Payment Worker Started...");
        
        while (active) {
            try {
                // 1. Wait for a job from Redis (Blocks for 5 seconds then loops)
                ProcessPaymentJob job = (ProcessPaymentJob) redisTemplate.opsForList().rightPop("queue:payments", 5, TimeUnit.SECONDS);

                if (job != null) {
                    processPayment(job.getPaymentId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processPayment(String paymentId) {
        System.out.println("Processing Payment: " + paymentId);
        
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) return;

        // 2. Simulate Delay (5-10 seconds)
        try {
            long delay = 5000 + (long)(Math.random() * 5000); 
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. Determine Outcome (Random Success/Fail)
        boolean isSuccess = determineOutcome(payment.getMethod());

        // 4. Update Database
        if (isSuccess) {
            payment.setStatus("success");
        } else {
            payment.setStatus("failed");
            payment.setErrorCode("BANK_FAILURE");
            payment.setErrorDescription("The bank rejected the transaction.");
        }
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // 5. Enqueue Webhook (Notify the merchant)
        enqueueWebhook(payment);
    }

    private boolean determineOutcome(String method) {
        double chance = Math.random(); // 0.0 to 1.0
        if ("upi".equalsIgnoreCase(method)) {
            return chance < 0.90; // 90% success for UPI
        } else {
            return chance < 0.95; // 95% success for Cards
        }
    }

    private void enqueueWebhook(Payment payment) {
        try {
            // Create the Payload JSON
            JSONObject payload = new JSONObject();
            payload.put("event", "payment." + payment.getStatus());
            
            JSONObject data = new JSONObject();
            JSONObject paymentData = new JSONObject();
            paymentData.put("id", payment.getId());
            paymentData.put("amount", payment.getAmount());
            paymentData.put("status", payment.getStatus());
            paymentData.put("order_id", payment.getOrderId());
            data.put("payment", paymentData);
            
            payload.put("data", data);

            // Create Log Entry
            WebhookLog log = new WebhookLog();
            log.setMerchantId(payment.getMerchant().getId());
            log.setEvent("payment." + payment.getStatus());
            log.setPayload(payload.toString());
            log.setNextRetryAt(LocalDateTime.now()); // Ready immediately
            
            WebhookLog savedLog = webhookLogRepository.save(log);

            // Push to Webhook Queue
            redisTemplate.opsForList().leftPush("queue:webhooks", new DeliverWebhookJob(savedLog.getId()));
            
        } catch (Exception e) {
            System.err.println("Failed to enqueue webhook: " + e.getMessage());
        }
    }
}