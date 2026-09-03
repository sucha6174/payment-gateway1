package com.gateway.controllers;

import com.gateway.jobs.ProcessRefundJob;
import com.gateway.models.Merchant;
import com.gateway.models.Payment;
import com.gateway.models.Refund;
import com.gateway.repositories.PaymentRepository;
import com.gateway.repositories.RefundRepository;
import com.gateway.services.AuthService;
import com.gateway.services.JobQueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
public class RefundController {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final AuthService authService;
    private final JobQueueService jobQueueService;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    public RefundController(RefundRepository refundRepository,
                            PaymentRepository paymentRepository,
                            AuthService authService,
                            JobQueueService jobQueueService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.authService = authService;
        this.jobQueueService = jobQueueService;
    }

    @PostMapping("/api/v1/payments/{paymentId}/refunds")
    @Transactional
    public ResponseEntity<?> createRefund(
            @PathVariable("paymentId") String paymentId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret,
            @RequestBody Map<String, Object> body) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        Optional<Payment> paymentOpt = paymentRepository.findByIdAndMerchantId(paymentId, merchant.getId());
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Payment not found or does not belong to merchant")
            ));
        }

        Payment payment = paymentOpt.get();
        if (!"success".equalsIgnoreCase(payment.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Payment status is not success")
            ));
        }

        if (!body.containsKey("amount") || !(body.get("amount") instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Field 'amount' is required and must be an integer")
            ));
        }

        int requestedAmount = ((Number) body.get("amount")).intValue();
        if (requestedAmount <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Refund amount must be greater than zero")
            ));
        }

        String reason = (String) body.get("reason");

        // Calculate total already refunded (sum of processed and pending refunds)
        List<Refund> existingRefunds = refundRepository.findByPaymentId(paymentId);
        int totalRefunded = 0;
        for (Refund r : existingRefunds) {
            if ("processed".equalsIgnoreCase(r.getStatus()) || "pending".equalsIgnoreCase(r.getStatus())) {
                totalRefunded += r.getAmount();
            }
        }

        int availableAmount = payment.getAmount() - totalRefunded;
        if (requestedAmount > availableAmount) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Refund amount exceeds available amount")
            ));
        }

        String refundId = generateRefundId();
        while (refundRepository.existsById(refundId)) {
            refundId = generateRefundId();
        }

        Refund refund = new Refund(refundId, paymentId, merchant.getId(), requestedAmount, reason);
        refundRepository.save(refund);

        // Enqueue background processing job
        jobQueueService.enqueueRefund(new ProcessRefundJob(refund.getId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(refund);
    }

    @GetMapping("/api/v1/refunds/{refundId}")
    public ResponseEntity<?> getRefund(
            @PathVariable("refundId") String refundId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        Optional<Refund> refundOpt = refundRepository.findByIdAndMerchantId(refundId, merchant.getId());
        if (refundOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND_ERROR", "description", "Refund not found")
            ));
        }

        return ResponseEntity.ok(refundOpt.get());
    }

    private String generateRefundId() {
        StringBuilder sb = new StringBuilder("rfnd_");
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
