package com.rumal.payment_service.service;

import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.dto.RefundAdminFinalizeRequest;
import com.rumal.payment_service.dto.RefundRequestCreateRequest;
import com.rumal.payment_service.dto.RefundRequestResponse;
import com.rumal.payment_service.dto.RefundVendorResponseRequest;
import com.rumal.payment_service.dto.VendorOrderStatusHistoryEntry;
import com.rumal.payment_service.dto.VendorOrderSummary;
import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.RefundRequest;
import com.rumal.payment_service.entity.RefundStatus;
import com.rumal.payment_service.exception.DuplicateResourceException;
import com.rumal.payment_service.exception.PayHereApiException;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.exception.ValidationException;
import com.rumal.payment_service.repo.PaymentRepository;
import com.rumal.payment_service.repo.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.rumal.payment_service.entity.PaymentStatus.SUCCESS;
import static com.rumal.payment_service.entity.RefundStatus.ADMIN_REJECTED;
import static com.rumal.payment_service.entity.RefundStatus.ESCALATED_TO_ADMIN;
import static com.rumal.payment_service.entity.RefundStatus.REFUND_COMPLETED;
import static com.rumal.payment_service.entity.RefundStatus.REFUND_FAILED;
import static com.rumal.payment_service.entity.RefundStatus.REFUND_PROCESSING;
import static com.rumal.payment_service.entity.RefundStatus.REQUESTED;
import static com.rumal.payment_service.entity.RefundStatus.VENDOR_APPROVED;
import static com.rumal.payment_service.entity.RefundStatus.VENDOR_REJECTED;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class RefundService {

    private static final List<RefundStatus> ACTIVE_REFUND_TERMINAL_STATUSES = List.of(ADMIN_REJECTED, REFUND_COMPLETED);
    private static final Set<String> CUSTOMER_REFUNDABLE_VENDOR_STATUSES = Set.of("DELIVERED", "RETURN_REJECTED");

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAuditService paymentAuditService;
    private final OrderClient orderClient;
    private final PayHereClient payHereClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<RefundService> selfProvider;

    @Value("${payment.refund.vendor-response-days:3}")
    private int vendorResponseDays;

    @Value("${payment.refund.customer-window-days:30}")
    private int customerRefundWindowDays;

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public RefundRequestResponse createRefundRequest(String keycloakId, RefundRequestCreateRequest request) {
        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + request.orderId()));
        if (payment.getStatus() != SUCCESS) {
            throw new ValidationException("Payment must be successful to request refund");
        }
        if (!keycloakId.equals(payment.getCustomerKeycloakId())) {
            throw new UnauthorizedException("You are not authorized to request a refund for this order");
        }

        VendorOrderSummary vendorOrder = orderClient.getVendorOrder(request.orderId(), request.vendorOrderId());
        validateCustomerRefundEligibility(request, vendorOrder);

        refundRequestRepository.findByVendorOrderIdAndStatusNotInForUpdate(
                request.vendorOrderId(),
                ACTIVE_REFUND_TERMINAL_STATUSES
        ).ifPresent(existing -> {
            throw new DuplicateResourceException("Active refund request already exists for this vendor order");
        });

        BigDecimal alreadyRefunded = refundRequestRepository.sumRefundedAmountByVendorOrderId(request.vendorOrderId());
        BigDecimal totalAfterThisRefund = alreadyRefunded.add(request.refundAmount());
        if (totalAfterThisRefund.compareTo(vendorOrder.orderTotal()) > 0) {
            throw new ValidationException(
                    "Refund amount would exceed vendor order total. Order total: " + vendorOrder.orderTotal()
                            + ", already refunded or processing: " + alreadyRefunded
                            + ", requested: " + request.refundAmount()
            );
        }

        BigDecimal totalPaymentRefunds = refundRequestRepository.sumActiveRefundsByPaymentId(payment.getId());
        BigDecimal totalPaymentRefundsAfterThis = totalPaymentRefunds.add(request.refundAmount());
        if (totalPaymentRefundsAfterThis.compareTo(payment.getAmount()) > 0) {
            throw new ValidationException(
                    "Cumulative refunds (" + totalPaymentRefundsAfterThis
                            + ") would exceed payment amount (" + payment.getAmount() + ")"
            );
        }

        RefundRequest refund = RefundRequest.builder()
                .payment(payment)
                .orderId(request.orderId())
                .vendorOrderId(request.vendorOrderId())
                .vendorId(vendorOrder.vendorId())
                .customerId(payment.getCustomerId())
                .customerKeycloakId(keycloakId)
                .refundAmount(request.refundAmount())
                .currency(payment.getCurrency())
                .customerReason(request.reason().trim())
                .status(REQUESTED)
                .vendorResponseDeadline(Instant.now().plus(vendorResponseDays, ChronoUnit.DAYS))
                .build();

        RefundRequest saved = refundRequestRepository.save(refund);
        paymentAuditService.writeAudit(
                payment.getId(),
                saved.getId(),
                null,
                "REFUND_REQUESTED",
                null,
                REQUESTED.name(),
                "customer",
                keycloakId,
                saved.getCustomerReason(),
                null
        );

        syncVendorOrderStatusBestEffort(
                saved.getOrderId(),
                saved.getVendorOrderId(),
                "RETURN_REQUESTED",
                "Customer requested refund",
                null,
                null,
                null
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public RefundRequestResponse vendorRespond(
            String vendorKeycloakId,
            UUID vendorId,
            UUID refundId,
            RefundVendorResponseRequest request
    ) {
        requireDecisionNoteIfRejected(request.approved(), request.note(), "Vendors must provide a reason when rejecting a refund request");
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));
        if (!refund.getVendorId().equals(vendorId)) {
            throw new UnauthorizedException("You are not authorized to respond to this refund request");
        }
        if (refund.getStatus() != REQUESTED) {
            throw new ValidationException("Refund is not in REQUESTED status");
        }

        String oldStatus = refund.getStatus().name();
        refund.setStatus(Boolean.TRUE.equals(request.approved()) ? VENDOR_APPROVED : VENDOR_REJECTED);
        refund.setVendorResponseNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        refund.setVendorRespondedBy(vendorKeycloakId);
        refund.setVendorRespondedAt(Instant.now());
        RefundRequest saved = refundRequestRepository.save(refund);

        paymentAuditService.writeAudit(
                saved.getPayment().getId(),
                saved.getId(),
                null,
                "VENDOR_RESPONSE",
                oldStatus,
                saved.getStatus().name(),
                "vendor",
                vendorKeycloakId,
                saved.getVendorResponseNote(),
                null
        );
        if (saved.getStatus() == VENDOR_REJECTED) {
            syncVendorOrderStatusBestEffort(
                    saved.getOrderId(),
                    saved.getVendorOrderId(),
                    "RETURN_REJECTED",
                    StringUtils.hasText(saved.getVendorResponseNote())
                            ? saved.getVendorResponseNote()
                            : "Refund request rejected by vendor",
                    null,
                    null,
                    null
            );
        }
        return toResponse(saved);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundRequestResponse adminFinalize(String adminKeycloakId, UUID refundId, RefundAdminFinalizeRequest request) {
        requireDecisionNoteIfRejected(request.approved(), request.note(), "Platform review note is required when rejecting a refund request");
        RefundProcessingContext context = transactionTemplate.execute(status -> {
            RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                    .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));
            validateAdminFinalizationState(refund.getStatus());

            String oldStatus = refund.getStatus().name();
            refund.setAdminFinalizedBy(adminKeycloakId);
            refund.setAdminFinalizedAt(Instant.now());
            refund.setAdminNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
            refund.setStatus(Boolean.TRUE.equals(request.approved()) ? REFUND_PROCESSING : ADMIN_REJECTED);
            RefundRequest saved = refundRequestRepository.save(refund);

            return new RefundProcessingContext(
                    saved.getId(),
                    saved.getPayment().getId(),
                    saved.getOrderId(),
                    saved.getVendorOrderId(),
                    saved.getRefundAmount(),
                    saved.getCustomerReason(),
                    oldStatus,
                    saved.getStatus(),
                    saved.getAdminNote()
            );
        });
        if (context == null) {
            throw new ValidationException("Unable to finalize refund");
        }

        if (!Boolean.TRUE.equals(request.approved())) {
            paymentAuditService.writeAudit(
                    context.paymentId(),
                    context.refundId(),
                    null,
                    "ADMIN_FINALIZE",
                    context.oldStatus(),
                    ADMIN_REJECTED.name(),
                    "admin",
                    adminKeycloakId,
                    context.note(),
                    null
            );
            syncVendorOrderStatusBestEffort(
                    context.orderId(),
                    context.vendorOrderId(),
                    "CLOSED",
                    StringUtils.hasText(context.note()) ? context.note() : "Refund request rejected",
                    null,
                    null,
                    null
            );
            return getRefundById(refundId);
        }

        syncVendorOrderStatusBestEffort(
                context.orderId(),
                context.vendorOrderId(),
                "REFUND_PENDING",
                "Refund approved and pending settlement",
                context.customerReason(),
                context.refundAmount(),
                null
        );

        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(context.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for refund: " + refundId));
        String payHerePaymentId = payment.getPayherePaymentId();

        Map<String, Object> payHereResult = null;
        PayHereApiException payHereError = null;
        try {
            payHereResult = payHereClient.refund(
                    payHerePaymentId,
                    context.refundAmount(),
                    "Refund for order " + context.orderId()
            );
        } catch (PayHereApiException ex) {
            log.error("PayHere refund failed for refund request {}: {}", refundId, ex.getMessage());
            payHereError = ex;
        }

        final Map<String, Object> finalResult = payHereResult;
        final PayHereApiException finalError = payHereError;
        final RefundOutcome outcome = transactionTemplate.execute(status -> {
            RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                    .orElseThrow(() -> new ResourceNotFoundException("Refund request not found: " + refundId));

            if (refund.getStatus() != REFUND_PROCESSING) {
                log.warn("Refund {} changed status to {} while settlement was in flight", refundId, refund.getStatus());
                return new RefundOutcome(refund.getStatus(), refund.getPayment().getId(), refund.getAdminNote());
            }

            if (finalError != null) {
                refund.setStatus(REFUND_FAILED);
                refund.setPayhereRefundRef(finalError.getMessage());
            } else if (isPayHereRefundSuccessful(finalResult)) {
                refund.setStatus(REFUND_COMPLETED);
                refund.setRefundCompletedAt(Instant.now());
                refund.setPayhereRefundRef(finalResult == null ? null : finalResult.toString());
            } else {
                refund.setStatus(REFUND_FAILED);
                refund.setPayhereRefundRef(finalResult == null ? "No response from PayHere" : finalResult.toString());
            }

            RefundRequest saved = refundRequestRepository.save(refund);
            paymentAuditService.writeAudit(
                    saved.getPayment().getId(),
                    saved.getId(),
                    null,
                    "ADMIN_FINALIZE",
                    context.oldStatus(),
                    saved.getStatus().name(),
                    "admin",
                    adminKeycloakId,
                    saved.getAdminNote(),
                    null
            );
            return new RefundOutcome(saved.getStatus(), saved.getPayment().getId(), saved.getAdminNote());
        });
        if (outcome == null) {
            throw new ValidationException("Unable to complete refund finalization");
        }

        if (outcome.status() == REFUND_COMPLETED) {
            syncVendorOrderStatusBestEffort(
                    context.orderId(),
                    context.vendorOrderId(),
                    "REFUNDED",
                    "Refund completed",
                    context.customerReason(),
                    context.refundAmount(),
                    null
            );
        }

        return getRefundById(refundId);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listRefundsForCustomer(
            String keycloakId,
            UUID orderId,
            UUID vendorOrderId,
            RefundStatus status,
            Pageable pageable
    ) {
        return refundRequestRepository.findByCustomerFiltered(keycloakId, orderId, vendorOrderId, status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listRefundsForVendor(
            UUID vendorId,
            UUID orderId,
            UUID vendorOrderId,
            RefundStatus status,
            Pageable pageable
    ) {
        return refundRequestRepository.findByVendorFiltered(vendorId, orderId, vendorOrderId, status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listAllRefunds(
            UUID vendorId,
            UUID orderId,
            UUID vendorOrderId,
            RefundStatus status,
            Pageable pageable
    ) {
        return refundRequestRepository.findAllFiltered(vendorId, orderId, vendorOrderId, status, pageable)
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

    @Transactional(readOnly = true)
    public void escalateExpiredRefunds() {
        int totalEscalated = 0;
        Page<RefundRequest> page;

        do {
            page = refundRequestRepository.findByStatusAndVendorResponseDeadlineBefore(
                    REQUESTED,
                    Instant.now(),
                    PageRequest.of(0, 100)
            );
            for (RefundRequest refund : page.getContent()) {
                try {
                    selfProvider.getObject().escalateSingleRefund(refund.getId());
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

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public void escalateSingleRefund(UUID refundId) {
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId).orElse(null);
        if (refund == null || refund.getStatus() != REQUESTED) {
            return;
        }
        refund.setStatus(ESCALATED_TO_ADMIN);
        RefundRequest saved = refundRequestRepository.save(refund);
        paymentAuditService.writeAudit(
                saved.getPayment().getId(),
                saved.getId(),
                null,
                "REFUND_ESCALATED",
                REQUESTED.name(),
                ESCALATED_TO_ADMIN.name(),
                "system",
                null,
                "Vendor response deadline expired",
                null
        );
    }

    private void validateCustomerRefundEligibility(RefundRequestCreateRequest request, VendorOrderSummary vendorOrder) {
        if (vendorOrder.orderId() != null && !vendorOrder.orderId().equals(request.orderId())) {
            throw new ValidationException("Vendor order does not belong to the requested order");
        }

        String vendorOrderStatus = normalizeStatus(vendorOrder.status());
        if (!CUSTOMER_REFUNDABLE_VENDOR_STATUSES.contains(vendorOrderStatus)) {
            throw new ValidationException("Refunds can only be requested for delivered vendor orders that remain within the refund window");
        }

        Instant deliveredAt = resolveDeliveredAt(request.vendorOrderId());
        if (deliveredAt == null) {
            throw new ValidationException("Vendor order has not been delivered yet");
        }
        Instant refundableUntil = deliveredAt.plus(customerRefundWindowDays, ChronoUnit.DAYS);
        if (Instant.now().isAfter(refundableUntil)) {
            throw new ValidationException("Refund window has expired for this vendor order");
        }
    }

    private Instant resolveDeliveredAt(UUID vendorOrderId) {
        return orderClient.getVendorOrderStatusHistory(vendorOrderId).stream()
                .filter(entry -> "DELIVERED".equals(normalizeStatus(entry.toStatus())))
                .map(VendorOrderStatusHistoryEntry::createdAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private void validateAdminFinalizationState(RefundStatus status) {
        if (status == null) {
            throw new ValidationException("Refund status is required");
        }
        if (status != REQUESTED
                && status != VENDOR_APPROVED
                && status != VENDOR_REJECTED
                && status != ESCALATED_TO_ADMIN
                && status != REFUND_FAILED) {
            throw new ValidationException(
                    "Refund must be in REQUESTED, VENDOR_APPROVED, VENDOR_REJECTED, ESCALATED_TO_ADMIN, or REFUND_FAILED status for admin finalization"
            );
        }
    }

    private void requireDecisionNoteIfRejected(Boolean approved, String note, String message) {
        if (Boolean.FALSE.equals(approved) && !StringUtils.hasText(note)) {
            throw new ValidationException(message);
        }
    }

    private void syncVendorOrderStatusBestEffort(
            UUID orderId,
            UUID vendorOrderId,
            String status,
            String reason,
            String refundReason,
            BigDecimal refundAmount,
            Integer refundedQuantity
    ) {
        try {
            orderClient.updateVendorOrderStatus(orderId, vendorOrderId, status, reason, refundReason, refundAmount, refundedQuantity);
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync vendor order {} to status {} during refund flow: {}",
                    vendorOrderId,
                    status,
                    ex.getMessage()
            );
        }
    }

    private boolean isPayHereRefundSuccessful(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        Object status = result.get("status");
        if (status == null) {
            return !result.containsKey("error");
        }
        String statusValue = status.toString().toLowerCase(Locale.ROOT);
        return "1".equals(statusValue) || "success".equals(statusValue) || "true".equals(statusValue);
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private RefundRequestResponse toResponse(RefundRequest refund) {
        return new RefundRequestResponse(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getOrderId(),
                refund.getVendorOrderId(),
                refund.getVendorId(),
                refund.getCustomerId(),
                refund.getRefundAmount(),
                refund.getCurrency(),
                refund.getCustomerReason(),
                refund.getVendorResponseNote(),
                refund.getAdminNote(),
                refund.getStatus().name(),
                refund.getVendorResponseDeadline(),
                refund.getPayhereRefundRef(),
                refund.getVendorRespondedAt(),
                refund.getAdminFinalizedAt(),
                refund.getRefundCompletedAt(),
                refund.getCreatedAt()
        );
    }

    private record RefundProcessingContext(
            UUID refundId,
            UUID paymentId,
            UUID orderId,
            UUID vendorOrderId,
            BigDecimal refundAmount,
            String customerReason,
            String oldStatus,
            RefundStatus currentStatus,
            String note
    ) {
    }

    private record RefundOutcome(
            RefundStatus status,
            UUID paymentId,
            String note
    ) {
    }
}
