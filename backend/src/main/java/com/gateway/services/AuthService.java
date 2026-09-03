package com.gateway.services;

import com.gateway.models.Merchant;
import com.gateway.repositories.MerchantRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final MerchantRepository merchantRepository;

    public AuthService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public Merchant authenticate(String apiKey, String apiSecret) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiSecret == null || apiSecret.trim().isEmpty()) {
            return null;
        }
        Optional<Merchant> merchantOpt = merchantRepository.findByApiKeyAndApiSecret(apiKey.trim(), apiSecret.trim());
        return merchantOpt.orElse(null);
    }

    public Merchant authenticateKeyOnly(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        return merchantRepository.findByApiKey(apiKey.trim()).orElse(null);
    }
}
