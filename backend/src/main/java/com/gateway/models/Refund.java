package com.gateway.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    private String id; // rfnd_...

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "merchant_id")
    private java.util.UUID merchantId;

    private Integer amount;
    private String reason;
    private String status; // pending, processed

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime processedAt;
}