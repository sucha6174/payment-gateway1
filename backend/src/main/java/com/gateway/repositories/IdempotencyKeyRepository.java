package com.gateway.repositories;

import com.gateway.models.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKey.IdempotencyKeyId> {
}