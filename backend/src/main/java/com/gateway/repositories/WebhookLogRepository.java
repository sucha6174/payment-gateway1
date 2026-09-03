package com.gateway.repositories;

import com.gateway.models.WebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {
    Page<WebhookLog> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);
    List<WebhookLog> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    Optional<WebhookLog> findByIdAndMerchantId(UUID id, UUID merchantId);
    long countByMerchantId(UUID merchantId);
    List<WebhookLog> findByStatusAndNextRetryAtBefore(String status, Instant nextRetryAt);
}
