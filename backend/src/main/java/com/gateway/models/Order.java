package com.gateway.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "merchant_id", nullable = false)
    @JsonProperty("merchant_id")
    private UUID merchantId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    private String receipt;

    @Column(nullable = false, length = 20)
    private String status = "created";

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private Instant createdAt = Instant.now();

    public Order() {}

    public Order(String id, UUID merchantId, Integer amount, String currency, String receipt) {
        this.id = id;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = (currency != null && !currency.isEmpty()) ? currency : "INR";
        this.receipt = receipt;
        this.status = "created";
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
