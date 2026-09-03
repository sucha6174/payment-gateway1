package com.gateway.repositories;

import com.gateway.models.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByApiKey(String apiKey);
    Optional<Merchant> findByApiKeyAndApiSecret(String apiKey, String apiSecret);
}
