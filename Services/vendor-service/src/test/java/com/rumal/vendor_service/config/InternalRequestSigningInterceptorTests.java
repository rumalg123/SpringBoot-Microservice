package com.rumal.vendor_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class InternalRequestSigningInterceptorTests {

    @Test
    void addsHmacHeadersWhenInternalAuthHeaderIsPresent() throws IOException {
        InternalRequestSigningInterceptor interceptor = new InternalRequestSigningInterceptor("shared-secret");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://product-service/internal/products/cache/vendors/00000000-0000-0000-0000-000000000001/evict"));
        request.getHeaders().set("X-Internal-Auth", "shared-secret");

        interceptor.intercept(request, new byte[0], (req, payload) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst("X-Internal-Timestamp")).isNotBlank();
        assertThat(request.getHeaders().getFirst("X-Internal-Signature")).isNotBlank();
        assertThat(request.getHeaders().getFirst("X-Internal-Path")).isEqualTo("/internal/products/cache/vendors/00000000-0000-0000-0000-000000000001/evict");
        assertThat(request.getHeaders().getFirst("X-Internal-Body-Hash")).isEqualTo("");
    }

    @Test
    void skipsHmacHeadersWhenInternalAuthHeaderIsMissing() throws IOException {
        InternalRequestSigningInterceptor interceptor = new InternalRequestSigningInterceptor("shared-secret");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://product-service/internal/products/cache/vendors/00000000-0000-0000-0000-000000000001/evict"));

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst("X-Internal-Timestamp")).isNull();
        assertThat(request.getHeaders().getFirst("X-Internal-Signature")).isNull();
        assertThat(request.getHeaders().getFirst("X-Internal-Path")).isNull();
        assertThat(request.getHeaders().getFirst("X-Internal-Body-Hash")).isNull();
    }
}
