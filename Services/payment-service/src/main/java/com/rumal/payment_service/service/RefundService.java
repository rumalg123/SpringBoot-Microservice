package com.rumal.payment_service.service;

import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.dto.*;
import com.rumal.payment_service.entity.*;
import com.rumal.payment_service.exception.DuplicateResourceException;
import com.rumal.payment_service.exception.PayHereApiException;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.exception.ValidationException;
import com.rumal.payment_service.repo.PaymentAuditRepository;
import com.rumal.payment_service.repo.PaymentRepository;
import com.rumal.payment_service.repo.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAuditRepository auditRepository;
    private final OrderClient orderClient;
    private final PayHereClient payHereClient;

    @Value("${payment.refund.vendor-response-days:7}")
    private int vendorResponseDays;

    // ── Create Refund Request ──────────────────────────────────────────

    @Transactional
    public RefundRequestResponse createRefundRequest(String keycloakId, RefundRequestCreateRequest req) {

        // 1. Find the latest payment for this order
        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(req.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + req.orderId()));

        if (payment.getStatus() != SUCCESS) {
            throw new ValidationException("Payment must be successful to request refund");
        }

        // 2. Check no existing active refund for this vendor order
        refundRequestRepository.findByVendorOrderIdAndStatusNotIn(
                req.vendorOrderId(),
                List.of(ADMIN_REJECTED, REFUND_COMPLETED, REFUND_FAILED)
        ).ifPresent(existing -> {
            throw new DuplicateResourceException("Active refund request already exists for this vendor order");
        });

        // 3. Get vendor order and validate refund amount
        VendorOrderSummary vendorOrder = orderClient.getVendorOrder(req.orderId(), req.vendorOrderId());

        if (req.refundAmount().compareTo(vendorOrder.orderTotal()) > 0) {
            throw new ValidationException("Refund amount cannot exceed vendor order total of " + vendorOrder.orderTotal());
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

        writeAudit(null, refund.getId(), null,
                "REFUND_REQUESTED", null, "REQUESTED",
                "customer", keycloakId, null, null);

        // 6. Return mapped response
        return toResponse(refund);
    }

    // ── Vendor Respond ─────────────────────────────────────────────────

    @Transactional
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
        writeAudit(null, refundId, null,
                "VENDOR_RESPONSE", oldStatus, refund.getStatus().name(),
                "vendor", vendorKeycloakId, null, null);

        // 9. Return mapped response
        return toResponse(refund);
    }

    // ── Admin Finalize ─────────────────────────────────────────────────

    @Transactional
    public RefundRequestResponse adminFinalize(String adminKeycloakId, UUID refundId,
                                                RefundAdminFinalizeRequest req) {

        // 1. Find refund with pessimistic lock
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

        // 2. Verify status is eligible for admin finalization
        if (refund.getStatus() != VENDOR_APPROVED
                && refund.getStatus() != VENDOR_REJECTED
                && refund.getStatus() != ESCALATED_TO_ADMIN) {
            throw new ValidationException("Refund must be in VENDOR_APPROVED, VENDOR_REJECTED, or ESCALATED_TO_ADMIN status for admin finalization");
        }

        // 3. Store old status
        String oldStatus = refund.getStatus().name();

        // 4/5/6. Set admin fields
        refund.setAdminFinalizedBy(adminKeycloakId);
        refund.setAdminFinalizedAt(Instant.now());
        refund.setAdminNote(req.note());

        // 7. Process approval or rejection
        if (req.approved()) {
            refund.setStatus(REFUND_PROCESSING);
            refundRequestRepository.save(refund);

            try {
                Payment payment = refund.getPayment();
                Map<String, Object> result = payHereClient.refund(
                        payment.getPayherePaymentId(),
                        refund.getRefundAmount(),
                        "Refund for order " + refund.getOrderId());

                refund.setPayhereRefundRef(result != null ? result.toString() : null);
                refund.setStatus(REFUND_COMPLETED);
                refund.setRefundCompletedAt(Instant.now());

                // Update vendor order status
                try {
                    orderClient.updateVendorOrderStatus(
                            refund.getOrderId(), refund.getVendorOrderId(),
                            "REFUNDED", "Refund completed");
                } catch (Exception ex) {
                    log.error("Failed to update vendor order {} status to REFUNDED: {}",
                            refund.getVendorOrderId(), ex.getMessage());
                }

            } catch (PayHereApiException ex) {
                log.error("PayHere refund failed for refund request {}: {}",
                        refundId, ex.getMessage());
                refund.setStatus(REFUND_FAILED);
            }

            refund = refundRequestRepository.save(refund);

            writeAudit(null, refundId, null,
                    "ADMIN_FINALIZE", oldStatus, refund.getStatus().name(),
                    "admin", adminKeycloakId, null, null);

        } else {
            // 8. Rejected
            refund.setStatus(ADMIN_REJECTED);
            refund = refundRequestRepository.save(refund);

            writeAudit(null, refundId, null,
                    "ADMIN_FINALIZE", oldStatus, "ADMIN_REJECTED",
                    "admin", adminKeycloakId, null, null);
        }

        // 9. Return mapped response
        return toResponse(refund);
    }

    // ── List & Get Methods ─────────────────────────────────────────────

    public Page<RefundRequestResponse> listRefundsForCustomer(String keycloakId, Pageable pageable) {
        return refundRequestRepository.findByCustomerKeycloakId(keycloakId, pageable)
                .map(this::toResponse);
    }

    public Page<RefundRequestResponse> listRefundsForVendor(UUID vendorId, RefundStatus status, Pageable pageable) {
        return refundRequestRepository.findByVendorFiltered(vendorId, status, pageable)
                .map(this::toResponse);
    }

    public Page<RefundRequestResponse> listAllRefunds(UUID vendorId, RefundStatus status, Pageable pageable) {
        return refundRequestRepository.findAllFiltered(vendorId, status, pageable)
                .map(this::toResponse);
    }

    public RefundRequestResponse getRefundById(UUID refundId) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));
        return toResponse(refund);
    }

    public RefundRequestResponse getRefundByIdAndCustomer(UUID refundId, String keycloakId) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

        if (!refund.getCustomerKeycloakId().equals(keycloakId)) {
            throw new UnauthorizedException("You are not authorized to view this refund request");
        }
        return toResponse(refund);
    }

    public RefundRequestResponse getRefundByIdAndVendor(UUID refundId, UUID vendorId) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

        if (!refund.getVendorId().equals(vendorId)) {
            throw new UnauthorizedException("You are not authorized to view this refund request");
        }
        return toResponse(refund);
    }

    // ── Escalate Expired Refunds (called by scheduler) ─────────────────

    @Transactional
    public void escalateExpiredRefunds() {
        List<RefundRequest> expired = refundRequestRepository
                .findByStatusAndVendorResponseDeadlineBefore(REQUESTED, Instant.now());

        for (RefundRequest refund : expired) {
            refund.setStatus(ESCALATED_TO_ADMIN);
            refundRequestRepository.save(refund);

            writeAudit(null, refund.getId(), null,
                    "REFUND_ESCALATED", "REQUESTED", "ESCALATED_TO_ADMIN",
                    "system", null, null, null);
        }

        if (!expired.isEmpty()) {
            log.info("Escalated {} expired refund requests to admin", expired.size());
        }
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

    private void writeAudit(UUID paymentId, UUID refundRequestId, UUID payoutId,
                            String eventType, String fromStatus, String toStatus,
                            String actorType, String actorId, String note, String rawPayload) {
        PaymentAudit audit = PaymentAudit.builder()
                .paymentId(paymentId)
                .refundRequestId(refundRequestId)
                .payoutId(payoutId)
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorType(actorType)
                .actorId(actorId)
                .note(note)
                .rawPayload(rawPayload)
                .build();
        auditRepository.save(audit);
    }
}
