package com.gateway.controllers;

import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.models.IdempotencyKey;
import com.gateway.models.Merchant;
import com.gateway.models.Payment;
import com.gateway.repositories.IdempotencyKeyRepository;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin // Allow frontend to call this
public class PaymentController {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    // Helper: Validate API Keys
    private Merchant validateAuth(String apiKey, String apiSecret) {
        Merchant merchant = merchantRepository.findByApiKey(apiKey).orElse(null);
        if (merchant != null && merchant.getApiSecret().equals(apiSecret)) {
            return merchant;
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<?> createPayment(
            @RequestHeader("X-Api-Key") String apiKey,
            @RequestHeader("X-Api-Secret") String apiSecret,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> request) {

        // 1. Authentication
        Merchant merchant = validateAuth(apiKey, apiSecret);
        if (merchant == null) return ResponseEntity.status(401).body("Invalid Credentials");

        // 2. Idempotency Check (Prevent duplicate charges)
        if (idempotencyKey != null) {
            IdempotencyKey keyRecord = idempotencyKeyRepository.findById(new IdempotencyKey.IdempotencyKeyId(idempotencyKey, merchant.getId())).orElse(null);
            if (keyRecord != null) {
                if (keyRecord.getExpiresAt().isAfter(LocalDateTime.now())) {
                    // Return Cached Response
                    return ResponseEntity.status(201).body(keyRecord.getResponse());
                } else {
                    idempotencyKeyRepository.delete(keyRecord); // Expired, delete and retry
                }
            }
        }

        // 3. Create Payment Record
        Payment payment = new Payment();
        payment.setId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 15));
        payment.setMerchant(merchant);
        payment.setOrderId((String) request.get("order_id"));
        payment.setAmount((Integer) request.get("amount"));
        payment.setCurrency((String) request.get("currency"));
        payment.setMethod((String) request.get("method"));
        payment.setVpa((String) request.get("vpa"));
        payment.setStatus("pending"); // Initial state
        payment.setCaptured(false);
        
        paymentRepository.save(payment);

        // 4. Send to Worker (Redis)
        redisTemplate.opsForList().leftPush("queue:payments", new ProcessPaymentJob(payment.getId()));

        // 5. Construct Response
        JSONObject response = new JSONObject();
        response.put("id", payment.getId());
        response.put("order_id", payment.getOrderId());
        response.put("amount", payment.getAmount());
        response.put("status", payment.getStatus());
        response.put("created_at", payment.getCreatedAt().toString());

        // 6. Save Idempotency Key
        if (idempotencyKey != null) {
            IdempotencyKey newKey = new IdempotencyKey();
            newKey.setKey(idempotencyKey);
            newKey.setMerchantId(merchant.getId());
            newKey.setResponse(response.toString());
            newKey.setCreatedAt(LocalDateTime.now());
            newKey.setExpiresAt(LocalDateTime.now().plusHours(24));
            idempotencyKeyRepository.save(newKey);
        }

        return ResponseEntity.status(201).body(response.toMap());
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<?> capturePayment(
            @PathVariable String id,
            @RequestHeader("X-Api-Key") String apiKey,
            @RequestHeader("X-Api-Secret") String apiSecret) {

        Merchant merchant = validateAuth(apiKey, apiSecret);
        if (merchant == null) return ResponseEntity.status(401).body("Invalid Credentials");

        Payment payment = paymentRepository.findById(id).orElse(null);
        if (payment == null) return ResponseEntity.status(404).body("Payment not found");

        if (!"success".equals(payment.getStatus())) {
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Payment not in capturable state")));
        }

        payment.setCaptured(true);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return ResponseEntity.ok(Map.of("captured", true, "status", "success", "id", payment.getId()));
    }
}