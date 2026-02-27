package com.rumal.payment_service.service;

import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.dto.*;
import com.rumal.payment_service.entity.*;
import com.rumal.payment_service.exception.DuplicateResourceException;
import com.rumal.payment_service.exception.PayHereApiException;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.exception.ValidationException;
import com.rumal.payment_service.repo.PaymentRepository;
import com.rumal.payment_service.repo.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.rumal.payment_service.entity.PaymentStatus.SUCCESS;
import static com.rumal.payment_service.entity.RefundStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAuditService paymentAuditService;
    private final OrderClient orderClient;
    private final PayHereClient payHereClient;
    private final TransactionTemplate transactionTemplate;
    @org.springframework.context.annotation.Lazy
    private final RefundService self;

    @Value("${payment.refund.vendor-response-days:7}")
    private int vendorResponseDays;

    // ── Create Refund Request ──────────────────────────────────────────

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public RefundRequestResponse createRefundRequest(String keycloakId, RefundRequestCreateRequest req) {

        // 1. Find the latest payment for this order
        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(req.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + req.orderId()));

        if (payment.getStatus() != SUCCESS) {
            throw new ValidationException("Payment must be successful to request refund");
        }

        // 2. Check no existing active refund for this vendor order (with lock to prevent duplicates)
        refundRequestRepository.findByVendorOrderIdAndStatusNotInForUpdate(
                req.vendorOrderId(),
                List.of(ADMIN_REJECTED, REFUND_COMPLETED, REFUND_FAILED)
        ).ifPresent(existing -> {
            throw new DuplicateResourceException("Active refund request already exists for this vendor order");
        });

        // 3. Get vendor order and validate refund amount
        VendorOrderSummary vendorOrder = orderClient.getVendorOrder(req.orderId(), req.vendorOrderId());

        // C-03: Check cumulative refunded amount (not just this request) to prevent exceeding order total
        BigDecimal alreadyRefunded = refundRequestRepository
                .sumRefundedAmountByVendorOrderId(req.vendorOrderId());
        BigDecimal totalAfterThisRefund = alreadyRefunded.add(req.refundAmount());

        if (totalAfterThisRefund.compareTo(vendorOrder.orderTotal()) > 0) {
            throw new ValidationException(
                    "Refund amount would exceed vendor order total. Order total: " + vendorOrder.orderTotal()
                    + ", already refunded/processing: " + alreadyRefunded
                    + ", requested: " + req.refundAmount());
        }

        // 4. Build RefundRequest entity
        RefundRequest refund = RefundRequest.builder()
                .payment(payment)
                .orderId(req.orderId())
                .vendorOrderId(req.vendorOrderId())
                .vendorId(vendorOrder.vendorId())
                .customerId(payment.getCustomerId())
                .customerKeycloakId(keycloakId)
                .refundAmount(req.refundAmount())
                .currency(payment.getCurrency())
                .customerReason(req.reason())
                .status(REQUESTED)
                .vendorResponseDeadline(Instant.now().plus(vendorResponseDays, ChronoUnit.DAYS))
                .build();

        // 5. Save and write audit
        refund = refundRequestRepository.save(refund);

        paymentAuditService.writeAudit(null, refund.getId(), null,
                "REFUND_REQUESTED", null, "REQUESTED",
                "customer", keycloakId, null, null);

        // 6. Return mapped response
        return toResponse(refund);
    }

    // ── Vendor Respond ─────────────────────────────────────────────────

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public RefundRequestResponse vendorRespond(String vendorKeycloakId, UUID vendorId,
                                                UUID refundId, RefundVendorResponseRequest req) {

        // 1. Find refund with pessimistic lock
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

        // 2. Verify vendor ownership
        if (!refund.getVendorId().equals(vendorId)) {
            throw new UnauthorizedException("You are not authorized to respond to this refund request");
        }

        // 3. Verify status
        if (refund.getStatus() != REQUESTED) {
            throw new ValidationException("Refund is not in REQUESTED status");
        }

        // 4. Store old status
        String oldStatus = refund.getStatus().name();

        // 5/6. Update based on approval
        if (req.approved()) {
            refund.setStatus(VENDOR_APPROVED);
        } else {
            refund.setStatus(VENDOR_REJECTED);
        }
        refund.setVendorResponseNote(req.note());
        refund.setVendorRespondedBy(vendorKeycloakId);
        refund.setVendorRespondedAt(Instant.now());

        // 7. Save
        refund = refundRequestRepository.save(refund);

        // 8. Write audit
        paymentAuditService.writeAudit(null, refundId, null,
                "VENDOR_RESPONSE", oldStatus, refund.getStatus().name(),
                "vendor", vendorKeycloakId, null, null);

        // 9. Return mapped response
        return toResponse(refund);
    }

    // ── Admin Finalize ─────────────────────────────────────────────────
    // C-01: Split into phases so the external PayHere API call does NOT hold a DB lock.
    // C-02: Validate PayHere refund response before marking REFUND_COMPLETED.

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundRequestResponse adminFinalize(String adminKeycloakId, UUID refundId,
                                                RefundAdminFinalizeRequest req) {

        // ── Phase 1: Validate & set REFUND_PROCESSING (or ADMIN_REJECTED) inside a short transaction ──
        String oldStatus = transactionTemplate.execute(status -> {
            RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                    .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

            if (refund.getStatus() != VENDOR_APPROVED
                    && refund.getStatus() != VENDOR_REJECTED
                    && refund.getStatus() != ESCALATED_TO_ADMIN) {
                throw new ValidationException("Refund must be in VENDOR_APPROVED, VENDOR_REJECTED, or ESCALATED_TO_ADMIN status for admin finalization");
            }

            String prevStatus = refund.getStatus().name();

            refund.setAdminFinalizedBy(adminKeycloakId);
            refund.setAdminFinalizedAt(Instant.now());
            refund.setAdminNote(req.note());

            if (req.approved()) {
                refund.setStatus(REFUND_PROCESSING);
            } else {
                refund.setStatus(ADMIN_REJECTED);
            }

            refundRequestRepository.save(refund);
            return prevStatus;
        });

        // ── If rejected, write audit and return immediately ──
        if (!req.approved()) {
            paymentAuditService.writeAudit(null, refundId, null,
                    "ADMIN_FINALIZE", oldStatus, "ADMIN_REJECTED",
                    "admin", adminKeycloakId, null, null);

            return toResponse(refundRequestRepository.findById(refundId)
                    .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId)));
        }

        // ── Phase 2: Call PayHere API *outside* any transaction (no DB lock held) ──
        Map<String, Object> payHereResult = null;
        PayHereApiException payHereError = null;
        try {
            Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(
                    refundRequestRepository.findById(refundId)
                            .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId))
                            .getOrderId()
            ).orElseThrow(() -> new ResourceNotFoundException("Payment not found for refund: " + refundId));

            payHereResult = payHereClient.refund(
                    payment.getPayherePaymentId(),
                    refundRequestRepository.findById(refundId).orElseThrow().getRefundAmount(),
                    "Refund for order " + refundRequestRepository.findById(refundId).orElseThrow().getOrderId());
        } catch (PayHereApiException ex) {
            log.error("PayHere refund failed for refund request {}: {}", refundId, ex.getMessage());
            payHereError = ex;
        }

        // ── Phase 3: Re-acquire lock, verify still REFUND_PROCESSING, set final status ──
        final Map<String, Object> finalResult = payHereResult;
        final PayHereApiException finalError = payHereError;

        transactionTemplate.executeWithoutResult(status -> {
            RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                    .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

            if (refund.getStatus() != REFUND_PROCESSING) {
                log.warn("Refund {} status changed to {} during PayHere call, skipping update",
                        refundId, refund.getStatus());
                return;
            }

            if (finalError != null) {
                refund.setStatus(REFUND_FAILED);
                refund.setPayhereRefundRef(finalError.getMessage());
            } else {
                // C-02: Validate PayHere response before marking completed
                boolean success = isPayHereRefundSuccessful(finalResult);
                if (success) {
                    refund.setStatus(REFUND_COMPLETED);
                    refund.setRefundCompletedAt(Instant.now());
                    refund.setPayhereRefundRef(finalResult != null ? finalResult.toString() : null);
                } else {
                    refund.setStatus(REFUND_FAILED);
                    String errorMsg = finalResult != null ? finalResult.toString() : "No response from PayHere";
                    refund.setPayhereRefundRef(errorMsg);
                    log.error("PayHere refund response indicates failure for refund {}: {}", refundId, errorMsg);
                }
            }

            refundRequestRepository.save(refund);

            paymentAuditService.writeAudit(null, refundId, null,
                    "ADMIN_FINALIZE", oldStatus, refund.getStatus().name(),
                    "admin", adminKeycloakId, null, null);

            // Sync vendor order status if refund completed
            if (refund.getStatus() == REFUND_COMPLETED) {
                try {
                    orderClient.updateVendorOrderStatus(
                            refund.getOrderId(), refund.getVendorOrderId(),
                            "REFUNDED", "Refund completed");
                } catch (Exception ex) {
                    log.error("Failed to update vendor order {} status to REFUNDED: {}",
                            refund.getVendorOrderId(), ex.getMessage());
                }
            }
        });

        return toResponse(refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId)));
    }

    private boolean isPayHereRefundSuccessful(Map<String, Object> result) {
        if (result == null) return false;
        Object status = result.get("status");
        if (status == null) {
            // If PayHere returned data but no explicit status field, treat as success
            // (some PayHere endpoints return the refund object on success)
            return !result.containsKey("error");
        }
        String statusStr = status.toString().toLowerCase();
        return "1".equals(statusStr) || "success".equals(statusStr) || "true".equals(statusStr);
    }

    // ── List & Get Methods ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listRefundsForCustomer(String keycloakId, Pageable pageable) {
        return refundRequestRepository.findByCustomerKeycloakId(keycloakId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listRefundsForVendor(UUID vendorId, RefundStatus status, Pageable pageable) {
        return refundRequestRepository.findByVendorFiltered(vendorId, status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listAllRefunds(UUID vendorId, RefundStatus status, Pageable pageable) {
        return refundRequestRepository.findAllFiltered(vendorId, status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse getRefundById(UUID refundId) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));
        return toResponse(refund);
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse getRefundByIdAndCustomer(UUID refundId, String keycloakId) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

        if (!refund.getCustomerKeycloakId().equals(keycloakId)) {
            throw new UnauthorizedException("You are not authorized to view this refund request");
        }
        return toResponse(refund);
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse getRefundByIdAndVendor(UUID refundId, UUID vendorId) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

        if (!refund.getVendorId().equals(vendorId)) {
            throw new UnauthorizedException("You are not authorized to view this refund request");
        }
        return toResponse(refund);
    }

    // ── Escalate Expired Refunds (called by scheduler) ─────────────────

    @Transactional(readOnly = true)
    public void escalateExpiredRefunds() {
        int totalEscalated = 0;
        Page<RefundRequest> page;

        do {
            page = refundRequestRepository
                    .findByStatusAndVendorResponseDeadlineBefore(REQUESTED, Instant.now(), PageRequest.of(0, 100));

            for (RefundRequest refund : page.getContent()) {
                try {
                    self.escalateSingleRefund(refund.getId());
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

    @Transactional(propagation = Propagation.REQUIRES_NEW,
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

    // ── Private Helpers ────────────────────────────────────────────────

    private RefundRequestResponse toResponse(RefundRequest r) {
        return new RefundRequestResponse(
                r.getId(),
                r.getPayment().getId(),
                r.getOrderId(),
                r.getVendorOrderId(),
                r.getVendorId(),
                r.getCustomerId(),
                r.getRefundAmount(),
                r.getCurrency(),
                r.getCustomerReason(),
                r.getVendorResponseNote(),
                r.getAdminNote(),
                r.getStatus().name(),
                r.getVendorResponseDeadline(),
                r.getPayhereRefundRef(),
                r.getVendorRespondedAt(),
                r.getAdminFinalizedAt(),
                r.getRefundCompletedAt(),
                r.getCreatedAt()
        );
    }

}
