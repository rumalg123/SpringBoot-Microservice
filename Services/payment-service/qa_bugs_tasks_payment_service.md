# QA Bugs & Tasks — payment-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `payment-service` (port 8092)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 1 High, 2 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `PaymentController` (`/payments/me`) | Customer-facing: initiate payment, get by id/order |
| Controller | `PayHereWebhookController` (`/webhooks/payhere`) | PayHere callback — webhook signature verification, status mapping |
| Controller | `RefundController` (`/payments/me/refunds`) | Customer-facing: create refund request, list, get |
| Controller | `VendorRefundController` (`/payments/vendor/me/refunds`) | Vendor self-service: list/get refunds, respond (approve/reject) |
| Controller | `AdminRefundController` (`/admin/payments/refunds`) | Admin: list/get refunds, finalize (approve/reject with PayHere refund) |
| Controller | `AdminPayoutController` (`/admin/payments/payouts`) | Admin: create/approve/complete/cancel vendor payouts |
| Controller | `AdminPaymentController` (`/admin/payments`) | Admin: list/get/detail payments, audit trail, verify with PayHere |
| Controller | `AdminBankAccountController` (`/admin/payments/bank-accounts`) | Admin: CRUD bank accounts, set primary |
| Controller | `InternalPaymentController` (`/internal/payments`) | Internal: get payment by order |
| Controller | `InternalPaymentAnalyticsController` (`/internal/payments/analytics`) | Internal: platform summary, method breakdown |
| Service | `PaymentService` | Payment initiation, webhook processing, query/admin methods |
| Service | `RefundService` | Refund lifecycle: create, vendor respond, admin finalize (3-phase), escalation |
| Service | `PayoutService` | Vendor payout lifecycle: create, approve, complete, cancel |
| Service | `PaymentAuditService` | Write audit entries for all payment/refund/payout events |
| Service | `PaymentAnalyticsService` | Platform summary, payment method breakdown |
| Service | `PayHereClient` | OAuth2 token management, refund API, payment retrieval API |
| Service | `PayHereHashUtil` | MD5 hash generation/verification for PayHere checkout and webhook |
| Repository | `PaymentRepository` | Payment CRUD, pessimistic locking, analytics aggregations |
| Repository | `RefundRequestRepository` | Refund CRUD, pessimistic locking, cumulative amount queries |
| Repository | `VendorPayoutRepository` | Payout CRUD, pessimistic locking, filtered listing |
| Repository | `VendorBankAccountRepository` | Bank account CRUD, primary flag management |
| Repository | `PaymentAuditRepository` | Audit trail queries |
| Entity | `Payment` | `@Version`, orderId, customerId, amount, status, payherePaymentId, webhookIdempotencyHash, orderSyncPending |
| Entity | `RefundRequest` | `@Version`, `@ManyToOne` Payment, vendorOrderId, refundAmount, vendorResponseDeadline |
| Entity | `VendorPayout` | `@Version`, `@ManyToOne` VendorBankAccount, vendorOrderIds (CSV), bank snapshots |
| Entity | `VendorBankAccount` | `@Version`, vendorId, primary flag, active flag |
| Entity | `PaymentAudit` | Audit trail: paymentId, refundRequestId, payoutId, eventType, from/to status, actor |
| Client | `CustomerClient` | Get customer/addresses by keycloakId (CB: customerService) |
| Client | `OrderClient` | Get order, set payment info, update order/vendor-order status (CB: orderService) |
| Scheduler | `PaymentExpiryScheduler` | Expire INITIATED payments past expiresAt |
| Scheduler | `RefundEscalationScheduler` | Escalate refunds past vendor response deadline |
| Scheduler | `OrderSyncRetryScheduler` | Retry failed order status syncs after webhook processing |
| Config | `SecurityConfig` | `permitAll()` — relies on API Gateway |
| Security | `InternalRequestVerifier` | HMAC-based internal request verification |

---

## BUG-PAY-001 — Resilience4j Retries Broken for All 3 Client Groups

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/OrderClient.java`, `client/CustomerClient.java`, `service/PayHereClient.java`, `application.yaml` |
| **Lines** | `OrderClient.java:51–53,80–82,108–110,128–130,156–158`, `CustomerClient.java:54–56,76–78`, `PayHereClient.java:130–135,156–161` |

### Description

The Resilience4j retry configuration for all three instances specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, **all** HTTP clients catch `RestClientException` (the parent of both `ResourceAccessException` and `HttpServerErrorException`) and re-wrap it **before** the exception reaches the Retry AOP aspect:

**OrderClient & CustomerClient** — wrap as `ServiceUnavailableException`:
```java
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
}
```

**PayHereClient** — wrap as `PayHereApiException`:
```java
} catch (RestClientException ex) {
    throw new PayHereApiException("PayHere refund request failed: " + ex.getMessage(), ex);
}
```

Since neither `ServiceUnavailableException` nor `PayHereApiException` is in any `retryExceptions` list, the Retry aspect sees these wrapped exceptions and does **not** retry. All 7 retry-annotated methods across all 3 clients have **zero effective retries**.

**Affected methods**:
- `OrderClient`: `getOrder`, `setPaymentInfo`, `updateOrderStatus`, `getVendorOrder`, `updateVendorOrderStatus` (5 methods)
- `CustomerClient`: `getCustomerByKeycloakId`, `getCustomerAddresses` (2 methods)
- `PayHereClient`: `refund`, `retrievePayment` (2 methods)

### Fix

Add the wrapped exception types to the retry configuration for each affected instance.

**`application.yaml`** — Replace the retry section (lines 79–107):

```yaml
  retry:
    instances:
      orderService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.payment_service.exception.ServiceUnavailableException
      customerService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.payment_service.exception.ServiceUnavailableException
      payHereService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.payment_service.exception.PayHereApiException
```

**Important**: Retries on `OrderClient.setPaymentInfo()` and `OrderClient.updateOrderStatus()` are safe because the order-service uses pessimistic locking and idempotent status transitions. Retries on `PayHereClient.refund()` require PayHere's refund endpoint to be idempotent (standard for payment APIs). If there is concern about non-idempotent PayHere refund calls, the `payHereService` retry can be left unchanged and only `orderService` and `customerService` updated.

---

## BUG-PAY-002 — OrderSyncRetryScheduler Holds DB Transaction During HTTP Calls and Has No Retry Limit

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Data Integrity |
| **Affected Files** | `scheduler/OrderSyncRetryScheduler.java`, `entity/Payment.java` |
| **Lines** | `OrderSyncRetryScheduler.java:29–60`, `Payment.java:106–107` |

### Description

The `OrderSyncRetryScheduler.retryPendingOrderSyncs()` has two compounding issues:

**Issue 1 — DB transaction held during HTTP calls**: The entire `do-while` loop runs within a single `@Transactional`. For each payment, the scheduler makes 1–2 HTTP calls to the order-service (`setPaymentInfo` + `updateOrderStatus`). Each HTTP call can take up to 5 seconds (configured response timeout). With 100 payments per page and 2 calls each, a single page could hold the DB connection for **up to 1000 seconds**. Multiple pages compound the problem. This can exhaust the connection pool under load.

**Issue 2 — No retry limit**: The `orderSyncPending` flag is a simple boolean with no retry counter. If the order-service permanently rejects a sync (e.g., the order was deleted, or the status transition is invalid), the scheduler retries that payment **every 2 minutes, forever**. This creates:
- Perpetual unnecessary HTTP calls to the order-service
- Log flooding from repeated warnings
- No mechanism to alert operators about permanently stuck syncs

### Current Code

**`OrderSyncRetryScheduler.java:29–60`**:
```java
@Scheduled(fixedDelayString = "${payment.order-sync.retry-interval:PT2M}")
@Transactional
public void retryPendingOrderSyncs() {
    try {
        int totalSynced = 0;
        int totalProcessed = 0;
        Page<Payment> page;

        do {
            page = paymentRepository.findOrderSyncPending(
                    List.of(SUCCESS, FAILED, CANCELLED), PageRequest.of(0, 100));

            for (Payment payment : page.getContent()) {
                try {
                    syncOrder(payment);
                    payment.setOrderSyncPending(false);
                    paymentRepository.save(payment);
                    totalSynced++;
                } catch (Exception ex) {
                    log.warn("Order sync retry failed for payment {}. Will retry next cycle.",
                            payment.getId(), ex);
                }
            }
            totalProcessed += page.getNumberOfElements();
        } while (!page.isEmpty());
        // ...
    }
}
```

### Fix

**Step 1** — Add a retry counter and max retries to `Payment.java`. After line 107, add:

```java
@Builder.Default
@Column(name = "order_sync_retry_count", nullable = false)
private int orderSyncRetryCount = 0;

@Builder.Default
@Column(name = "order_sync_max_retries", nullable = false)
private int orderSyncMaxRetries = 10;
```

**Step 2** — Replace the scheduler to process one payment at a time in separate transactions.

Replace `OrderSyncRetryScheduler.java` entirely:
```java
package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static com.rumal.payment_service.entity.PaymentStatus.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncRetryScheduler {

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${payment.order-sync.retry-interval:PT2M}")
    public void retryPendingOrderSyncs() {
        try {
            int totalSynced = 0;
            int totalFailed = 0;
            Page<Payment> page;

            do {
                page = transactionTemplate.execute(status ->
                        paymentRepository.findOrderSyncPending(
                                List.of(SUCCESS, FAILED, CANCELLED), PageRequest.of(0, 100)));

                if (page == null || page.isEmpty()) break;

                for (Payment payment : page.getContent()) {
                    try {
                        syncOrder(payment);
                        transactionTemplate.executeWithoutResult(status -> {
                            Payment p = paymentRepository.findById(payment.getId()).orElse(null);
                            if (p != null) {
                                p.setOrderSyncPending(false);
                                paymentRepository.save(p);
                            }
                        });
                        totalSynced++;
                    } catch (Exception ex) {
                        transactionTemplate.executeWithoutResult(status -> {
                            Payment p = paymentRepository.findById(payment.getId()).orElse(null);
                            if (p != null) {
                                p.setOrderSyncRetryCount(p.getOrderSyncRetryCount() + 1);
                                if (p.getOrderSyncRetryCount() >= p.getOrderSyncMaxRetries()) {
                                    p.setOrderSyncPending(false);
                                    log.error("Order sync permanently failed for payment {} after {} retries",
                                            p.getId(), p.getOrderSyncRetryCount());
                                }
                                paymentRepository.save(p);
                            }
                        });
                        totalFailed++;
                        log.warn("Order sync retry failed for payment {}. Attempt {}/{}.",
                                payment.getId(), payment.getOrderSyncRetryCount() + 1,
                                payment.getOrderSyncMaxRetries(), ex);
                    }
                }
            } while (!page.isEmpty());

            if (totalSynced > 0 || totalFailed > 0) {
                log.info("Order sync retry: {} synced, {} failed", totalSynced, totalFailed);
            }
        } catch (Exception ex) {
            log.error("Error during order sync retry", ex);
        }
    }

    private void syncOrder(Payment payment) {
        if (payment.getStatus() == SUCCESS) {
            orderClient.setPaymentInfo(
                    payment.getOrderId(),
                    payment.getId().toString(),
                    payment.getPaymentMethod(),
                    payment.getPayherePaymentId());
            orderClient.updateOrderStatus(
                    payment.getOrderId(), "CONFIRMED", "Payment confirmed via PayHere (sync retry)");
        } else {
            orderClient.updateOrderStatus(
                    payment.getOrderId(), "PAYMENT_FAILED",
                    "Payment " + payment.getStatus().name().toLowerCase() + " (sync retry)");
        }
    }
}
```

This fix:
- Makes each payment's DB read and HTTP call happen outside a long-running transaction
- Re-reads the payment in a fresh short transaction to update the flag
- Adds a retry counter that caps retries at 10, then permanently clears the flag and logs an error for operator alerting

---

## BUG-PAY-003 — AdminBankAccountController.setPrimary() Race Condition Allows Multiple Primary Accounts

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `controller/AdminBankAccountController.java`, `repo/VendorBankAccountRepository.java` |
| **Lines** | `AdminBankAccountController.java:108–122`, `VendorBankAccountRepository.java:23–25` |

### Description

The `setPrimary()` method has a race condition that can result in a vendor having **multiple primary bank accounts**.

The `@Modifying` JPQL query `unsetPrimaryForVendor` acquires row-level write locks **only on rows where `primary = true`**. When no primary account currently exists for the vendor (e.g., the vendor's accounts were all just created or the previous primary was deactivated), the UPDATE matches zero rows and acquires **no locks**. Two concurrent `setPrimary` requests can then both proceed through the unset step without blocking, and both set their respective accounts as primary.

### Current Code

**`AdminBankAccountController.java:108–122`**:
```java
@PostMapping("/{id}/set-primary")
@Transactional
public VendorBankAccountResponse setPrimary(...) {
    VendorBankAccount account = bankAccountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
    bankAccountRepository.unsetPrimaryForVendor(account.getVendorId());
    account.setPrimary(true);
    return toResponse(bankAccountRepository.save(account));
}
```

**`VendorBankAccountRepository.java:23–25`**:
```java
@Modifying
@Query("UPDATE VendorBankAccount b SET b.primary = false WHERE b.vendorId = :vendorId AND b.primary = true")
int unsetPrimaryForVendor(@Param("vendorId") UUID vendorId);
```

### Scenario

1. Vendor has accounts X (primary=false) and Y (primary=false) — no existing primary
2. Admin A sends `setPrimary(X)`, Admin B sends `setPrimary(Y)` concurrently
3. Both execute `unsetPrimaryForVendor()` — no rows match (`primary = true`), no locks acquired
4. Admin A sets X.primary=true, saves (version check passes)
5. Admin B sets Y.primary=true, saves (version check passes)
6. **Result**: Both X and Y have `primary = true`

### Fix

Move the setPrimary logic into a service method and use a pessimistic lock on all accounts for the vendor.

**Step 1** — Add a pessimistic-locking query to `VendorBankAccountRepository.java`. After line 25, add:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM VendorBankAccount b WHERE b.vendorId = :vendorId AND b.active = true")
List<VendorBankAccount> findByVendorIdAndActiveTrueForUpdate(@Param("vendorId") UUID vendorId);
```

**Step 2** — Replace `AdminBankAccountController.java:108–122`:

```java
@PostMapping("/{id}/set-primary")
@Transactional
public VendorBankAccountResponse setPrimary(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @PathVariable UUID id
) {
    internalRequestVerifier.verify(internalAuth);
    requireUserSub(userSub);
    VendorBankAccount account = bankAccountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));

    // Lock ALL active accounts for this vendor to prevent concurrent setPrimary
    List<VendorBankAccount> allAccounts = bankAccountRepository
            .findByVendorIdAndActiveTrueForUpdate(account.getVendorId());
    for (VendorBankAccount a : allAccounts) {
        a.setPrimary(a.getId().equals(id));
    }
    bankAccountRepository.saveAll(allAccounts);

    // Re-read to return fresh state
    account = bankAccountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
    return toResponse(account);
}
```

Add the required import to `AdminBankAccountController.java`:
```java
import java.util.List;
import jakarta.persistence.LockModeType;
```

This fix locks all active accounts for the vendor before modifying any, ensuring serialized access.

---

## BUG-PAY-004 — Refund Escalation Scheduler Entire Batch Rolls Back on Concurrent Pod Conflict

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/RefundService.java` |
| **Lines** | 341–364 |

### Description

The `escalateExpiredRefunds()` method processes all expired refund requests in a single `@Transactional` without pessimistic locking. In a multi-pod deployment, all pods run the `RefundEscalationScheduler` simultaneously:

1. Pod A and Pod B both query `findByStatusAndVendorResponseDeadlineBefore(REQUESTED, now(), page(0, 100))` — they load the **same** 100 refund requests
2. Pod A saves refund #1 with status=ESCALATED_TO_ADMIN (version 0→1)
3. Pod B tries to save the same refund #1 (still expects version 0, now 1 in DB) → `OptimisticLockingFailureException`
4. The exception propagates up and **rolls back Pod B's entire transaction** — all 100 escalations in that batch are lost

Since the scheduler only catches exceptions at the outermost level (`RefundEscalationScheduler.escalateExpiredRefunds()`), the entire batch fails. The refunds are picked up on the next cycle (1 hour later), but if pods continue to race, this can delay escalations significantly.

### Current Code

**`RefundService.java:341–364`**:
```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public void escalateExpiredRefunds() {
    int totalEscalated = 0;
    Page<RefundRequest> page;

    do {
        page = refundRequestRepository
                .findByStatusAndVendorResponseDeadlineBefore(REQUESTED, Instant.now(), PageRequest.of(0, 100));

        for (RefundRequest refund : page.getContent()) {
            refund.setStatus(ESCALATED_TO_ADMIN);
            refundRequestRepository.save(refund);

            paymentAuditService.writeAudit(null, refund.getId(), null,
                    "REFUND_ESCALATED", "REQUESTED", "ESCALATED_TO_ADMIN",
                    "system", null, null, null);
        }
        totalEscalated += page.getNumberOfElements();
    } while (!page.isEmpty());

    if (totalEscalated > 0) {
        log.info("Escalated {} expired refund requests to admin", totalEscalated);
    }
}
```

### Fix

Process each refund in its own transaction so one failure doesn't roll back the entire batch.

**Step 1** — Add a single-refund escalation method to `RefundService.java`. After line 364, add:

```java
@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
               isolation = Isolation.REPEATABLE_READ, timeout = 10)
public void escalateSingleRefund(UUID refundId) {
    RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
            .orElse(null);
    if (refund == null || refund.getStatus() != REQUESTED) {
        return; // Already escalated by another pod or status changed
    }
    refund.setStatus(ESCALATED_TO_ADMIN);
    refundRequestRepository.save(refund);

    paymentAuditService.writeAudit(null, refund.getId(), null,
            "REFUND_ESCALATED", "REQUESTED", "ESCALATED_TO_ADMIN",
            "system", null, null, null);
}
```

**Step 2** — Replace `escalateExpiredRefunds()` (lines 341–364):

```java
@Transactional(readOnly = true)
public void escalateExpiredRefunds() {
    int totalEscalated = 0;
    Page<RefundRequest> page;

    do {
        page = refundRequestRepository
                .findByStatusAndVendorResponseDeadlineBefore(REQUESTED, Instant.now(), PageRequest.of(0, 100));

        for (RefundRequest refund : page.getContent()) {
            try {
                escalateSingleRefund(refund.getId());
                totalEscalated++;
            } catch (Exception ex) {
                log.warn("Failed to escalate refund {}: {}", refund.getId(), ex.getMessage());
            }
        }
    } while (!page.isEmpty());

    if (totalEscalated > 0) {
        log.info("Escalated {} expired refund requests to admin", totalEscalated);
    }
}
```

**Important**: `escalateSingleRefund` must be called via the Spring proxy (not a `this.` call) for the `REQUIRES_NEW` propagation to work. Since both methods are in the same class (`RefundService`), inject a self-reference:

Add to `RefundService.java` fields (after line 47):
```java
@org.springframework.context.annotation.Lazy
private final RefundService self;
```

And in `escalateExpiredRefunds()`, call via `self`:
```java
self.escalateSingleRefund(refund.getId());
```

This fix:
- Uses pessimistic locking (`findByIdForUpdate`) to prevent duplicate processing
- Re-checks the status after acquiring the lock (guard against concurrent pod)
- Processes each refund in its own `REQUIRES_NEW` transaction so one failure doesn't affect others
- Uses self-injection to avoid Spring proxy bypass

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-PAY-001 | **HIGH** | Architecture & Resilience | Resilience4j retries broken for all 3 client groups (9 methods) |
| BUG-PAY-002 | Medium | Architecture & Data Integrity | OrderSyncRetryScheduler holds DB transaction during HTTP calls, no retry limit |
| BUG-PAY-003 | Medium | Data Integrity & Concurrency | setPrimary race condition allows multiple primary bank accounts |
| BUG-PAY-004 | Low | Data Integrity & Concurrency | Refund escalation batch rolls back entirely on concurrent pod conflict |
