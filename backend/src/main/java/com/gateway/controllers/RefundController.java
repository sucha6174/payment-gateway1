package com.gateway.controllers;

import com.gateway.jobs.ProcessRefundJob;
import com.gateway.models.Merchant;
import com.gateway.models.Payment;
import com.gateway.models.Refund;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.PaymentRepository;
import com.gateway.repositories.RefundRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin
public class RefundController {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private Merchant validateAuth(String apiKey, String apiSecret) {
        return merchantRepository.findByApiKey(apiKey)
                .filter(m -> m.getApiSecret().equals(apiSecret))
                .orElse(null);
    }

    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<?> createRefund(
            @PathVariable String paymentId,
            @RequestHeader("X-Api-Key") String apiKey,
            @RequestHeader("X-Api-Secret") String apiSecret,
            @RequestBody Map<String, Object> request) {

        Merchant merchant = validateAuth(apiKey, apiSecret);
        if (merchant == null) return ResponseEntity.status(401).body("Invalid Credentials");

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.getMerchant().getId().equals(merchant.getId())) {
            return ResponseEntity.status(404).body("Payment not found");
        }

        if (!"success".equals(payment.getStatus())) {
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Payment not successful")));
        }

        // Calculate if refund is possible
        List<Refund> existingRefunds = refundRepository.findByPaymentId(paymentId);
        int totalRefunded = existingRefunds.stream().mapToInt(Refund::getAmount).sum();
        int requestedAmount = (Integer) request.get("amount");

        if (totalRefunded + requestedAmount > payment.getAmount()) {
             return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Refund amount exceeds available amount")));
        }

        // Create Refund
        Refund refund = new Refund();
        refund.setId("rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        refund.setPaymentId(paymentId);
        refund.setMerchantId(merchant.getId());
        refund.setAmount(requestedAmount);
        refund.setReason((String) request.get("reason"));
        refund.setStatus("pending");
        
        refundRepository.save(refund);

        // Send to Worker
        redisTemplate.opsForList().leftPush("queue:refunds", new ProcessRefundJob(refund.getId()));

        return ResponseEntity.status(201).body(refund);
    }

    @GetMapping("/refunds/{id}")
    public ResponseEntity<?> getRefund(
            @PathVariable String id,
            @RequestHeader("X-Api-Key") String apiKey,
            @RequestHeader("X-Api-Secret") String apiSecret) {
        
        if (validateAuth(apiKey, apiSecret) == null) return ResponseEntity.status(401).build();
        
        Refund refund = refundRepository.findById(id).orElse(null);
        if (refund == null) return ResponseEntity.status(404).build();
        
        return ResponseEntity.ok(refund);
    }
}