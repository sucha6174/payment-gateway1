package com.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.controllers.PaymentController;
import com.gateway.models.IdempotencyKey;
import com.gateway.models.Merchant;
import com.gateway.models.Order;
import com.gateway.models.Payment;
import com.gateway.repositories.IdempotencyKeyRepository;
import com.gateway.repositories.OrderRepository;
import com.gateway.repositories.PaymentRepository;
import com.gateway.services.AuthService;
import com.gateway.services.JobQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PaymentControllerTest {

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private IdempotencyKeyRepository idempotencyKeyRepository;
    private AuthService authService;
    private JobQueueService jobQueueService;
    private ObjectMapper objectMapper;
    private PaymentController paymentController;

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        idempotencyKeyRepository = mock(IdempotencyKeyRepository.class);
        authService = mock(AuthService.class);
        jobQueueService = mock(JobQueueService.class);
        objectMapper = new ObjectMapper();

        paymentController = new PaymentController(
                paymentRepository,
                orderRepository,
                idempotencyKeyRepository,
                authService,
                jobQueueService,
                objectMapper
        );

        testMerchant = new Merchant();
        testMerchant.setId(UUID.randomUUID());
        testMerchant.setApiKey("key_test_abc123");
        testMerchant.setApiSecret("secret_test_xyz789");

        when(authService.authenticate("key_test_abc123", "secret_test_xyz789")).thenReturn(testMerchant);
    }

    @Test
    void testIdempotencyCachedResponse() {
        String key = "req_unique_123";
        String cachedJson = "{\"id\":\"pay_cached123\",\"status\":\"pending\",\"amount\":50000}";
        IdempotencyKey idempKey = new IdempotencyKey(key, testMerchant.getId(), cachedJson);
        idempKey.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(idempotencyKeyRepository.findByKeyAndMerchantId(key, testMerchant.getId()))
                .thenReturn(Optional.of(idempKey));

        Map<String, Object> body = Map.of(
                "order_id", "order_123",
                "method", "upi",
                "vpa", "user@paytm"
        );

        ResponseEntity<?> response = paymentController.createPayment(
                "key_test_abc123",
                "secret_test_xyz789",
                key,
                body
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(paymentRepository, never()).save(any());
        verify(jobQueueService, never()).enqueuePayment(any());
    }

    @Test
    void testNewPaymentCreationEnqueuesJob() {
        Order order = new Order("order_test123", testMerchant.getId(), 50000, "INR", "rec_1");
        when(orderRepository.findById("order_test123")).thenReturn(Optional.of(order));

        Map<String, Object> body = Map.of(
                "order_id", "order_test123",
                "method", "upi",
                "vpa", "test@upi"
        );

        ResponseEntity<?> response = paymentController.createPayment(
                "key_test_abc123",
                "secret_test_xyz789",
                null,
                body
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Payment created = (Payment) response.getBody();
        assertNotNull(created);
        assertEquals("pending", created.getStatus());
        assertEquals(50000, created.getAmount());

        verify(paymentRepository).saveAndFlush(any(Payment.class));
        verify(jobQueueService).enqueuePayment(any());
    }
}
