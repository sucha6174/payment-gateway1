package com.gateway.controllers;

import com.gateway.models.Merchant;
import com.gateway.models.Order;
import com.gateway.repositories.OrderRepository;
import com.gateway.services.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository orderRepository;
    private final AuthService authService;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    public OrderController(OrderRepository orderRepository, AuthService authService) {
        this.orderRepository = orderRepository;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret,
            @RequestBody Map<String, Object> body) {

        Merchant merchant = authService.authenticate(apiKey, apiSecret);
        if (merchant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "AUTHENTICATION_ERROR", "description", "Invalid API credentials")
            ));
        }

        if (!body.containsKey("amount") || !(body.get("amount") instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Field 'amount' is required and must be an integer")
            ));
        }

        int amount = ((Number) body.get("amount")).intValue();
        if (amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Field 'amount' must be greater than 0")
            ));
        }

        String currency = (String) body.getOrDefault("currency", "INR");
        String receipt = (String) body.get("receipt");

        String orderId = generateOrderId();
        Order order = new Order(orderId, merchant.getId(), amount, currency, receipt);
        orderRepository.save(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(
            @PathVariable("orderId") String orderId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Api-Secret", required = false) String apiSecret) {

        // Allow lookup with Key only (for SDK/checkout frontend) or with Key+Secret
        Merchant merchant = null;
        if (apiKey != null && apiSecret != null) {
            merchant = authService.authenticate(apiKey, apiSecret);
        } else if (apiKey != null) {
            merchant = authService.authenticateKeyOnly(apiKey);
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND_ERROR", "description", "Order not found")
            ));
        }

        Order order = orderOpt.get();
        if (merchant != null && !order.getMerchantId().equals(merchant.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", Map.of("code", "FORBIDDEN_ERROR", "description", "Access denied")
            ));
        }

        return ResponseEntity.ok(order);
    }

    private String generateOrderId() {
        StringBuilder sb = new StringBuilder("order_");
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
