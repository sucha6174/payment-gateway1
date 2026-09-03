package com.gateway;

import com.gateway.repositories.MerchantRepository;
import com.gateway.repositories.WebhookLogRepository;
import com.gateway.services.WebhookDeliveryService;
import com.gateway.services.WebhookSignerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class WebhookDeliveryServiceTest {

    private WebhookDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        WebhookLogRepository webhookLogRepository = mock(WebhookLogRepository.class);
        WebhookSignerService signerService = mock(WebhookSignerService.class);

        deliveryService = new WebhookDeliveryService(merchantRepository, webhookLogRepository, signerService);
    }

    @Test
    void testProductionRetryDelays() {
        deliveryService.setTestWebhookRetry(false);

        // Attempt 1: Immediate (0s)
        assertEquals(0L, deliveryService.getRetryDelaySeconds(1));
        // Attempt 2: 1 minute (60s)
        assertEquals(60L, deliveryService.getRetryDelaySeconds(2));
        // Attempt 3: 5 minutes (300s)
        assertEquals(300L, deliveryService.getRetryDelaySeconds(3));
        // Attempt 4: 30 minutes (1800s)
        assertEquals(1800L, deliveryService.getRetryDelaySeconds(4));
        // Attempt 5: 2 hours (7200s)
        assertEquals(7200L, deliveryService.getRetryDelaySeconds(5));
    }

    @Test
    void testTestModeRetryDelays() {
        deliveryService.setTestWebhookRetry(true);

        // Test Intervals: 0s, 5s, 10s, 15s, 20s
        assertEquals(0L, deliveryService.getRetryDelaySeconds(1));
        assertEquals(5L, deliveryService.getRetryDelaySeconds(2));
        assertEquals(10L, deliveryService.getRetryDelaySeconds(3));
        assertEquals(15L, deliveryService.getRetryDelaySeconds(4));
        assertEquals(20L, deliveryService.getRetryDelaySeconds(5));
    }
}
