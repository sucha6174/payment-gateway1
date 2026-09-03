package com.gateway.jobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.UUID;

public class DeliverWebhookJob implements Serializable {
    @JsonProperty("merchant_id")
    private UUID merchantId;

    @JsonProperty("webhook_log_id")
    private UUID webhookLogId;

    @JsonProperty("event")
    private String event;

    @JsonProperty("payload")
    private String payload;

    public DeliverWebhookJob() {}

    public DeliverWebhookJob(UUID merchantId, UUID webhookLogId, String event, String payload) {
        this.merchantId = merchantId;
        this.webhookLogId = webhookLogId;
        this.event = event;
        this.payload = payload;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public UUID getWebhookLogId() {
        return webhookLogId;
    }

    public void setWebhookLogId(UUID webhookLogId) {
        this.webhookLogId = webhookLogId;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
