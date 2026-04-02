package com.gateway.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private String id; // pay_...

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    private String orderId;
    private Integer amount;
    private String currency;
    private String method;
    private String status; // pending, success, failed
    private Boolean captured;
    private String vpa;
    private String errorCode;
    private String errorDescription;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}