package com.gateway;

import com.gateway.services.WebhookSignerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WebhookSignerTest {

    private WebhookSignerService signerService;

    @BeforeEach
    void setUp() {
        signerService = new WebhookSignerService();
    }

    @Test
    void testHmacSha256SignatureGeneration() {
        String payload = "{\"event\":\"payment.success\",\"timestamp\":1705315870}";
        String secret = "whsec_test_abc123";

        String signature = signerService.generateSignature(payload, secret);

        assertNotNull(signature);
        assertEquals(64, signature.length(), "HMAC-SHA256 signature should be exactly 64 hex characters");

        // Verify repeatability
        String signature2 = signerService.generateSignature(payload, secret);
        assertEquals(signature, signature2);
    }

    @Test
    void testSignatureVerification() {
        String payload = "{\"event\":\"payment.success\"}";
        String secret = "whsec_test_abc123";

        String signature = signerService.generateSignature(payload, secret);

        assertTrue(signerService.verifySignature(payload, signature, secret));
        assertFalse(signerService.verifySignature(payload, "invalid_signature", secret));
        assertFalse(signerService.verifySignature("{\"altered\":true}", signature, secret));
    }
}
