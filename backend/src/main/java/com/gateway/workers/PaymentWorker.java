package com.gateway.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.models.Payment;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.PaymentRepository;
import com.gateway.repositories.WebhookLogRepository;
import com.gateway.services.JobQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Component
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final PaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final JobQueueService jobQueueService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Value("${gateway.test-mode:false}")
    private boolean testMode;

    @Value("${gateway.test-delay:1000}")
    private long testProcessingDelay;

    @Value("${gateway.test-payment-success:true}")
    private boolean testPaymentSuccess;

    public PaymentWorker(PaymentRepository paymentRepository,
                         MerchantRepository merchantRepository,
                         WebhookLogRepository webhookLogRepository,
                         JobQueueService jobQueueService,
                         ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.merchantRepository = merchantRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.jobQueueService = jobQueueService;
        this.objectMapper = objectMapper;
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public void setTestProcessingDelay(long testProcessingDelay) {
        this.testProcessingDelay = testProcessingDelay;
    }

    public void setTestPaymentSuccess(boolean testPaymentSuccess) {
        this.testPaymentSuccess = testPaymentSuccess;
    }

    public void processPayment(ProcessPaymentJob job) {
        if (job == null || job.getPaymentId() == null) {
            return;
        }

        jobQueueService.incrementProcessing();
        String paymentId = job.getPaymentId();
        log.info("Processing payment: {}", paymentId);

        try {
            Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
            for (int i = 0; i < 15 && paymentOpt.isEmpty(); i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                paymentOpt = paymentRepository.findById(paymentId);
            }

            if (paymentOpt.isEmpty()) {
                log.error("Payment not found with id: {}", paymentId);
                jobQueueService.incrementFailed();
                return;
            }

            Payment payment = paymentOpt.get();

            // Simulate delay
            long delay;
            if (testMode) {
                delay = testProcessingDelay > 0 ? testProcessingDelay : 1000;
            } else {
                // 5 to 10 seconds random delay
                delay = 5000 + random.nextInt(5001);
            }

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Determine outcome
            boolean isSuccess;
            if (testMode) {
                isSuccess = testPaymentSuccess;
            } else {
                if ("upi".equalsIgnoreCase(payment.getMethod())) {
                    // UPI: 90% success
                    isSuccess = random.nextInt(100) < 90;
                } else {
                    // Card: 95% success
                    isSuccess = random.nextInt(100) < 95;
                }
            }

            String eventName;
            if (isSuccess) {
                payment.setStatus("success");
                payment.setErrorCode(null);
                payment.setErrorDescription(null);
                eventName = "payment.success";
                log.info("Payment {} processed successfully", paymentId);
            } else {
                payment.setStatus("failed");
                payment.setErrorCode("PAYMENT_FAILED");
                payment.setErrorDescription("Payment processing failed by acquiring bank");
                eventName = "payment.failed";
                log.warn("Payment {} failed during processing", paymentId);
            }

            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            // Construct Webhook payload
            ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("event", eventName);
            payloadNode.put("timestamp", Instant.now().getEpochSecond());
            ObjectNode dataNode = payloadNode.putObject("data");
            dataNode.set("payment", objectMapper.valueToTree(payment));

            String payloadString = objectMapper.writeValueAsString(payloadNode);

            // Create initial webhook log
            WebhookLog webhookLog = new WebhookLog(payment.getMerchantId(), eventName, payloadString);
            webhookLog = webhookLogRepository.save(webhookLog);

            // Enqueue Webhook Delivery Job
            DeliverWebhookJob webhookJob = new DeliverWebhookJob(
                    payment.getMerchantId(),
                    webhookLog.getId(),
                    eventName,
                    payloadString
            );
            jobQueueService.enqueueWebhook(webhookJob);
            jobQueueService.incrementCompleted();

        } catch (Exception e) {
            log.error("Error processing payment {}: {}", paymentId, e.getMessage(), e);
            jobQueueService.incrementFailed();
        } finally {
            jobQueueService.decrementProcessing();
        }
    }
}
