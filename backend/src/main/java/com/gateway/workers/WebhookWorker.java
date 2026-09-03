package com.gateway.workers;

import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.services.JobQueueService;
import com.gateway.services.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebhookWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookWorker.class);

    private final WebhookDeliveryService webhookDeliveryService;
    private final JobQueueService jobQueueService;

    public WebhookWorker(WebhookDeliveryService webhookDeliveryService, JobQueueService jobQueueService) {
        this.webhookDeliveryService = webhookDeliveryService;
        this.jobQueueService = jobQueueService;
    }

    public void processWebhook(DeliverWebhookJob job) {
        if (job == null || job.getMerchantId() == null) {
            return;
        }

        jobQueueService.incrementProcessing();
        log.info("Processing webhook delivery for merchant: {}, event: {}", job.getMerchantId(), job.getEvent());

        try {
            webhookDeliveryService.deliverWebhook(
                    job.getMerchantId(),
                    job.getWebhookLogId(),
                    job.getEvent(),
                    job.getPayload()
            );
            jobQueueService.incrementCompleted();
        } catch (Exception e) {
            log.error("Error processing webhook delivery: {}", e.getMessage(), e);
            jobQueueService.incrementFailed();
        } finally {
            jobQueueService.decrementProcessing();
        }
    }
}
