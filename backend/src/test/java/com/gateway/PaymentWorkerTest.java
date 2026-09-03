package com.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.models.Payment;
import com.gateway.models.WebhookLog;
import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.PaymentRepository;
import com.gateway.repositories.WebhookLogRepository;
import com.gateway.services.JobQueueService;
import com.gateway.workers.PaymentWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PaymentWorkerTest {

    private PaymentRepository paymentRepository;
    private MerchantRepository merchantRepository;
    private WebhookLogRepository webhookLogRepository;
    private JobQueueService jobQueueService;
    private ObjectMapper objectMapper;
    private PaymentWorker paymentWorker;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        merchantRepository = mock(MerchantRepository.class);
        webhookLogRepository = mock(WebhookLogRepository.class);
        jobQueueService = mock(JobQueueService.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        paymentWorker = new PaymentWorker(
                paymentRepository,
                merchantRepository,
                webhookLogRepository,
                jobQueueService,
                objectMapper
        );
    }

    @Test
    void testDeterministicSuccessInTestMode() {
        paymentWorker.setTestMode(true);
        paymentWorker.setTestProcessingDelay(50); // fast test delay
        paymentWorker.setTestPaymentSuccess(true);

        Payment payment = new Payment();
        payment.setId("pay_test_123456789012");
        payment.setMerchantId(UUID.randomUUID());
        payment.setOrderId("order_123");
        payment.setAmount(50000);
        payment.setCurrency("INR");
        payment.setMethod("upi");
        payment.setStatus("pending");

        when(paymentRepository.findById("pay_test_123456789012")).thenReturn(Optional.of(payment));
        when(webhookLogRepository.save(any(WebhookLog.class))).thenAnswer(i -> {
            WebhookLog l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        paymentWorker.processPayment(new ProcessPaymentJob("pay_test_123456789012"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        assertEquals("success", paymentCaptor.getValue().getStatus());
        verify(jobQueueService).enqueueWebhook(any());
        verify(jobQueueService).incrementCompleted();
    }

    @Test
    void testDeterministicFailureInTestMode() {
        paymentWorker.setTestMode(true);
        paymentWorker.setTestProcessingDelay(50);
        paymentWorker.setTestPaymentSuccess(false);

        Payment payment = new Payment();
        payment.setId("pay_test_987654321098");
        payment.setMerchantId(UUID.randomUUID());
        payment.setOrderId("order_456");
        payment.setAmount(25000);
        payment.setCurrency("INR");
        payment.setMethod("card");
        payment.setStatus("pending");

        when(paymentRepository.findById("pay_test_987654321098")).thenReturn(Optional.of(payment));
        when(webhookLogRepository.save(any(WebhookLog.class))).thenAnswer(i -> {
            WebhookLog l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        paymentWorker.processPayment(new ProcessPaymentJob("pay_test_987654321098"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        assertEquals("failed", paymentCaptor.getValue().getStatus());
        assertEquals("PAYMENT_FAILED", paymentCaptor.getValue().getErrorCode());
        verify(jobQueueService).enqueueWebhook(any());
    }
}
