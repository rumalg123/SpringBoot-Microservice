package com.rumal.product_service.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * H-03: Caches the request body for POST/PUT/PATCH requests that carry HMAC signatures,
 * so InternalRequestVerifier can hash the body without consuming the input stream.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InternalRequestBodyCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String method = httpRequest.getMethod();
        boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        boolean hasHmac = httpRequest.getHeader("X-Internal-Signature") != null;
        if (hasBody && hasHmac) {
            chain.doFilter(new CachedBodyRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    public static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        public byte[] getCachedBody() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return bais.read(); }
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {}
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
