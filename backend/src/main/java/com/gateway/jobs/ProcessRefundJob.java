package com.gateway.jobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class ProcessRefundJob implements Serializable {
    @JsonProperty("refund_id")
    private String refundId;

    public ProcessRefundJob() {}

    public ProcessRefundJob(String refundId) {
        this.refundId = refundId;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }
}
