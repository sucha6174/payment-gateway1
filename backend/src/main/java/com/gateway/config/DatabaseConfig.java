package com.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Value("${spring.datasource.url:}")
    private String springDatasourceUrl;

    @Value("${spring.datasource.username:gateway_user}")
    private String springDatasourceUsername;

    @Value("${spring.datasource.password:gateway_pass}")
    private String springDatasourcePassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        if (databaseUrl != null && !databaseUrl.trim().isEmpty()) {
            try {
                String cleanUrl = databaseUrl;
                if (cleanUrl.startsWith("jdbc:")) {
                    cleanUrl = cleanUrl.substring(5);
                }
                URI uri = new URI(cleanUrl);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                String userInfo = uri.getUserInfo();

                String username = springDatasourceUsername;
                String password = springDatasourcePassword;

                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    password = parts[1];
                }

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                return DataSourceBuilder.create()
                        .driverClassName("org.postgresql.Driver")
                        .url(jdbcUrl)
                        .username(username)
                        .password(password)
                        .build();
            } catch (Exception e) {
                // Fallback to standard spring properties
            }
        }

        String fallbackUrl = springDatasourceUrl != null && !springDatasourceUrl.isEmpty() 
                ? springDatasourceUrl 
                : "jdbc:postgresql://localhost:5432/payment_gateway";

        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(fallbackUrl)
                .username(springDatasourceUsername)
                .password(springDatasourcePassword)
                .build();
    }
}
