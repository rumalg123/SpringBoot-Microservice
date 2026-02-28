# Global Audit Report: E-Commerce Microservices Platform

**Audit Date:** 2026-02-27
**Auditor:** Principal Software Architect / Senior Security Researcher
**Scope:** Full A-to-Z audit of 18 Spring Boot microservices (975 Java source files)
**Platform:** Java 21, Spring Boot 4.0.3, Spring Cloud 2025.1.1

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Critical Findings](#2-critical-findings)
3. [High Severity Findings](#3-high-severity-findings)
4. [Medium Severity Findings](#4-medium-severity-findings)
5. [Low Severity Findings](#5-low-severity-findings)
6. [Architectural Tech Debt](#6-architectural-tech-debt)
7. [What Is Done Well](#7-what-is-done-well)

---

## 1. Executive Summary

**Total Issues Found: 62**

| Severity | Count |
|----------|-------|
| Critical | 9 |
| High | 16 |
| Medium | 21 |
| Low | 10 |
| Architectural Tech Debt | 6 |

The platform demonstrates strong security fundamentals in several areas (idempotency, price integrity at checkout, header sanitization, audience validation). However, critical gaps exist in infrastructure security (unauthenticated Eureka, zero CI/CD validation), defense-in-depth (no JWT verification at service level), and payment integrity (refund ownership bypass, webhook amount not validated).

---

## 2. Critical Findings

### C-01: Refund Requester Ownership Not Validated (Payment Service)

**Severity:** CRITICAL
**Category:** Authorization Bypass / Financial Integrity
**Impact:** Any authenticated customer can submit a refund request for any order if they know the order ID and vendor order ID.

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/service/RefundService.java`
**Lines:** 57-113

**Flaw:** The `createRefundRequest` method receives `keycloakId` from the controller but only checks that a payment exists for the order. It does NOT verify that the requesting customer's `keycloakId` matches the payment's `customerKeycloakId`.

**Fix:**
```java
// In RefundService.java, after line 63 (after fetching the payment):

if (!keycloakId.equals(payment.getCustomerKeycloakId())) {
    throw new UnauthorizedException(
        "You are not authorized to request a refund for this order"
    );
}
```

---

### C-02: Webhook Amount Not Validated Against Stored Payment (Payment Service)

**Severity:** CRITICAL
**Category:** Payment Integrity
**Impact:** A compromised or tampered PayHere webhook could report a different amount than what was actually charged, causing the system to confirm payment for an incorrect amount.

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/service/PaymentService.java`
**Lines:** 208-330

**Flaw:** The webhook handler verifies the MD5 signature and merchant ID but does not compare the received `payhereAmount` against the stored `payment.getAmount()`.

**Fix:**
```java
// In PaymentService.java, inside handleWebhookNotification(),
// after the MD5 signature verification block and before status processing:

BigDecimal receivedAmount = new BigDecimal(payhereAmount);
if (receivedAmount.compareTo(payment.getAmount()) != 0) {
    log.error("PAYMENT AMOUNT MISMATCH for payment {}: expected={}, received={}",
        payment.getId(), payment.getAmount(), receivedAmount);
    paymentAuditService.record(payment, "AMOUNT_MISMATCH",
        "Expected: " + payment.getAmount() + ", Received: " + receivedAmount);
    return; // Reject the webhook
}
```

---

### C-03: Cross-Vendor Stock Placement via Unvalidated Warehouse ID (Inventory Service)

**Severity:** CRITICAL
**Category:** Tenant Isolation / Authorization Bypass
**Impact:** A vendor can create stock items in another vendor's warehouse by specifying a foreign `warehouseId`.

**File:** `Services/inventory-service/src/main/java/com/rumal/inventory_service/controller/VendorInventoryController.java`
**Lines:** 130-134 (create), 179-184 (bulk import)

**Flaw:** The `warehouseId` from the request body is not validated against the resolved vendor's ownership. The code forces the correct `vendorId` but trusts the `warehouseId` blindly.

**Fix:**
```java
// In VendorInventoryController.java, before creating stock items:

// Add this validation call:
warehouseService.assertWarehouseOwnedByVendor(request.warehouseId(), resolvedVendorId);

// In WarehouseService.java, add:
public void assertWarehouseOwnedByVendor(UUID warehouseId, UUID vendorId) {
    Warehouse warehouse = warehouseRepository.findById(warehouseId)
        .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + warehouseId));
    if (!vendorId.equals(warehouse.getVendorId())) {
        throw new UnauthorizedException(
            "Warehouse " + warehouseId + " does not belong to vendor " + vendorId
        );
    }
}
```

Apply the same fix for the bulk import endpoint at line 179-184.

---

### C-04: Eureka Discovery Server Exposed Without Authentication

**Severity:** CRITICAL
**Category:** Infrastructure Security
**Impact:** Rogue service registration, service deregistration causing outages, and metadata exposure (all service IPs, ports, metadata).

**Files:**
- `Services/discovery-server/src/main/java/com/rumal/discovery_server/DiscoveryServerApplication.java`
- `Services/discovery-server/src/main/resources/application.yaml`
- `docker-compose.yml` (port 8761 exposed)

**Flaw:** Eureka has no authentication. Port 8761 is exposed in docker-compose. No Spring Security dependency exists.

**Fix:**

1. Add to `Services/discovery-server/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

2. Add `Services/discovery-server/src/main/java/com/rumal/discovery_server/SecurityConfig.java`:
```java
package com.rumal.discovery_server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {})
            .build();
    }
}
```

3. Add to `application.yaml`:
```yaml
spring:
  security:
    user:
      name: ${EUREKA_USERNAME:eureka}
      password: ${EUREKA_PASSWORD}
```

4. Update all service `application.yaml` Eureka client URLs:
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_USERNAME:eureka}:${EUREKA_PASSWORD}@discovery-server:8761/eureka/
```

5. Remove port 8761 exposure from `docker-compose.yml`:
```yaml
# REMOVE this line:
#   ports:
#     - "8761:8761"
```

---

### C-05: No Build, Test, or Validation Step in CI/CD Pipeline

**Severity:** CRITICAL
**Category:** Deployment Safety
**Impact:** Untested, unvalidated code deploys directly to production on every push to main.

**File:** `.github/workflows/deploy-main.yml`

**Flaw:** The pipeline contains a single step that runs a deploy script. There is no compilation, no tests, no SAST/DAST scanning, no approval gate, and no rollback mechanism. `cancel-in-progress: true` can leave the system partially deployed.

**Fix:**
```yaml
name: Deploy Backend
on:
  push:
    branches: [main]

jobs:
  build-and-test:
    runs-on: self-hosted
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build and Test All Services
        run: |
          for service_dir in Services/*/; do
            if [ -f "$service_dir/pom.xml" ]; then
              echo "Building $service_dir"
              cd "$service_dir"
              ./mvnw clean verify -B
              cd -
            fi
          done

  deploy:
    needs: build-and-test
    runs-on: self-hosted
    concurrency:
      group: deploy-backend
      cancel-in-progress: false  # Changed: don't cancel in-progress deploys
    environment:
      name: production
      # Add required reviewers in GitHub repo settings
    steps:
      - name: Deploy
        run: /home/ubuntu/deploy_microservice_backend/deploy.sh
```

---

### C-06: Elasticsearch Security Disabled

**Severity:** CRITICAL
**Category:** Infrastructure Security
**Impact:** Open read/write access to all indexed data (product catalog, search terms) from any network-reachable host.

**Files:**
- `docker-compose.yml` (Elasticsearch environment)
- `docker-compose-db.yml` (port 9200 exposed)

**Flaw:** `xpack.security.enabled=false` in Elasticsearch configuration.

**Fix:**
```yaml
# In docker-compose.yml, elasticsearch service:
environment:
  - xpack.security.enabled=true
  - xpack.security.http.ssl.enabled=false  # Enable TLS in production
  - ELASTIC_PASSWORD=${ELASTIC_PASSWORD}

# In search-service application.yaml:
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://elasticsearch:9200}
    username: ${ELASTICSEARCH_USERNAME:elastic}
    password: ${ELASTICSEARCH_PASSWORD}
```

---

### C-07: Client-Submitted Line-Level Discount Amounts Not Validated (Order Service)

**Severity:** CRITICAL
**Category:** Price Manipulation / Financial Integrity
**Impact:** A malicious client can submit inflated line-level discounts (e.g., from "auto-apply" promotions) that reduce the grand total, since only arithmetic consistency is validated, not the discount source.

**File:** `Services/order-service/src/main/java/com/rumal/order_service/service/OrderService.java`
**Lines:** 812-877

**Flaw:** The `validateAndResolvePricingSnapshot` method validates that submitted `subtotal` matches server-computed `itemSubtotal` and `shippingAmount` matches server-computed shipping. However, `lineDiscountTotal`, `cartDiscountTotal`, and `shippingDiscountTotal` are taken from the client request without validation against the promotion engine. Only coupon-based discounts go through the reservation system.

**Fix:**

The checkout flow in the cart-service already computes server-side promotion quotes. The order-service should validate that discount amounts match the promotion quote:

```java
// In OrderService.java, in validateAndResolvePricingSnapshot():
// After validating subtotal and shipping, add:

// Validate discount totals match the promotion quote response
if (promotionQuoteResponse != null) {
    BigDecimal expectedLineDiscount = promotionQuoteResponse.lineDiscountTotal();
    BigDecimal expectedCartDiscount = promotionQuoteResponse.cartDiscountTotal();
    BigDecimal expectedShippingDiscount = promotionQuoteResponse.shippingDiscountTotal();

    if (pricing.lineDiscountTotal().compareTo(expectedLineDiscount) != 0
        || pricing.cartDiscountTotal().compareTo(expectedCartDiscount) != 0
        || pricing.shippingDiscountTotal().compareTo(expectedShippingDiscount) != 0) {
        throw new ValidationException(
            "Discount amounts do not match server-computed promotion quote"
        );
    }
}
```

Alternatively, the order-service should call the promotion-service itself to independently verify discounts rather than trusting any client-submitted values.

---

### C-08: Tenant Isolation Bypass via Optional X-Caller-Vendor-Id (Access Service)

**Severity:** CRITICAL
**Category:** Multi-Tenant Data Leakage
**Impact:** If the API gateway fails to set `X-Caller-Vendor-Id` for a vendor user, that user can access any vendor's staff records.

**File:** `Services/access-service/src/main/java/com/rumal/access_service/controller/AdminVendorStaffController.java`

**Flaw:** The `X-Caller-Vendor-Id` header is declared as `required = false`. The `verifyVendorTenancy` check only applies when this header is non-null. A missing header bypasses the check entirely.

**Fix:**

In all vendor-scoped endpoints in the access-service, the vendor ID should be resolved from the authenticated user's vendor memberships (via the vendor-service) rather than trusting a header:

```java
// Short-term fix: Make the header required for non-platform roles
@GetMapping
public ResponseEntity<Page<VendorStaffAccessResponse>> listVendorStaffAccess(
    @RequestHeader("X-Internal-Auth") String auth,
    @RequestHeader("X-User-Roles") String userRoles,
    @RequestHeader(value = "X-Caller-Vendor-Id", required = false) UUID callerVendorId,
    @RequestParam UUID vendorId, ...) {

    internalRequestVerifier.verify(auth);

    // If user is not a platform admin, require vendor ID
    boolean isPlatformAdmin = userRoles != null &&
        (userRoles.contains("super_admin") || userRoles.contains("platform_staff"));
    if (!isPlatformAdmin && callerVendorId == null) {
        throw new UnauthorizedException("Vendor context required for non-platform users");
    }

    accessService.verifyVendorTenancy(callerVendorId, vendorId);
    // ... rest of method
}
```

---

### C-09: Internal Auth Shared Secret Silently Passes When Empty (Multiple Services)

**Severity:** CRITICAL
**Category:** Authentication Bypass
**Impact:** If `INTERNAL_AUTH_SHARED_SECRET` environment variable is not set, some services allow all internal requests without any authentication.

**Files:** (Pattern found in cart-service, wishlist-service, and potentially others)
- `Services/cart-service/src/main/java/com/rumal/cart_service/security/InternalRequestVerifier.java`
- `Services/wishlist-service/src/main/java/com/rumal/wishlist_service/security/InternalRequestVerifier.java`

**Flaw:** Some `InternalRequestVerifier` implementations have `if (sharedSecret == null || sharedSecret.isBlank()) { return; }` which silently passes all requests when the secret is not configured. Other services correctly throw an exception.

**Fix:**

Ensure ALL services use the fail-closed pattern:

```java
public void verify(String internalAuth) {
    if (sharedSecret == null || sharedSecret.isBlank()) {
        throw new UnauthorizedException("Internal auth secret is not configured");
    }
    if (internalAuth == null || internalAuth.isBlank()) {
        throw new UnauthorizedException("Missing internal authentication");
    }
    if (!MessageDigest.isEqual(
            sharedSecret.getBytes(StandardCharsets.UTF_8),
            internalAuth.getBytes(StandardCharsets.UTF_8))) {
        throw new UnauthorizedException("Invalid internal authentication");
    }
    // ... HMAC verification
}
```

---

## 3. High Severity Findings

### H-01: Rate Limiter Fails Open When Redis is Down (API Gateway)

**Severity:** HIGH
**Category:** Availability / DDoS Protection
**Impact:** If Redis is unavailable, ALL rate limiting is bypassed. The `X-RateLimit-Bypass` response header also leaks this operational state to attackers.

**File:** `Services/api-gateway/src/main/java/com/rumal/api_gateway/config/RateLimitEnforcementFilter.java`
**Lines:** 204-223

**Fix:**
```java
// In RateLimitEnforcementFilter.java, for critical endpoints:
// Instead of always failing open, implement endpoint-specific behavior:

private boolean shouldFailOpen(String path) {
    // Critical financial/auth endpoints should fail CLOSED
    if (path.startsWith("/orders") || path.startsWith("/cart/me/checkout")
        || path.startsWith("/payments") || path.startsWith("/customers/register")
        || path.startsWith("/auth")) {
        return false; // Reject requests when rate limiter is unavailable
    }
    return failOpenOnRateLimiterError; // Configurable for other endpoints
}

// Also remove the X-RateLimit-Bypass header that leaks operational state:
// DELETE this line:
// exchange.getResponse().getHeaders().set("X-RateLimit-Bypass", "RATE_LIMITER_UNAVAILABLE");
```

---

### H-02: No JWT Verification at Service Level (Multiple Services)

**Severity:** HIGH
**Category:** Defense in Depth
**Impact:** If the API gateway is bypassed (misconfigured ingress, direct network access), ALL service endpoints are publicly accessible with no authentication.

**Files:**
- `Services/customer-service/pom.xml` (no spring-security dependency)
- `Services/product-service/pom.xml` (no spring-security dependency)
- `Services/access-service/pom.xml` (no spring-security dependency)
- All services with `SecurityConfig` use `anyRequest().permitAll()`

**Fix:**

Add `spring-boot-starter-oauth2-resource-server` to each service and validate the JWT forwarded by the gateway. Alternatively, implement a shared library:

```xml
<!-- Add to each service pom.xml: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```java
// Shared SecurityConfig for backend services:
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/internal/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

---

### H-03: HMAC Signature Does Not Cover Request Body (All Services)

**Severity:** HIGH
**Category:** Request Tampering
**Impact:** A man-in-the-middle can modify the request body (e.g., change an order status, refund amount, or loyalty points) without invalidating the HMAC signature.

**File:** `Services/order-service/src/main/java/com/rumal/order_service/security/InternalRequestVerifier.java`
**Line:** 58

**Flaw:** `String payload = timestampStr + ":" + request.getMethod() + ":" + request.getRequestURI();` -- body not included.

**Fix:**
```java
// Include a hash of the request body in the HMAC payload:
String bodyHash = ""; // empty for GET/DELETE
if ("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod())
    || "PATCH".equals(request.getMethod())) {
    // Use a cached/wrapped request body
    byte[] body = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
    bodyHash = DigestUtils.sha256Hex(body);
}

String payload = timestampStr + ":" + request.getMethod() + ":"
    + request.getRequestURI() + ":" + bodyHash;
```

Note: This requires wrapping the request in `ContentCachingRequestWrapper` before HMAC verification.

---

### H-04: Payment-to-Order Sync Silently Abandoned After Max Retries (Payment Service)

**Severity:** HIGH
**Category:** Data Consistency / Distributed Systems
**Impact:** After 10 failed retries, the payment is SUCCESS but the order remains PAYMENT_PENDING. No alert, no dead-letter queue, no manual reconciliation path.

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/scheduler/OrderSyncRetryScheduler.java`
**Lines:** 59-62

**Fix:**
```java
if (p.getOrderSyncRetryCount() >= p.getOrderSyncMaxRetries()) {
    p.setOrderSyncPending(false);
    p.setOrderSyncFailed(true); // Add this field to Payment entity
    log.error("CRITICAL: Order sync permanently failed for payment {} " +
        "after {} retries. Manual reconciliation required.",
        p.getId(), p.getOrderSyncRetryCount());

    // Emit a metric for alerting
    meterRegistry.counter("payment.order_sync.permanent_failure").increment();

    // Optionally: create an admin notification/ticket
    paymentAuditService.record(p, "ORDER_SYNC_PERMANENT_FAILURE",
        "Sync abandoned after " + p.getOrderSyncRetryCount() + " retries");
}
```

---

### H-05: Vendor Operational State Fallback Defaults to Visible (Product Service)

**Severity:** HIGH
**Category:** Business Logic / Access Control
**Impact:** During vendor-service outages, suspended/unverified vendors' products appear visible on the storefront.

**File:** `Services/product-service/src/main/java/com/rumal/product_service/client/VendorOperationalStateClient.java`

**Flaw:** Circuit breaker fallback returns `storefrontVisible=true, verified=true`.

**Fix:**
```java
private VendorOperationalState fallbackState(UUID vendorId, Throwable ex) {
    log.warn("Vendor service unavailable, hiding vendor {} products as safety measure", vendorId);
    // Fail CLOSED: hide products when vendor status cannot be determined
    return new VendorOperationalState(vendorId, false, false);
}
```

---

### H-06: Keycloak User Created with emailVerified=true (Customer Service)

**Severity:** HIGH
**Category:** Authentication Bypass
**Impact:** Newly registered users are immediately marked as email-verified without actual email verification. Attackers can register with any email and bypass email verification requirements.

**File:** `Services/customer-service/src/main/java/com/rumal/customer_service/auth/KeycloakManagementService.java`

**Fix:**
```java
// When creating Keycloak user, set emailVerified to false:
userRep.setEmailVerified(false); // Changed from true

// Then trigger the verification email:
// The API gateway's AuthController already has /auth/resend-verification
// Trigger it automatically after registration
```

---

### H-07: Payout Creation Trusts Admin-Submitted Amounts (Payment Service)

**Severity:** HIGH
**Category:** Financial Integrity
**Impact:** An admin can submit incorrect payout amounts with no validation against actual vendor order totals.

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/service/PayoutService.java`
**Lines:** 39-83

**Fix:**
```java
// In PayoutService.createPayout(), add validation:

// 1. Validate vendor order IDs exist and are completed
List<UUID> vendorOrderIds = parseVendorOrderIds(request.vendorOrderIds());
BigDecimal expectedPayout = BigDecimal.ZERO;
for (UUID voId : vendorOrderIds) {
    VendorOrderSummary vo = orderClient.getVendorOrderSummary(voId);
    if (vo == null) {
        throw new ValidationException("Vendor order not found: " + voId);
    }
    expectedPayout = expectedPayout.add(vo.payoutAmount());
}

// 2. Validate amount matches
if (request.payoutAmount().compareTo(expectedPayout) != 0) {
    throw new ValidationException(
        "Payout amount " + request.payoutAmount() +
        " does not match computed total " + expectedPayout
    );
}

// 3. Check for duplicate payouts
for (UUID voId : vendorOrderIds) {
    if (vendorPayoutRepository.existsByVendorOrderIdsContaining(voId.toString())) {
        throw new ValidationException("Vendor order " + voId + " already has a payout");
    }
}
```

---

### H-08: Vendor UUID Lookup Bypasses Storefront Visibility (Vendor Service)

**Severity:** HIGH
**Category:** Information Disclosure
**Impact:** `GET /vendors/{uuid}` returns vendor data for inactive, unverified, or suspended vendors. Only slug-based lookups enforce visibility.

**File:** `Services/vendor-service/src/main/java/com/rumal/vendor_service/controller/VendorController.java`

**Fix:**
```java
@GetMapping("/{idOrSlug}")
public ResponseEntity<VendorResponse> getVendor(@PathVariable String idOrSlug) {
    VendorResponse vendor;
    try {
        UUID id = UUID.fromString(idOrSlug);
        vendor = vendorService.getByIdPublic(id);
    } catch (IllegalArgumentException e) {
        vendor = vendorService.getBySlugPublic(idOrSlug);
    }
    return ResponseEntity.ok(vendor);
}

// In VendorServiceImpl, add:
public VendorResponse getByIdPublic(UUID id) {
    Vendor vendor = vendorRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
    if (vendor.isDeleted() || !vendor.isStorefrontVisible()) {
        throw new ResourceNotFoundException("Vendor not found");
    }
    return vendor.toPublicResponse();
}
```

---

### H-09: No Docker Resource Limits on Any Container

**Severity:** HIGH
**Category:** Infrastructure Resilience
**Impact:** A memory leak or resource exhaustion in one service takes down the entire host.

**File:** `docker-compose.yml`

**Fix:**
```yaml
# Add to each service in docker-compose.yml:
services:
  api-gateway:
    # ... existing config
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M

  order-service:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 768M
        reservations:
          cpus: '0.5'
          memory: 384M

  redis:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M

  elasticsearch:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
```

---

### H-10: No Docker Network Segmentation

**Severity:** HIGH
**Category:** Infrastructure Security
**Impact:** All services share one network. A compromised service can directly access all databases, Redis, and Elasticsearch.

**File:** `docker-compose.yml`

**Fix:**
```yaml
networks:
  public:
    driver: bridge
  internal:
    driver: bridge
    internal: true  # No external access
  database:
    driver: bridge
    internal: true

services:
  api-gateway:
    networks: [public, internal]
  frontend:
    networks: [public]

  # All microservices:
  order-service:
    networks: [internal, database]
  payment-service:
    networks: [internal, database]
  # ... etc

  # Databases only on database network:
  postgres-order:
    networks: [database]
  redis:
    networks: [internal]
  elasticsearch:
    networks: [internal]
```

---

### H-11: Missing Role Checks on Admin Vendor Read Endpoints (Admin Service)

**Severity:** HIGH
**Category:** Authorization Bypass
**Impact:** Any authenticated internal request can list all vendors regardless of caller's role. Vendor staff could enumerate all platform vendors.

**File:** `Services/admin-service/src/main/java/com/rumal/admin_service/controller/AdminVendorController.java`

**Flaw:** GET endpoints only check `internalRequestVerifier.verify(request)` without role verification, unlike mutation endpoints which properly call `actorScopeService.requirePlatformRole(...)`.

**Fix:**
```java
@GetMapping
public ResponseEntity<Page<...>> listAll(
    HttpServletRequest request,
    @RequestHeader("X-User-Sub") String userSub,
    @RequestHeader("X-User-Roles") String userRoles,
    ...) {
    internalRequestVerifier.verify(request);
    actorScopeService.requirePlatformRole(userSub, userRoles, request); // ADD THIS
    // ... rest of method
}
```

Apply to all GET endpoints: `listAll`, `listDeleted`, `getById`, `search`.

---

### H-12: PayHere Refund Response Fully Logged (Payment Service)

**Severity:** HIGH
**Category:** PCI Data Exposure
**Impact:** Sensitive transaction details from PayHere refund responses are written to application logs.

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/service/PayHereClient.java`
**Line:** 128

**Fix:**
```java
// Replace:
log.info("PayHere refund response for payment {}: {}", payherePaymentId, response);

// With:
log.info("PayHere refund response for payment {}: status={}",
    payherePaymentId, response.getOrDefault("status", "unknown"));
```

---

### H-13: Webhook Endpoint Has No IP Allowlisting (Payment Service)

**Severity:** HIGH
**Category:** Payment Security
**Impact:** Anyone who discovers the merchant secret can forge valid webhook calls. MD5 signature is the only protection.

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/controller/PayHereWebhookController.java`

**Fix:**

Add IP validation for PayHere's known IP ranges:

```java
@PostMapping("/webhooks/payhere/notify")
public ResponseEntity<String> handleNotification(HttpServletRequest request, ...) {
    // Validate source IP against PayHere's known ranges
    String clientIp = request.getHeader("X-Forwarded-For");
    if (clientIp == null) clientIp = request.getRemoteAddr();
    if (!payHereIpAllowList.contains(clientIp)) {
        log.warn("Webhook from unauthorized IP: {}", clientIp);
        return ResponseEntity.status(403).body("Forbidden");
    }
    // ... existing handler
}
```

---

### H-14: Cumulative Refund Amount Never Validated Against Payment (Payment Service)

**Severity:** HIGH
**Category:** Financial Integrity
**Impact:** In a multi-vendor order, individual vendor-order refunds could exceed the total payment amount.

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/service/RefundService.java`
**Lines:** 79-87

**Fix:**
```java
// After validating refund amount against vendor order total, add:

BigDecimal totalRefunded = refundRequestRepository
    .sumApprovedRefundsByPaymentId(payment.getId());
BigDecimal totalWithThis = totalRefunded.add(request.refundAmount());

if (totalWithThis.compareTo(payment.getAmount()) > 0) {
    throw new ValidationException(
        "Cumulative refunds (" + totalWithThis +
        ") would exceed payment amount (" + payment.getAmount() + ")"
    );
}
```

---

### H-15: confirmReservation Can Produce Negative On-Hand (Inventory Service)

**Severity:** HIGH
**Category:** Data Integrity
**Impact:** If admin adjusts stock downward between reservation and confirmation, `quantityOnHand` goes negative.

**File:** `Services/inventory-service/src/main/java/com/rumal/inventory_service/service/StockService.java`

**Fix:**
```java
// In confirmReservation, before decrementing:
if (item.getQuantityOnHand() < res.getQuantityReserved()) {
    log.error("Insufficient on-hand ({}) for reservation confirmation ({}). " +
        "Stock item: {}, Reservation: {}",
        item.getQuantityOnHand(), res.getQuantityReserved(),
        item.getId(), res.getId());
    // Option A: Reject confirmation
    throw new InsufficientStockException("On-hand quantity insufficient for confirmation");
    // Option B: Allow but flag for investigation
    // item.setQuantityOnHand(0);
    // item.setFlaggedForReconciliation(true);
}

item.setQuantityOnHand(item.getQuantityOnHand() - res.getQuantityReserved());
item.setQuantityReserved(item.getQuantityReserved() - res.getQuantityReserved());
```

---

### H-16: Session Revocation Silently Fails (API Gateway)

**Severity:** HIGH
**Category:** Authentication Lifecycle
**Impact:** User believes logout succeeded but their Keycloak session remains active.

**File:** `Services/api-gateway/src/main/java/com/rumal/api_gateway/controller/AuthController.java`
**Lines:** 21-29

**Fix:**
```java
@PostMapping("/auth/logout")
public Mono<ResponseEntity<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
    String sessionId = jwt.getClaimAsString("sid");
    if (sessionId == null) {
        return Mono.just(ResponseEntity.noContent().build());
    }
    return keycloakService.revokeSession(sessionId)
        .then(Mono.just(ResponseEntity.noContent().<Void>build()))
        .onErrorResume(ex -> {
            log.error("Failed to revoke Keycloak session {}: {}", sessionId, ex.getMessage());
            // Return 503 instead of 204 to signal the client that logout may not have succeeded
            return Mono.just(ResponseEntity.status(503).<Void>build());
        });
}
```

---

## 4. Medium Severity Findings

### M-01: N+1 Queries on Order List Endpoints (Order Service)

**File:** `Services/order-service/src/main/java/com/rumal/order_service/entity/Order.java`
**Lines:** 148-154

`OrderItem` and `VendorOrder` collections are LAZY. Listing 20 orders triggers up to 40+ additional SELECT queries. Use `@EntityGraph` or `JOIN FETCH`:

```java
// In OrderRepository.java:
@EntityGraph(attributePaths = {"orderItems", "vendorOrders"})
Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
```

---

### M-02: Analytics Service Executes 14+ Individual COUNT Queries (Order Service)

**File:** `Services/order-service/src/main/java/com/rumal/order_service/service/OrderAnalyticsService.java`
**Lines:** 41-65, 88

**Fix:**
```java
// Replace individual countByStatus calls with a single GROUP BY:
// In OrderRepository.java:
@Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
List<Object[]> countByStatusGrouped();
```

---

### M-03: Sequential Product HTTP Calls During Order Creation (Order Service)

**File:** `Services/order-service/src/main/java/com/rumal/order_service/service/OrderService.java`
**Lines:** 756-763

Each order item triggers a separate HTTP call. For 20 items = 20 sequential round-trips.

**Fix:** Add a batch product endpoint:
```java
// In ProductClient:
public List<ProductSummary> getBatch(List<UUID> productIds) {
    return restClient.post()
        .uri("/internal/products/batch")
        .body(new BatchProductRequest(productIds))
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
}
```

---

### M-04: AdminFinalize Re-queries Refund 3 Times Outside Transaction (Payment Service)

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/service/RefundService.java`
**Lines:** 210-218

**Fix:** Capture values in Phase 1:
```java
// Phase 1: capture all needed values
BigDecimal refundAmount = refundRequest.getRefundAmount();
UUID orderId = refundRequest.getOrderId();
String paymentIdStr = payment.getPayherePaymentId();

// Phase 2: use captured values instead of re-querying
payhereClient.refund(paymentIdStr, refundAmount, orderId);
```

---

### M-05: Promotion Quote Engine Loads ALL Active Promotions Per Request

**File:** `Services/promotion-service/src/main/java/com/rumal/promotion_service/service/PromotionQuoteService.java`

**Fix:** Add scope-based pre-filtering:
```java
// Instead of loading all active promotions, filter by cart context:
Set<UUID> vendorIds = lines.stream()
    .map(PromotionQuoteLineRequest::vendorId).collect(Collectors.toSet());
Set<UUID> categoryIds = lines.stream()
    .flatMap(l -> l.categoryIds().stream()).collect(Collectors.toSet());

Page<PromotionCampaign> candidates = promotionRepository
    .findActiveByScopeMatch(vendorIds, categoryIds, pageable);
```

---

### M-06: PaymentExpiryScheduler Runs in Single Transaction

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/scheduler/PaymentExpiryScheduler.java`
**Line:** 32

**Fix:** Remove `@Transactional` from the method and process each payment in its own transaction:
```java
@Scheduled(...)
public void expireStalePayments() {
    // Remove @Transactional annotation
    Page<Payment> page;
    do {
        page = paymentRepository.findExpired(cutoff, PageRequest.of(0, 100));
        for (Payment p : page) {
            transactionTemplate.executeWithoutResult(status -> {
                expireSinglePayment(p.getId());
            });
        }
    } while (page.hasNext());
}
```

---

### M-07: No Rate Limiting on POST /products/{id}/view

**File:** `Services/product-service/src/main/java/com/rumal/product_service/controller/ProductController.java`

**Impact:** View counts can be artificially inflated. Add rate limiting at the gateway:

```yaml
# In API Gateway application.yaml, add a rate limit policy for view tracking:
rate-limit:
  product-view:
    replenish-rate: ${RATE_LIMIT_PRODUCT_VIEW_REPLENISH:2}
    burst-capacity: ${RATE_LIMIT_PRODUCT_VIEW_BURST:5}
    key-resolver: ipKeyResolver
```

---

### M-08: Error Messages Leak Internal Details (Product + Inventory Services)

**File:** `Services/product-service/src/main/java/com/rumal/product_service/exception/GlobalExceptionHandler.java`

**Fix:**
```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public Map<String, String> handleGeneric(Exception ex) {
    log.error("Unexpected error", ex); // Log full detail server-side
    return Map.of("error", "An unexpected internal error occurred");
    // Removed: ex.getMessage() which can expose internals
}
```

---

### M-09: Inventory Service Has No Max Page Size Limit

**File:** `Services/inventory-service/` (missing `PaginationConfig.java`)

**Fix:** Add `PaginationConfig.java`:
```java
package com.rumal.inventory_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PaginationConfig {
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer paginationCustomizer() {
        return resolver -> {
            resolver.setMaxPageSize(100);
            resolver.setFallbackPageable(org.springframework.data.domain.PageRequest.of(0, 20));
        };
    }
}
```

---

### M-10: Audit Log IP Address Never Populated (Admin Service)

**File:** `Services/admin-service/src/main/java/com/rumal/admin_service/controller/AdminOrderController.java` (and all admin controllers)

All calls to `auditService.log(...)` pass `null` for `ipAddress`.

**Fix:**
```java
// Extract IP from request header (set by gateway):
String clientIp = request.getHeader("X-Forwarded-For");
if (clientIp != null && clientIp.contains(",")) {
    clientIp = clientIp.split(",")[0].trim();
}
auditService.log(..., clientIp, ...);
```

---

### M-11: Bulk Operations Not Atomic (Admin Service + Product Service)

**Files:**
- `Services/admin-service/src/main/java/com/rumal/admin_service/controller/AdminOrderController.java`
- `Services/product-service/src/main/java/com/rumal/product_service/service/ProductServiceImpl.java`

Bulk operations process items one-by-one. Partial failures leave the system in an inconsistent state with some items updated and others not.

**Fix:** Return detailed `BulkOperationResult` (already exists) and consider adding a `dryRun` mode for preview.

---

### M-12: Aggregate Order Status Derivation is Misleading (Order Service)

**File:** `Services/order-service/src/main/java/com/rumal/order_service/service/OrderService.java`
**Lines:** 1261-1299

If vendor order 1 is `SHIPPED` and vendor order 2 is `PROCESSING`, the parent shows `SHIPPED` (misleading since not everything shipped).

**Fix:** Use a "lowest common denominator" approach:
```java
// Instead of taking the highest priority status,
// if any vendor order is less advanced, use PARTIALLY_SHIPPED or PROCESSING
if (vendorStatuses.contains(OrderStatus.SHIPPED)
    && vendorStatuses.stream().anyMatch(s -> s.ordinal() < OrderStatus.SHIPPED.ordinal())) {
    return OrderStatus.PARTIALLY_SHIPPED; // Add this status to the enum
}
```

---

### M-13: No GDPR Opt-Out for Personalization Tracking

**File:** `Services/personalization-service/src/main/java/com/rumal/personalization_service/service/EventService.java`

**Fix:** Add an opt-out check:
```java
public void recordEvent(RecordEventRequest request, String userId, String sessionId) {
    // Check for opt-out before recording
    if (userId != null && userPreferencesClient.hasOptedOutOfTracking(userId)) {
        return; // Respect user's privacy preference
    }
    // ... existing event recording
}
```

---

### M-14: Redis Communication Unencrypted

**Files:** All `application.yaml` files with Redis configuration

No TLS configured for Redis connections. Rate limit tokens, cached responses, and idempotency state transmitted in plaintext.

**Fix:**
```yaml
spring:
  data:
    redis:
      ssl:
        enabled: ${REDIS_SSL_ENABLED:true}
```

---

### M-15: Orphaned Keycloak Users on DB Failure (Customer Service)

**File:** `Services/customer-service/src/main/java/com/rumal/customer_service/service/CustomerServiceImpl.java`

Keycloak user creation happens outside the transaction. If DB save fails, an orphaned Keycloak user exists.

**Fix:**
```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public CustomerResponse register(RegisterCustomerRequest request) {
    // ... validation
    String keycloakId = keycloakService.createUser(email, name, password);
    try {
        return transactionTemplate.execute(status -> {
            Customer customer = buildCustomer(keycloakId, request);
            return customerRepository.save(customer).toResponse();
        });
    } catch (Exception e) {
        // Compensating action: clean up the orphaned Keycloak user
        try {
            keycloakService.deleteUser(keycloakId);
        } catch (Exception cleanupEx) {
            log.error("Failed to cleanup orphaned Keycloak user {}: {}",
                keycloakId, cleanupEx.getMessage());
        }
        throw e;
    }
}
```

---

### M-16: Slug Generation Executes Up to 100K Sequential Queries (Product Service)

**File:** `Services/product-service/src/main/java/com/rumal/product_service/service/ProductServiceImpl.java`

**Fix:**
```java
private String resolveUniqueSlug(String base) {
    if (!productRepository.existsBySlug(base)) return base;

    // Single query to find max existing suffix
    Integer maxSuffix = productRepository.findMaxSlugSuffix(base);
    return base + "-" + ((maxSuffix == null ? 0 : maxSuffix) + 1);
}

// In ProductRepository:
@Query("SELECT MAX(CAST(SUBSTRING(p.slug, LENGTH(:base) + 2) AS integer)) " +
       "FROM Product p WHERE p.slug LIKE CONCAT(:base, '-%') " +
       "AND p.slug REGEXP CONCAT(:base, '-[0-9]+')")
Integer findMaxSlugSuffix(@Param("base") String base);
```

---

### M-17: Order Status PENDING -> CONFIRMED Bypasses Payment (Order Service)

**File:** `Services/order-service/src/main/java/com/rumal/order_service/service/OrderService.java`
**Lines:** 1094-1117

Admin can confirm an order that was never paid. If intentional for manual/offline payments, add explicit documentation and audit logging.

---

### M-18: Customer Cancellation of CONFIRMED Orders May Fail to Release Committed Inventory

**File:** `Services/order-service/src/main/java/com/rumal/order_service/service/OrderService.java`
**Line:** 83-88, 977-994

After confirmation, inventory is committed (not just reserved). The `RELEASE_INVENTORY_RESERVATION` event may fail because the reservation no longer exists in RESERVED state.

**Fix:** The inventory service should handle both reservation release and committed stock reversal:
```java
// In StockService, add a method that handles both cases:
public void reverseStockCommitment(UUID orderId) {
    List<StockReservation> reservations = stockReservationRepository
        .findByOrderId(orderId);
    for (StockReservation res : reservations) {
        if (res.getStatus() == ReservationStatus.CONFIRMED) {
            // Reverse the committed deduction
            StockItem item = stockItemRepository.findByIdForUpdate(res.getStockItem().getId())
                .orElseThrow();
            item.setQuantityOnHand(item.getQuantityOnHand() + res.getQuantityReserved());
            res.setStatus(ReservationStatus.CANCELLED);
        } else if (res.getStatus() == ReservationStatus.RESERVED) {
            // Release the reservation
            releaseReservation(res);
        }
    }
}
```

---

### M-19: Popular Search Terms Can Be Gamed (Search Service)

**File:** `Services/search-service/src/main/java/com/rumal/search_service/service/PopularSearchService.java`

No per-user deduplication. Automated requests can inflate search term popularity.

**Fix:** Deduplicate by user/session:
```java
public void trackSearch(String query, String userId, String sessionId) {
    String dedupeKey = "search:dedup:" + (userId != null ? userId : sessionId) + ":" + query;
    Boolean isNew = redisTemplate.opsForValue()
        .setIfAbsent(dedupeKey, "1", Duration.ofMinutes(30));
    if (Boolean.TRUE.equals(isNew)) {
        // Only count if this user hasn't searched this term recently
        redisTemplate.opsForZSet().add(popularSearchKey, query, System.currentTimeMillis());
    }
}
```

---

### M-20: No Maximum Length Validation on Search Query (Search Service)

**File:** `Services/search-service/src/main/java/com/rumal/search_service/controller/SearchController.java`

**Fix:**
```java
@GetMapping
public ResponseEntity<SearchResponse> search(@RequestParam String q, ...) {
    if (q.length() > 256) {
        throw new ValidationException("Search query too long (max 256 characters)");
    }
    // ... existing logic
}
```

---

### M-21: Review Image Uploaded to S3 Before Content Validation

**File:** `Services/review-service/src/main/java/com/rumal/review_service/service/ReviewImageStorageServiceImpl.java`

**Fix:** Validate image content BEFORE uploading:
```java
public StoredImage uploadImage(MultipartFile file) {
    // Validate extension first
    validateExtension(file.getOriginalFilename());

    // Validate actual image content BEFORE upload
    try (InputStream is = file.getInputStream()) {
        BufferedImage img = ImageIO.read(is);
        if (img == null) {
            throw new ValidationException("File is not a valid image");
        }
    }

    // Now safe to upload
    String key = generateKey(file);
    objectStorage.putObject(key, file.getBytes(), contentType);
    return new StoredImage(key, contentType);
}
```

---

## 5. Low Severity Findings

### L-01: No Audit Trail for Loyalty Point Changes (Customer Service)

**File:** `Services/customer-service/src/main/java/com/rumal/customer_service/service/CustomerServiceImpl.java`

Points are added atomically but no `CustomerActivityLog` entry is recorded. Add:
```java
customerActivityLogRepository.save(CustomerActivityLog.builder()
    .customerId(customerId)
    .action("LOYALTY_POINTS_ADDED")
    .details("Added " + points + " points")
    .build());
```

---

### L-02: No Upper Bound on Loyalty Points Per Transaction

**File:** `Services/customer-service/src/main/java/com/rumal/customer_service/service/CustomerServiceImpl.java`

**Fix:**
```java
public void addLoyaltyPoints(UUID customerId, int points) {
    if (points <= 0) throw new ValidationException("Points must be positive");
    if (points > 10000) throw new ValidationException("Points per transaction cannot exceed 10,000");
    // ...
}
```

---

### L-03: PayHere REST Calls Create New RestClient Per Invocation (Payment Service)

**File:** `Services/payment-service/src/main/java/com/rumal/payment_service/service/PayHereClient.java`
**Lines:** 119, 150

**Fix:** Cache the built client:
```java
private final RestClient payhereRestClient;

@PostConstruct
void init() {
    this.payhereRestClient = restClientBuilder
        .baseUrl(properties.getBaseUrl())
        .build();
}
```

---

### L-04: Vendor Metrics Can Be Set to Invalid Values (Vendor Service)

**File:** `Services/vendor-service/src/main/java/com/rumal/vendor_service/service/VendorServiceImpl.java`

**Fix:** Add validation:
```java
public VendorResponse updateMetrics(UUID vendorId, UpdateVendorMetricsRequest req) {
    if (req.averageRating() != null && (req.averageRating() < 0 || req.averageRating() > 5)) {
        throw new ValidationException("Average rating must be between 0 and 5");
    }
    if (req.fulfillmentRate() != null && (req.fulfillmentRate() < 0 || req.fulfillmentRate() > 100)) {
        throw new ValidationException("Fulfillment rate must be between 0 and 100");
    }
    // ...
}
```

---

### L-05: keycloakId Exposed in CustomerResponse DTO

**File:** `Services/customer-service/src/main/java/com/rumal/customer_service/dto/CustomerResponse.java`

Internal Keycloak ID should not be in public API responses. Remove or replace with a separate public-facing customer ID.

---

### L-06: No Rate Limiting on Vote Toggle Operations (Review Service)

Users can rapidly toggle votes creating unnecessary DB load. Add to gateway rate limit config:
```yaml
rate-limit:
  review-vote:
    replenish-rate: 3
    burst-capacity: 6
    key-resolver: userOrIpKeyResolver
```

---

### L-07: Analytics Dashboard Blocks on Slowest Downstream Service

**File:** `Services/analytics-service/src/main/java/com/rumal/analytics_service/service/AdminAnalyticsService.java`

`CompletableFuture.allOf().join()` waits for ALL futures. Return partial results instead:
```java
// Use orTimeout + handle on each future individually
// and collect whatever completed within the deadline
```

---

### L-08: AZP Accepted as Audience by Default (API Gateway)

**File:** `Services/api-gateway/src/main/java/com/rumal/api_gateway/config/AudienceValidator.java`

`acceptAzpAsAudience` defaults to `true`. Consider setting to `false` for strict audience enforcement:
```yaml
keycloak:
  accept-azp-as-audience: false
```

---

### L-09: Computation Jobs Run in Single Long Transaction (Personalization Service)

**File:** `Services/personalization-service/src/main/java/com/rumal/personalization_service/service/ComputationJobService.java`

300-second transaction timeout for jobs processing up to 500K events. Consider chunked commits.

---

### L-10: Cache clear-on-startup=true in All Services

All services clear Redis cache on startup. In production with rolling deployments, this causes cold-start cache storms. Set to `false` for production:
```yaml
cache:
  clear-on-startup: ${CACHE_CLEAR_ON_STARTUP:false}
```

---

## 6. Architectural Tech Debt

### AT-01: No Database Migration Tool

All services use Hibernate DDL auto-generation. Production should use Flyway or Liquibase:

```xml
<!-- Add to each pom.xml: -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

```yaml
# application.yaml:
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

### AT-02: No Distributed Tracing Infrastructure

While trace headers are configured, there is no Zipkin/Jaeger/OTEL collector. Add:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

---

### AT-03: No Health Checks on Docker Services

Only Elasticsearch has a healthcheck. All other containers lack health probes:

```yaml
# Add to each service in docker-compose.yml:
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

---

### AT-04: Idempotency Filter Code Duplicated Across Services

`AbstractRedisServletIdempotencyFilter` is copy-pasted into 8+ services. Extract to a shared library:

```
shared-libs/
  idempotency-filter/
    src/main/java/com/rumal/shared/idempotency/
      AbstractRedisServletIdempotencyFilter.java
    pom.xml
```

---

### AT-05: InternalRequestVerifier Code Duplicated Across All Services

Same copy-paste issue. Extract to shared library alongside the idempotency filter.

---

### AT-06: No Shared API Error Response Contract

Each service defines its own error response format (some use `Map<String, String>`, some use `Map<String, Object>`). Standardize:

```java
public record ApiError(
    String error,
    String message,
    String requestId,
    Instant timestamp
) {}
```

---

## 7. What Is Done Well

The audit identified many positive security patterns that demonstrate mature engineering:

1. **Header Sanitization (API Gateway):** Internal headers are stripped from all incoming requests before JWT extraction, preventing identity spoofing.

2. **Comprehensive Rate Limiting:** 30+ distinct rate limit policies with separate read/write limits per endpoint family.

3. **Idempotency Protection:** Critical mutations require idempotency keys with SHA-256 request hashing to prevent replay with different payloads. Scoped per-user.

4. **Price Integrity at Checkout (Cart Service):** All product prices are re-fetched server-side at checkout time. Client-submitted prices are completely ignored. Compare-and-swap snapshot verification prevents TOCTOU attacks.

5. **Coupon Double-Spend Prevention (Promotion Service):** SERIALIZABLE isolation + pessimistic locks + request key idempotency + budget accounting including active reservations.

6. **IDOR Prevention:** Consistent `keycloakId` scoping across all user-facing endpoints. No cart/wishlist/order ID in URLs for user operations.

7. **Trusted Proxy Resolution (API Gateway):** Only reads `X-Forwarded-For` from configured proxy IPs, preventing IP spoofing.

8. **Email Verification Enforcement (API Gateway):** All authenticated actions require verified email.

9. **Outbox Pattern (Order Service):** `FOR UPDATE SKIP LOCKED` enables safe concurrent processing across multiple instances.

10. **Timing-Safe Secret Comparison:** All `InternalRequestVerifier` implementations use `MessageDigest.isEqual()`.

11. **Cache Deserialization Protection:** Polymorphic type validation restricts deserialization to whitelisted packages.

12. **Purchase Verification for Reviews:** Pessimistic lock prevents TOCTOU race on duplicate review check.

13. **Elasticsearch Query Safety:** Typed Java API prevents injection.

14. **Graceful Degradation in Analytics:** `safeCall` pattern returns partial results when downstream services fail.

15. **Vendor Lifecycle Audit Trail:** Comprehensive lifecycle auditing for all vendor state transitions.

---

*End of Global Audit Report*
