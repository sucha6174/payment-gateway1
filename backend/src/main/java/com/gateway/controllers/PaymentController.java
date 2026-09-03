package com.gateway.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.models.IdempotencyKey;
import com.gateway.models.Merchant;
import com.gateway.models.Order;
import com.gateway.models.Payment;
import com.gateway.repositories.IdempotencyKeyRepository;
import com.gateway.repositories.OrderRepository;
import com.gateway.repositories.PaymentRepository;
import com.gateway.services.AuthService;
import com.gateway.services.JobQueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuthService authService;
    private final JobQueueService jobQueueService;
    private final ObjectMapper objectMapper;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    public PaymentController(PaymentRepository paymentRepository,
                             OrderRepository orderRepository,
                             IdempotencyKeyRepository idempotencyKeyRepository,
                             AuthService authService,
                             JobQueueService jobQueueService,
                             ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.authService = authService;
        this.jobQueueService = jobQueueService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createPayment(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody Map<String, Object> body) {

        if (apiKey == null || apiKey.trim().isEmpty() || apiSecret == null || apiSecret.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Missing API credentials. Both X-Api-Key and X-Api-Secret are required for server-to-server payment requests.")
            ));
        }

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        return doCreatePayment(merchant, idempotencyKeyHeader, body);
    }

    @PostMapping({"/public", "/hosted"})
    @Transactional
    public ResponseEntity<?> createHostedPayment(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody Map<String, Object> body) {

        String apiKey = (apiKeyHeader != null && !apiKeyHeader.trim().isEmpty())
                ? apiKeyHeader.trim()
                : (body != null && body.containsKey("key") ? String.valueOf(body.get("key")).trim() : null);

        if (apiKey == null && body != null && body.containsKey("api_key")) {
            apiKey = String.valueOf(body.get("api_key")).trim();
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Missing API Key")
            ));
        }

        Merchant merchant = authService.authenticateKeyOnly(apiKey);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API key")
            ));
        }

        String idempKey = idempotencyKeyHeader;
        if ((idempKey == null || idempKey.trim().isEmpty()) && body != null && body.containsKey("idempotency_key")) {
            idempKey = String.valueOf(body.get("idempotency_key"));
        }

        return doCreatePayment(merchant, idempKey, body);
    }

    private ResponseEntity<?> doCreatePayment(Merchant merchant, String idempotencyKeyHeader, Map<String, Object> body) {

        // Idempotency Key Handling
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.trim().isEmpty()) {
            String trimmedKey = idempotencyKeyHeader.trim();
            Optional<IdempotencyKey> existingKeyOpt = idempotencyKeyRepository.findByKeyAndMerchantId(trimmedKey, merchant.getId());
            if (existingKeyOpt.isPresent()) {
                IdempotencyKey existingKey = existingKeyOpt.get();
                if (existingKey.getExpiresAt().isAfter(Instant.now())) {
                    // Return cached response
                    try {
                        Map<String, Object> cachedResponse = objectMapper.readValue(
                                existingKey.getResponse(),
                                new TypeReference<Map<String, Object>>() {}
                        );
                        return ResponseEntity.status(HttpStatus.CREATED).body(cachedResponse);
                    } catch (Exception e) {
                        // If parsing fails, proceed with processing
                    }
                } else {
                    idempotencyKeyRepository.deleteByKeyAndMerchantId(trimmedKey, merchant.getId());
                }
            }
        }

        // Validate Order
        if (!body.containsKey("order_id") || !(body.get("order_id") instanceof String)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Field 'order_id' is required")
            ));
        }

        String orderId = (String) body.get("order_id");
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Order not found")
            ));
        }

        Order order = orderOpt.get();
        if (!order.getMerchantId().equals(merchant.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Order does not belong to merchant")
            ));
        }

        // Validate Method
        if (!body.containsKey("method") || !(body.get("method") instanceof String)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Field 'method' is required")
            ));
        }

        String method = ((String) body.get("method")).toLowerCase();
        if (!"upi".equals(method) && !"card".equals(method)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Invalid payment method. Supported: upi, card")
            ));
        }

        Payment payment = new Payment();
        payment.setId(generatePaymentId());
        payment.setOrderId(orderId);
        payment.setMerchantId(merchant.getId());
        payment.setAmount(order.getAmount());
        payment.setCurrency(order.getCurrency());
        payment.setMethod(method);
        payment.setStatus("pending");
        payment.setCaptured(false);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());

        if ("upi".equals(method)) {
            String vpa = (String) body.get("vpa");
            if (vpa == null || vpa.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Field 'vpa' is required for UPI")
                ));
            }
            payment.setVpa(vpa.trim());
        } else {
            String cardNumber = (String) body.get("card_number");
            Object expMonthObj = body.get("card_exp_month");
            Object expYearObj = body.get("card_exp_year");
            String cardHolder = (String) body.get("card_holder");

            if (cardNumber == null || expMonthObj == null || expYearObj == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Card details (card_number, card_exp_month, card_exp_year) are required")
                ));
            }
            payment.setCardNumber(cardNumber);
            payment.setCardExpMonth(((Number) expMonthObj).intValue());
            payment.setCardExpYear(((Number) expYearObj).intValue());
            payment.setCardHolder(cardHolder);
        }

        paymentRepository.saveAndFlush(payment);

        // Cache response if Idempotency-Key was provided
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.trim().isEmpty()) {
            try {
                String responseJson = objectMapper.writeValueAsString(payment);
                IdempotencyKey idempKey = new IdempotencyKey(
                        idempotencyKeyHeader.trim(),
                        merchant.getId(),
                        responseJson
                );
                idempotencyKeyRepository.saveAndFlush(idempKey);
            } catch (Exception e) {
                // non-fatal
            }
        }

        // Enqueue background processing job to Redis
        jobQueueService.enqueuePayment(new ProcessPaymentJob(payment.getId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<?> capturePayment(
            @PathVariable("paymentId") String paymentId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret,
            @RequestBody(required = false) Map<String, Object> body) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        Optional<Payment> paymentOpt = paymentRepository.findByIdAndMerchantId(paymentId, merchant.getId());
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND_ERROR", "description", "Payment not found")
            ));
        }

        Payment payment = paymentOpt.get();
        if (!"success".equalsIgnoreCase(payment.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Payment not in capturable state")
            ));
        }

        payment.setCaptured(true);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);

        return ResponseEntity.ok(payment);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPayment(
            @PathVariable("paymentId") String paymentId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        Merchant merchant = null;
        if (apiKey != null && apiSecret != null) {
            merchant = authService.authenticate(apiKey, apiSecret);
        } else if (apiKey != null) {
            merchant = authService.authenticateKeyOnly(apiKey);
        }

        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND_ERROR", "description", "Payment not found")
            ));
        }

        Payment payment = paymentOpt.get();
        if (merchant != null && !payment.getMerchantId().equals(merchant.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", Map.of("code", "FORBIDDEN_ERROR", "description", "Access denied")
            ));
        }

        return ResponseEntity.ok(payment);
    }

    @GetMapping
    public ResponseEntity<?> listPayments(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        List<Payment> payments = paymentRepository.findByMerchantId(merchant.getId());
        return ResponseEntity.ok(payments);
    }

    private String generatePaymentId() {
        StringBuilder sb = new StringBuilder("pay_");
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
