package com.gateway.repositories;

import com.gateway.models.IdempotencyKey;
import com.gateway.models.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {
    Optional<IdempotencyKey> findByKeyAndMerchantId(String key, UUID merchantId);
    void deleteByKeyAndMerchantId(String key, UUID merchantId);
}
