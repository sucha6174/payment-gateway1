package com.gateway.workers;

import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.WebhookLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("worker")
public class RetryScheduler {

    @Autowired
    private WebhookLogRepository webhookLogRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Run every 10 seconds to check for due retries
    @Scheduled(fixedRate = 10000)
    public void requeuePendingWebhooks() {
        List<WebhookLog> logsToRetry = webhookLogRepository.findByStatusAndNextRetryAtBefore("pending", LocalDateTime.now());

        for (WebhookLog log : logsToRetry) {
            // Push back to Redis
            redisTemplate.opsForList().leftPush("queue:webhooks", new DeliverWebhookJob(log.getId()));
            
            // Update nextRetryAt to null so we don't pick it up again immediately
            log.setNextRetryAt(null); 
            webhookLogRepository.save(log);
            
            System.out.println("Re-queued webhook: " + log.getId());
        }
    }
}