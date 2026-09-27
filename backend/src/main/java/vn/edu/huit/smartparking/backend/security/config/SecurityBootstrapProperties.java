package vn.edu.huit.smartparking.backend.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smart-parking.security.bootstrap")
public record SecurityBootstrapProperties(String username, String password, String fullName) {}
