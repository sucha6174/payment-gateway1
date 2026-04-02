package com.gateway.models;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Data // Auto-generates getters and setters
@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String name;
    private String email;
    private String apiKey;
    private String apiSecret;
    private String webhookUrl;
    private String webhookSecret;
}