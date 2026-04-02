package com.gateway.workers;

import com.gateway.jobs.ProcessRefundJob;
import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.models.Refund;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.RefundRepository;
import com.gateway.repositories.WebhookLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class RefundWorker {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RefundRepository refundRepository;
    
    @Autowired
    private WebhookLogRepository webhookLogRepository;

    private boolean active = true;

    @Async
    public void start() {
        System.out.println("✅ Refund Worker Started...");
        while (active) {
            try {
                ProcessRefundJob job = (ProcessRefundJob) redisTemplate.opsForList().rightPop("queue:refunds", 5, TimeUnit.SECONDS);
                if (job != null) {
                    processRefund(job.getRefundId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processRefund(String refundId) {
        System.out.println("Processing Refund: " + refundId);
        
        Refund refund = refundRepository.findById(refundId).orElse(null);
        if (refund == null) return;

        // Simulate Processing Delay
        try {
            Thread.sleep(3000); 
        } catch (InterruptedException e) {}

        // Update Status
        refund.setStatus("processed");
        refund.setProcessedAt(LocalDateTime.now());
        refundRepository.save(refund);
        
        // Enqueue Webhook
        enqueueWebhook(refund);
    }
    
    private void enqueueWebhook(Refund refund) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("event", "refund.processed");
            
            JSONObject data = new JSONObject();
            data.put("refund_id", refund.getId());
            data.put("payment_id", refund.getPaymentId());
            data.put("amount", refund.getAmount());
            data.put("status", "processed");
            payload.put("data", data);

            WebhookLog log = new WebhookLog();
            log.setMerchantId(refund.getMerchantId());
            log.setEvent("refund.processed");
            log.setPayload(payload.toString());
            log.setNextRetryAt(LocalDateTime.now());
            
            WebhookLog savedLog = webhookLogRepository.save(log);
            
            redisTemplate.opsForList().leftPush("queue:webhooks", new DeliverWebhookJob(savedLog.getId()));
        } catch (Exception e) {}
    }
}