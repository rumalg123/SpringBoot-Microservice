package com.rumal.api_gateway.config;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthHeaderRelayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    String subject = auth.getToken().getSubject();
                    String email = auth.getToken().getClaimAsString("email");

                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
                    if (subject != null && !subject.isBlank()) {
                        requestBuilder.header("X-Auth0-Sub", subject);
                    }
                    if (email != null && !email.isBlank()) {
                        requestBuilder.header("X-Auth0-Email", email);
                    }

                    return exchange.mutate().request(requestBuilder.build()).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
