package com.gateway.repositories;

import com.gateway.models.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {
    // Find logs that are 'pending' and the retry time has passed
    List<WebhookLog> findByStatusAndNextRetryAtBefore(String status, LocalDateTime now);
    
    // Find all logs for a specific merchant (for the dashboard)
    List<WebhookLog> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}