package com.gateway.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Import this
import lombok.Data;
import lombok.NoArgsConstructor; // Import this
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.IdempotencyKeyId.class)
public class IdempotencyKey {

    @Id
    private String key;

    @Id
    @Column(name = "merchant_id")
    private UUID merchantId;

    private String response;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    // Helper class for Composite Primary Key
    @Data
    @AllArgsConstructor // <--- REQUIRED: Generates constructor(key, merchantId)
    @NoArgsConstructor  // <--- REQUIRED: Generates constructor()
    public static class IdempotencyKeyId implements Serializable {
        private String key;
        private UUID merchantId;
    }
}