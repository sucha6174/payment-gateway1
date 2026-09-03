package com.gateway.services;

import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class WebhookSignerService {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * Generates HMAC-SHA256 signature for the given payload using the secret.
     * The payload must be the exact raw JSON string sent in the request body.
     */
    public String generateSignature(String payload, String secret) {
        if (payload == null || secret == null) {
            throw new IllegalArgumentException("Payload and secret must not be null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hmacBytes).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC-SHA256 signature", e);
        }
    }

    /**
     * Verifies if a given signature matches the expected signature.
     */
    public boolean verifySignature(String payload, String signature, String secret) {
        if (signature == null || payload == null || secret == null) {
            return false;
        }
        String expected = generateSignature(payload, secret);
        return expected.equalsIgnoreCase(signature.trim());
    }
}
