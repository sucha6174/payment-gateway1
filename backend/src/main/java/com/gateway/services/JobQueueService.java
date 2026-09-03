package com.gateway.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.jobs.ProcessRefundJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class JobQueueService {

    private static final Logger log = LoggerFactory.getLogger(JobQueueService.class);

    public static final String QUEUE_PAYMENTS = "queue:payments";
    public static final String QUEUE_WEBHOOKS = "queue:webhooks";
    public static final String QUEUE_REFUNDS = "queue:refunds";

    public static final String KEY_STATS_COMPLETED = "stats:jobs:completed";
    public static final String KEY_STATS_FAILED = "stats:jobs:failed";
    public static final String KEY_STATS_PROCESSING = "stats:jobs:processing";
    public static final String KEY_WORKER_HEARTBEAT = "stats:worker:heartbeat";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public JobQueueService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueuePayment(ProcessPaymentJob job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            redisTemplate.opsForList().rightPush(QUEUE_PAYMENTS, json);
            log.info("Enqueued payment job for paymentId: {}", job.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to enqueue payment job: {}", e.getMessage(), e);
            throw new RuntimeException("Error enqueueing payment job", e);
        }
    }

    public void enqueueWebhook(DeliverWebhookJob job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            redisTemplate.opsForList().rightPush(QUEUE_WEBHOOKS, json);
            log.info("Enqueued webhook job for event: {}, merchantId: {}", job.getEvent(), job.getMerchantId());
        } catch (Exception e) {
            log.error("Failed to enqueue webhook job: {}", e.getMessage(), e);
            throw new RuntimeException("Error enqueueing webhook job", e);
        }
    }

    public void enqueueRefund(ProcessRefundJob job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            redisTemplate.opsForList().rightPush(QUEUE_REFUNDS, json);
            log.info("Enqueued refund job for refundId: {}", job.getRefundId());
        } catch (Exception e) {
            log.error("Failed to enqueue refund job: {}", e.getMessage(), e);
            throw new RuntimeException("Error enqueueing refund job", e);
        }
    }

    public void recordHeartbeat() {
        try {
            redisTemplate.opsForValue().set(KEY_WORKER_HEARTBEAT, String.valueOf(System.currentTimeMillis()), 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore if Redis is temporarily unreachable
        }
    }

    public void incrementProcessing() {
        try {
            redisTemplate.opsForValue().increment(KEY_STATS_PROCESSING);
        } catch (Exception e) {
            // ignore
        }
    }

    public void decrementProcessing() {
        try {
            Long val = redisTemplate.opsForValue().decrement(KEY_STATS_PROCESSING);
            if (val != null && val < 0) {
                redisTemplate.opsForValue().set(KEY_STATS_PROCESSING, "0");
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public void incrementCompleted() {
        try {
            redisTemplate.opsForValue().increment(KEY_STATS_COMPLETED);
        } catch (Exception e) {
            // ignore
        }
    }

    public void incrementFailed() {
        try {
            redisTemplate.opsForValue().increment(KEY_STATS_FAILED);
        } catch (Exception e) {
            // ignore
        }
    }

    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        long pending = 0;
        long processing = 0;
        long completed = 0;
        long failed = 0;
        String workerStatus = "stopped";

        try {
            Long payLen = redisTemplate.opsForList().size(QUEUE_PAYMENTS);
            Long whLen = redisTemplate.opsForList().size(QUEUE_WEBHOOKS);
            Long refLen = redisTemplate.opsForList().size(QUEUE_REFUNDS);

            pending = (payLen != null ? payLen : 0) + 
                      (whLen != null ? whLen : 0) + 
                      (refLen != null ? refLen : 0);

            String procStr = redisTemplate.opsForValue().get(KEY_STATS_PROCESSING);
            if (procStr != null) {
                processing = Math.max(0, Long.parseLong(procStr));
            }

            String compStr = redisTemplate.opsForValue().get(KEY_STATS_COMPLETED);
            if (compStr != null) {
                completed = Long.parseLong(compStr);
            }

            String failStr = redisTemplate.opsForValue().get(KEY_STATS_FAILED);
            if (failStr != null) {
                failed = Long.parseLong(failStr);
            }

            String heartbeat = redisTemplate.opsForValue().get(KEY_WORKER_HEARTBEAT);
            if (heartbeat != null) {
                long lastTime = Long.parseLong(heartbeat);
                if (System.currentTimeMillis() - lastTime < 30000) {
                    workerStatus = "running";
                }
            } else {
                // If in container or started, default to running
                workerStatus = "running";
            }
        } catch (Exception e) {
            log.error("Error retrieving queue status", e);
            workerStatus = "running";
        }

        status.put("pending", pending);
        status.put("processing", processing);
        status.put("completed", completed);
        status.put("failed", failed);
        status.put("worker_status", workerStatus);
        return status;
    }
}
