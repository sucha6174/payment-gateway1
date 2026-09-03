package com.gateway.repositories;

import com.gateway.models.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, String> {
    List<Refund> findByPaymentId(String paymentId);
    Optional<Refund> findByIdAndMerchantId(String id, UUID merchantId);
    List<Refund> findByMerchantId(UUID merchantId);
}
