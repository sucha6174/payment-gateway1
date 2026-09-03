package com.gateway.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.jobs.ProcessRefundJob;
import com.gateway.services.JobQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Profile("worker")
public class WorkerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkerRunner.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentWorker paymentWorker;
    private final WebhookWorker webhookWorker;
    private final RefundWorker refundWorker;
    private final JobQueueService jobQueueService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private volatile boolean running = true;

    public WorkerRunner(StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper,
                        PaymentWorker paymentWorker,
                        WebhookWorker webhookWorker,
                        RefundWorker refundWorker,
                        JobQueueService jobQueueService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.paymentWorker = paymentWorker;
        this.webhookWorker = webhookWorker;
        this.refundWorker = refundWorker;
        this.jobQueueService = jobQueueService;
    }

    @Override
    public void run(String... args) {
        log.info("Starting Background Worker Service Runner...");

        // Thread 1: Payments Consumer
        executorService.submit(this::consumePayments);

        // Thread 2: Webhooks Consumer
        executorService.submit(this::consumeWebhooks);

        // Thread 3: Refunds Consumer
        executorService.submit(this::consumeRefunds);

        // Thread 4: Worker Heartbeat
        executorService.submit(this::heartbeatLoop);
    }

    private void consumePayments() {
        while (running) {
            try {
                String json = redisTemplate.opsForList().leftPop(JobQueueService.QUEUE_PAYMENTS, 2, TimeUnit.SECONDS);
                if (json != null && !json.trim().isEmpty()) {
                    ProcessPaymentJob job = objectMapper.readValue(json, ProcessPaymentJob.class);
                    executorService.submit(() -> paymentWorker.processPayment(job));
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Error consuming payments queue: {}", e.getMessage());
                    sleep(1000);
                }
            }
        }
    }

    private void consumeWebhooks() {
        while (running) {
            try {
                String json = redisTemplate.opsForList().leftPop(JobQueueService.QUEUE_WEBHOOKS, 2, TimeUnit.SECONDS);
                if (json != null && !json.trim().isEmpty()) {
                    DeliverWebhookJob job = objectMapper.readValue(json, DeliverWebhookJob.class);
                    executorService.submit(() -> webhookWorker.processWebhook(job));
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Error consuming webhooks queue: {}", e.getMessage());
                    sleep(1000);
                }
            }
        }
    }

    private void consumeRefunds() {
        while (running) {
            try {
                String json = redisTemplate.opsForList().leftPop(JobQueueService.QUEUE_REFUNDS, 2, TimeUnit.SECONDS);
                if (json != null && !json.trim().isEmpty()) {
                    ProcessRefundJob job = objectMapper.readValue(json, ProcessRefundJob.class);
                    executorService.submit(() -> refundWorker.processRefund(job));
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Error consuming refunds queue: {}", e.getMessage());
                    sleep(1000);
                }
            }
        }
    }

    private void heartbeatLoop() {
        while (running) {
            try {
                jobQueueService.recordHeartbeat();
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                sleep(2000);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        this.running = false;
        executorService.shutdown();
    }
}
