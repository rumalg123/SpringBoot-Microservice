package com.rumal.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TrustedProxyResolver {

    private final Set<String> trustedProxyExactIps;
    private final List<CidrRange> trustedProxyCidrs;

    public TrustedProxyResolver(
            @Value("${RATE_LIMIT_TRUSTED_PROXY_IPS:127.0.0.1,::1}") String trustedProxyIps
    ) {
        Set<String> exactIps = new HashSet<>();
        List<CidrRange> cidrs = new ArrayList<>();

        for (String entry : trustedProxyIps.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) continue;
            if (trimmed.contains("/")) {
                CidrRange range = CidrRange.parse(trimmed);
                if (range != null) cidrs.add(range);
            } else {
                exactIps.add(trimmed);
            }
        }
        this.trustedProxyExactIps = Set.copyOf(exactIps);
        this.trustedProxyCidrs = List.copyOf(cidrs);
    }

    public String resolveClientIp(ServerWebExchange exchange) {
        String remoteIp = extractRemoteIp(exchange);
        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }

        String cfConnectingIp = exchange.getRequest().getHeaders().getFirst("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }

        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0].trim();
            }
        }

        return remoteIp;
    }

    private String extractRemoteIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        String ip = remoteAddress.getAddress().getHostAddress();
        if (ip != null && ip.startsWith("::ffff:")) {
            return ip.substring("::ffff:".length());
        }
        return ip;
    }

    private boolean isTrustedProxy(String remoteIp) {
        if (trustedProxyExactIps.contains(remoteIp)) {
            return true;
        }
        if (trustedProxyCidrs.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(remoteIp);
            byte[] addrBytes = addr.getAddress();
            for (CidrRange cidr : trustedProxyCidrs) {
                if (cidr.contains(addrBytes)) return true;
            }
        } catch (UnknownHostException ignored) {
        }
        return false;
    }

    private record CidrRange(byte[] network, int prefixLength) {
        static CidrRange parse(String cidr) {
            try {
                String[] parts = cidr.split("/");
                InetAddress addr = InetAddress.getByName(parts[0].trim());
                int prefix = Integer.parseInt(parts[1].trim());
                return new CidrRange(addr.getAddress(), prefix);
            } catch (Exception e) {
                return null;
            }
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) return false;
            }
            if (remainingBits > 0 && fullBytes < network.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                return (address[fullBytes] & mask) == (network[fullBytes] & mask);
            }
            return true;
        }
    }
}
