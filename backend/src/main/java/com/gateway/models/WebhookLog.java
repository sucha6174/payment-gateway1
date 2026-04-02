package com.gateway.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "webhook_logs")
public class WebhookLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "merchant_id")
    private UUID merchantId;

    private String event;
    private String payload; // Stores JSON as string
    private String status; // pending, success, failed
    private Integer attempts;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextRetryAt;
    private Integer responseCode;
    private String responseBody;
    
    private LocalDateTime createdAt = LocalDateTime.now();
}