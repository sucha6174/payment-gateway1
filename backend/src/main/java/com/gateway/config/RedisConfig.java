package com.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;

@Configuration
public class RedisConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisConfig.class);

    @Value("${REDIS_URL:}")
    private String redisUrl;

    @Value("${spring.data.redis.host:redis}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config;
        if (redisUrl != null && !redisUrl.trim().isEmpty()) {
            try {
                URI uri = new URI(redisUrl);
                String host = uri.getHost() != null ? uri.getHost() : "redis";
                int port = uri.getPort() == -1 ? 6379 : uri.getPort();

                log.info("Configuring Redis connection from REDIS_URL -> host: {}, port: {}", host, port);
                config = new RedisStandaloneConfiguration(host, port);
                if (uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":", 2);
                    if (parts.length > 1) {
                        config.setPassword(parts[1]);
                    } else if (parts.length == 1 && !parts[0].isEmpty()) {
                        config.setPassword(parts[0]);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse REDIS_URL '{}', falling back to host={}:{}", redisUrl, redisHost, redisPort, e);
                config = new RedisStandaloneConfiguration(redisHost, redisPort);
            }
        } else {
            log.info("Configuring Redis connection from host={}:{}", redisHost, redisPort);
            config = new RedisStandaloneConfiguration(redisHost, redisPort);
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
