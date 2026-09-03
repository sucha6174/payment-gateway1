package com.gateway.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.jobs.ProcessRefundJob;
import com.gateway.models.Payment;
import com.gateway.models.Refund;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.PaymentRepository;
import com.gateway.repositories.RefundRepository;
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
public class RefundWorker {

    private static final Logger log = LoggerFactory.getLogger(RefundWorker.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final JobQueueService jobQueueService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Value("${gateway.test-mode:false}")
    private boolean testMode;

    public RefundWorker(RefundRepository refundRepository,
                        PaymentRepository paymentRepository,
                        WebhookLogRepository webhookLogRepository,
                        JobQueueService jobQueueService,
                        ObjectMapper objectMapper) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.jobQueueService = jobQueueService;
        this.objectMapper = objectMapper;
    }

    public void processRefund(ProcessRefundJob job) {
        if (job == null || job.getRefundId() == null) {
            return;
        }

        jobQueueService.incrementProcessing();
        String refundId = job.getRefundId();
        log.info("Processing refund: {}", refundId);

        try {
            Optional<Refund> refundOpt = refundRepository.findById(refundId);
            for (int i = 0; i < 15 && refundOpt.isEmpty(); i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                refundOpt = refundRepository.findById(refundId);
            }

            if (refundOpt.isEmpty()) {
                log.error("Refund not found with id: {}", refundId);
                jobQueueService.incrementFailed();
                return;
            }

            Refund refund = refundOpt.get();
            Optional<Payment> paymentOpt = paymentRepository.findById(refund.getPaymentId());
            if (paymentOpt.isEmpty()) {
                log.error("Payment not found for refund: {}", refundId);
                jobQueueService.incrementFailed();
                return;
            }

            // Simulate refund processing delay (3-5 seconds, or 500ms in test mode)
            long delay = testMode ? 500 : (3000 + random.nextInt(2001));
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            refund.setStatus("processed");
            refund.setProcessedAt(Instant.now());
            refundRepository.save(refund);
            log.info("Refund {} processed successfully", refundId);

            // Construct Webhook payload for refund.processed
            String eventName = "refund.processed";
            ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("event", eventName);
            payloadNode.put("timestamp", Instant.now().getEpochSecond());
            ObjectNode dataNode = payloadNode.putObject("data");
            dataNode.set("refund", objectMapper.valueToTree(refund));

            String payloadString = objectMapper.writeValueAsString(payloadNode);

            // Create webhook log
            WebhookLog webhookLog = new WebhookLog(refund.getMerchantId(), eventName, payloadString);
            webhookLog = webhookLogRepository.save(webhookLog);

            // Enqueue Webhook Delivery Job
            DeliverWebhookJob webhookJob = new DeliverWebhookJob(
                    refund.getMerchantId(),
                    webhookLog.getId(),
                    eventName,
                    payloadString
            );
            jobQueueService.enqueueWebhook(webhookJob);
            jobQueueService.incrementCompleted();

        } catch (Exception e) {
            log.error("Error processing refund {}: {}", refundId, e.getMessage(), e);
            jobQueueService.incrementFailed();
        } finally {
            jobQueueService.decrementProcessing();
        }
    }
}
