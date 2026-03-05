package com.rumal.payment_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final byte[] sharedSecretBytes;

    public SecurityConfig(@Value("${internal.auth.shared-secret:}") String sharedSecret) {
        this.sharedSecretBytes = (sharedSecret == null ? "" : sharedSecret.trim()).getBytes(StandardCharsets.UTF_8);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .anyRequest().access((authentication, context) ->
                                new AuthorizationDecision(hasValidInternalHeader(context.getRequest().getHeader("X-Internal-Auth"))))
                )
                .build();
    }

    private boolean hasValidInternalHeader(String headerValue) {
        if (sharedSecretBytes.length == 0 || headerValue == null || headerValue.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(sharedSecretBytes, headerValue.trim().getBytes(StandardCharsets.UTF_8));
    }
}
