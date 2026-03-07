package com.rumal.inventory_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpClientConfigTest {

    @Test
    void loadBalancedBuilderAddsHmacHeadersForInternalRequests() {
        HttpClientConfig config = new HttpClientConfig();
        ReflectionTestUtils.setField(config, "connectTimeoutSeconds", 2);
        ReflectionTestUtils.setField(config, "responseTimeoutSeconds", 5);
        ReflectionTestUtils.setField(config, "idleEvictSeconds", 30);
        ReflectionTestUtils.setField(config, "maxConnections", 100);
        ReflectionTestUtils.setField(config, "maxConnectionsPerRoute", 20);
        ReflectionTestUtils.setField(config, "internalAuthSharedSecret", "shared-secret");

        RestClient.Builder builder = config.loadBalancedRestClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://vendor-service").build();

        server.expect(requestTo("http://vendor-service/internal/vendors/access/by-keycloak/user-123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> {
                    String timestamp = request.getHeaders().getFirst("X-Internal-Timestamp");
                    String signature = request.getHeaders().getFirst("X-Internal-Signature");
                    String path = request.getHeaders().getFirst("X-Internal-Path");
                    String bodyHash = request.getHeaders().getFirst("X-Internal-Body-Hash");

                    assertEquals("shared-secret", request.getHeaders().getFirst("X-Internal-Auth"));
                    assertNotNull(timestamp);
                    assertFalse(timestamp.isBlank());
                    assertEquals("/internal/vendors/access/by-keycloak/user-123", path);
                    assertEquals("", bodyHash);
                    assertEquals(
                            computeHmac("shared-secret", timestamp + ":GET:" + path + ":" + bodyHash),
                            signature
                    );
                })
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        String response = client.get()
                .uri("/internal/vendors/access/by-keycloak/{keycloakUserId}", "user-123")
                .header("X-Internal-Auth", "shared-secret")
                .retrieve()
                .body(String.class);

        assertEquals("[]", response);
        server.verify();
    }

    private String computeHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute expected HMAC for test", ex);
        }
    }
}
