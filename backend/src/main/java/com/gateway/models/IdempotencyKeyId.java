package com.gateway.models;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class IdempotencyKeyId implements Serializable {
    private String key;
    private UUID merchantId;

    public IdempotencyKeyId() {}

    public IdempotencyKeyId(String key, UUID merchantId) {
        this.key = key;
        this.merchantId = merchantId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKeyId that = (IdempotencyKeyId) o;
        return Objects.equals(key, that.key) && Objects.equals(merchantId, that.merchantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, merchantId);
    }
}
