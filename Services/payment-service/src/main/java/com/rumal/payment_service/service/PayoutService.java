package com.rumal.payment_service.service;

import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.dto.*;
import com.rumal.payment_service.entity.PaymentAudit;
import com.rumal.payment_service.entity.PayoutStatus;
import com.rumal.payment_service.entity.VendorBankAccount;
import com.rumal.payment_service.entity.VendorPayout;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.ValidationException;
import com.rumal.payment_service.repo.PaymentAuditRepository;
import com.rumal.payment_service.repo.VendorBankAccountRepository;
import com.rumal.payment_service.repo.VendorPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.rumal.payment_service.entity.PayoutStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final VendorPayoutRepository payoutRepository;
    private final VendorBankAccountRepository bankAccountRepository;
    private final PaymentAuditRepository auditRepository;
    private final OrderClient orderClient;

    // ── Create Payout ──────────────────────────────────────────────────

    @Transactional
    public VendorPayoutResponse createPayout(String adminKeycloakId, CreatePayoutRequest req) {

        // 1. Find and validate bank account
        VendorBankAccount bankAccount = bankAccountRepository.findById(req.bankAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + req.bankAccountId()));

        if (!bankAccount.getVendorId().equals(req.vendorId())) {
            throw new ValidationException("Bank account does not belong to the specified vendor");
        }

        if (!bankAccount.isActive()) {
            throw new ValidationException("Bank account is not active");
        }

        // 2. Convert vendor order IDs to comma-separated string
        String vendorOrderIdsStr = req.vendorOrderIds().stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        // 3. Build VendorPayout entity
        VendorPayout payout = VendorPayout.builder()
                .vendorId(req.vendorId())
                .payoutAmount(req.payoutAmount())
                .platformFee(req.platformFee())
                .currency("USD")
                .vendorOrderIds(vendorOrderIdsStr)
                .bankAccount(bankAccount)
                .bankNameSnapshot(bankAccount.getBankName())
                .accountNumberSnapshot(bankAccount.getAccountNumber())
                .accountHolderSnapshot(bankAccount.getAccountHolderName())
                .branchCodeSnapshot(bankAccount.getBranchCode())
                .status(PENDING)
                .adminNote(req.adminNote())
                .build();

        // 4. Save and write audit
        payout = payoutRepository.save(payout);

        writeAudit(null, null, payout.getId(),
                "PAYOUT_CREATED", null, "PENDING",
                "admin", adminKeycloakId, null, null);

        // 5. Return mapped response
        return toResponse(payout);
    }

    // ── Approve Payout ─────────────────────────────────────────────────

    @Transactional
    public VendorPayoutResponse approvePayout(String adminKeycloakId, UUID payoutId) {

        // 1. Find payout with pessimistic lock
        VendorPayout payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));

        // 2. Verify status
        if (payout.getStatus() != PENDING) {
            throw new ValidationException("Payout must be in PENDING status to approve");
        }

        // 3. Update status
        String oldStatus = payout.getStatus().name();
        payout.setStatus(APPROVED);
        payout.setApprovedBy(adminKeycloakId);
        payout.setApprovedAt(Instant.now());

        // 4. Save and write audit
        payout = payoutRepository.save(payout);

        writeAudit(null, null, payoutId,
                "PAYOUT_APPROVED", oldStatus, "APPROVED",
                "admin", adminKeycloakId, null, null);

        // 5. Return mapped response
        return toResponse(payout);
    }

    // ── Complete Payout ────────────────────────────────────────────────

    @Transactional
    public VendorPayoutResponse completePayout(String adminKeycloakId, UUID payoutId,
                                                CompletePayoutRequest req) {

        // 1. Find payout with pessimistic lock
        VendorPayout payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));

        // 2. Verify status
        if (payout.getStatus() != APPROVED) {
            throw new ValidationException("Payout must be in APPROVED status to complete");
        }

        // 3. Update fields
        String oldStatus = payout.getStatus().name();
        payout.setStatus(COMPLETED);
        payout.setCompletedBy(adminKeycloakId);
        payout.setCompletedAt(Instant.now());
        payout.setReferenceNumber(req.referenceNumber());

        // 4. Append admin note if provided
        if (req.adminNote() != null && !req.adminNote().isBlank()) {
            String existingNote = payout.getAdminNote();
            if (existingNote != null && !existingNote.isBlank()) {
                payout.setAdminNote(existingNote + " | " + req.adminNote());
            } else {
                payout.setAdminNote(req.adminNote());
            }
        }

        // 5. Save and write audit
        payout = payoutRepository.save(payout);

        writeAudit(null, null, payoutId,
                "PAYOUT_COMPLETED", oldStatus, "COMPLETED",
                "admin", adminKeycloakId, null, null);

        // 6. Return mapped response
        return toResponse(payout);
    }

    // ── Cancel Payout ──────────────────────────────────────────────────

    @Transactional
    public VendorPayoutResponse cancelPayout(String adminKeycloakId, UUID payoutId, String reason) {

        // 1. Find payout with pessimistic lock
        VendorPayout payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));

        // 2. Verify status
        if (payout.getStatus() != PENDING && payout.getStatus() != APPROVED) {
            throw new ValidationException("Payout must be in PENDING or APPROVED status to cancel");
        }

        // 3. Update fields
        String oldStatus = payout.getStatus().name();
        payout.setStatus(CANCELLED);
        payout.setAdminNote(reason);

        // 4. Save and write audit
        payout = payoutRepository.save(payout);

        writeAudit(null, null, payoutId,
                "PAYOUT_CANCELLED", oldStatus, "CANCELLED",
                "admin", adminKeycloakId, null, null);

        // 5. Return mapped response
        return toResponse(payout);
    }

    // ── List & Get Methods ─────────────────────────────────────────────

    public Page<VendorPayoutResponse> listPayouts(UUID vendorId, PayoutStatus status, Pageable pageable) {
        return payoutRepository.findFiltered(vendorId, status, pageable)
                .map(this::toResponse);
    }

    public VendorPayoutResponse getPayoutById(UUID payoutId) {
        VendorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));
        return toResponse(payout);
    }

    // ── Private Helpers ────────────────────────────────────────────────

    private VendorPayoutResponse toResponse(VendorPayout p) {
        return new VendorPayoutResponse(
                p.getId(),
                p.getVendorId(),
                p.getPayoutAmount(),
                p.getPlatformFee(),
                p.getCurrency(),
                p.getVendorOrderIds(),
                p.getBankNameSnapshot(),
                p.getAccountNumberSnapshot(),
                p.getAccountHolderSnapshot(),
                p.getStatus().name(),
                p.getReferenceNumber(),
                p.getApprovedBy(),
                p.getCompletedBy(),
                p.getAdminNote(),
                p.getApprovedAt(),
                p.getCompletedAt(),
                p.getCreatedAt()
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
