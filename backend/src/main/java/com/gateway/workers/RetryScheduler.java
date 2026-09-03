package com.gateway.workers;

import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.WebhookLogRepository;
import com.gateway.services.JobQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final WebhookLogRepository webhookLogRepository;
    private final JobQueueService jobQueueService;

    public RetryScheduler(WebhookLogRepository webhookLogRepository, JobQueueService jobQueueService) {
        this.webhookLogRepository = webhookLogRepository;
        this.jobQueueService = jobQueueService;
    }

    @Scheduled(fixedDelay = 2000)
    public void processScheduledRetries() {
        try {
            Instant now = Instant.now();
            List<WebhookLog> pendingLogs = webhookLogRepository.findByStatusAndNextRetryAtBefore("pending", now);
            if (pendingLogs != null && !pendingLogs.isEmpty()) {
                log.info("Found {} pending webhooks due for retry", pendingLogs.size());
                for (WebhookLog logEntry : pendingLogs) {
                    // Temporarily postpone next_retry_at to avoid immediate duplicate scheduling
                    logEntry.setNextRetryAt(now.plus(30, ChronoUnit.SECONDS));
                    webhookLogRepository.save(logEntry);

                    DeliverWebhookJob job = new DeliverWebhookJob(
                            logEntry.getMerchantId(),
                            logEntry.getId(),
                            logEntry.getEvent(),
                            logEntry.getPayload()
                    );
                    jobQueueService.enqueueWebhook(job);
                }
            }
        } catch (Exception e) {
            log.error("Error running RetryScheduler: {}", e.getMessage(), e);
        }
    }
}
