package com.rumal.payment_service.service;

import com.rumal.payment_service.client.CustomerClient;
import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.config.PayHereProperties;
import com.rumal.payment_service.dto.*;
import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentAudit;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.ValidationException;
import com.rumal.payment_service.repo.PaymentAuditRepository;
import com.rumal.payment_service.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.rumal.payment_service.entity.PaymentStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditRepository auditRepository;
    private final PaymentAuditService paymentAuditService;
    private final PayHereProperties payHereProperties;
    private final OrderClient orderClient;
    private final CustomerClient customerClient;
    private final PayHereClient payHereClient;

    @Value("${payment.expiry.ttl:30m}")
    private Duration expiryTtl;

    // ── Payment Initiation ─────────────────────────────────────────────

    @Transactional
    public PayHereCheckoutFormData initiatePayment(String keycloakId, UUID orderId) {

        // 1. Fetch order
        OrderSummary order = orderClient.getOrder(orderId);

        // 2. Verify requesting customer owns this order
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        if (!customer.id().equals(order.customerId())) {
            throw new com.rumal.payment_service.exception.UnauthorizedException(
                    "You are not authorized to pay for this order");
        }

        // 3. Validate order status and transition PENDING → PAYMENT_PENDING
        if ("PENDING".equals(order.status())) {
            orderClient.updateOrderStatus(orderId, "PAYMENT_PENDING", "Payment initiated by customer");
        } else if (!"PAYMENT_PENDING".equals(order.status())) {
            throw new ValidationException("Order is not eligible for payment (status: " + order.status() + ")");
        }

        // 4. Check for existing payment (with lock to prevent duplicate creation)
        Optional<Payment> existing = paymentRepository.findByOrderIdAndStatusInForUpdate(
                orderId, List.of(INITIATED, PENDING));

        Payment payment;

        if (existing.isPresent()) {
            Payment found = existing.get();
            if (found.getStatus() == INITIATED) {
                // Reuse initiated payment – refresh expiry
                found.setExpiresAt(Instant.now().plus(expiryTtl));
                payment = paymentRepository.save(found);
            } else {
                // PENDING – payment is actively being processed
                throw new ValidationException("Payment is already being processed");
            }
        } else {
            // 5. Create new payment
            List<CustomerAddressSummary> addresses = customerClient.getCustomerAddresses(keycloakId);

            // Find default billing address, fallback to first non-deleted
            CustomerAddressSummary address = addresses.stream()
                    .filter(a -> a.defaultBilling() && !a.deleted())
                    .findFirst()
                    .orElseGet(() -> addresses.stream()
                            .filter(a -> !a.deleted())
                            .findFirst()
                            .orElse(null));

            // Split customer name
            String fullName = customer.name() != null ? customer.name().trim() : "";
            String firstName;
            String lastName;
            int spaceIdx = fullName.indexOf(' ');
            if (spaceIdx > 0) {
                firstName = fullName.substring(0, spaceIdx);
                lastName = fullName.substring(spaceIdx + 1).trim();
            } else {
                firstName = fullName;
                lastName = "";
            }

            // Truncate items description to 500 chars
            String itemsDesc = order.item() != null
                    ? (order.item().length() > 500 ? order.item().substring(0, 500) : order.item())
                    : "";

            // Build address fields
            String customerAddress = null;
            String customerCity = null;
            String customerCountry = null;
            if (address != null) {
                customerAddress = address.line1();
                if (address.line2() != null && !address.line2().isBlank()) {
                    customerAddress += ", " + address.line2();
                }
                customerCity = address.city();
                customerCountry = address.countryCode();
            }

            String currency = order.currency() != null ? order.currency() : "USD";

            payment = Payment.builder()
                    .orderId(orderId)
                    .customerId(order.customerId())
                    .customerKeycloakId(keycloakId)
                    .amount(order.orderTotal())
                    .currency(currency)
                    .status(INITIATED)
                    .itemsDescription(itemsDesc)
                    .customerFirstName(firstName)
                    .customerLastName(lastName)
                    .customerEmail(customer.email())
                    .customerPhone(customer.phone())
                    .customerAddress(customerAddress)
                    .customerCity(customerCity)
                    .customerCountry(customerCountry)
                    .expiresAt(Instant.now().plus(expiryTtl))
                    .build();

            payment = paymentRepository.save(payment);

            // Audit
            paymentAuditService.writeAudit(payment.getId(), null, null,
                    "PAYMENT_INITIATED", null, "INITIATED",
                    "customer", keycloakId, null, null);
        }

        // 6. Generate PayHere order ID
        String payhereOrderId = "ORD-" + payment.getId();

        // 7. Generate hash
        String hash = PayHereHashUtil.generateCheckoutHash(
                payHereProperties.getMerchantId(),
                payhereOrderId,
                payment.getAmount(),
                payment.getCurrency(),
                payHereProperties.getMerchantSecret());

        // 8. Build and return checkout form data
        String formattedAmount = payment.getAmount()
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();

        return new PayHereCheckoutFormData(
                payment.getId(),
                payHereProperties.getMerchantId(),
                payHereProperties.getReturnUrl(),
                payHereProperties.getCancelUrl(),
                payHereProperties.getNotifyUrl(),
                payhereOrderId,
                payment.getItemsDescription(),
                payment.getCurrency(),
                formattedAmount,
                hash,
                payment.getCustomerFirstName(),
                payment.getCustomerLastName(),
                payment.getCustomerEmail(),
                payment.getCustomerPhone(),
                payment.getCustomerAddress(),
                payment.getCustomerCity(),
                payment.getCustomerCountry(),
                payHereProperties.getCheckoutUrl(),
                payment.getOrderId().toString(),
                ""
        );
    }

    // ── Webhook Processing ─────────────────────────────────────────────

    @Transactional
    public void processWebhook(String merchantId, String orderId, String paymentId,
                               String payhereAmount, String currency, int statusCode,
                               String md5sig, String method, String cardHolderName,
                               String cardNo, String cardExpiry, String rawPayload) {

        // 1. Verify merchant ID
        if (!payHereProperties.getMerchantId().equals(merchantId)) {
            log.warn("Webhook merchant_id mismatch: expected={}, received={}",
                    payHereProperties.getMerchantId(), merchantId);
            return;
        }

        // 2. Verify signature
        boolean validSig = PayHereHashUtil.verifyWebhookSignature(
                merchantId, orderId, payhereAmount, currency, statusCode,
                payHereProperties.getMerchantSecret(), md5sig);
        if (!validSig) {
            log.error("Webhook md5sig verification failed for order_id={}", orderId);
            return;
        }

        // 3. Parse payment UUID from orderId
        UUID paymentUuid;
        try {
            String uuidStr = orderId.startsWith("ORD-") ? orderId.substring(4) : orderId;
            paymentUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ex) {
            log.error("Failed to parse payment UUID from order_id={}", orderId);
            return;
        }

        // 4. Find payment with pessimistic lock
        Optional<Payment> opt = paymentRepository.findByIdForUpdate(paymentUuid);
        if (opt.isEmpty()) {
            log.error("Payment not found for id={} (order_id={})", paymentUuid, orderId);
            return;
        }
        Payment payment = opt.get();

        // 5. Store old status – skip if already terminal (idempotency)
        PaymentStatus oldStatus = payment.getStatus();
        if (oldStatus == SUCCESS || oldStatus == FAILED || oldStatus == CANCELLED || oldStatus == CHARGEBACKED) {
            log.info("Webhook for payment {} already in terminal state {}. Skipping.", paymentUuid, oldStatus);
            return;
        }

        // 6. Set webhook fields
        payment.setPayherePaymentId(paymentId);
        payment.setPayhereStatusCode(statusCode);
        payment.setPaymentMethod(method);
        payment.setCardHolderName(cardHolderName);
        payment.setCardNoMasked(cardNo);
        payment.setCardExpiry(cardExpiry);

        // 7. Map status code
        PaymentStatus newStatus = switch (statusCode) {
            case 2 -> SUCCESS;
            case 0 -> PENDING;
            case -1 -> CANCELLED;
            case -2 -> FAILED;
            case -3 -> CHARGEBACKED;
            default -> FAILED;
        };

        // 8. Update status
        payment.setStatus(newStatus);

        // 9. Mark order sync pending for statuses that require order updates
        if (newStatus == SUCCESS || newStatus == FAILED || newStatus == CANCELLED) {
            payment.setOrderSyncPending(true);
        }

        if (newStatus == SUCCESS) {
            payment.setPaidAt(Instant.now());
        }

        // 10. Save payment first to persist the status change
        paymentRepository.save(payment);

        // 11. Sync order status (best-effort, flag stays true on failure for scheduler retry)
        if (newStatus == SUCCESS) {
            try {
                orderClient.setPaymentInfo(
                        payment.getOrderId(),
                        payment.getId().toString(),
                        method,
                        paymentId);
                orderClient.updateOrderStatus(
                        payment.getOrderId(), "CONFIRMED", "Payment confirmed via PayHere");
                payment.setOrderSyncPending(false);
                paymentRepository.save(payment);
            } catch (Exception ex) {
                log.error("Failed to sync order {} after successful payment. Will retry via scheduler.",
                        payment.getOrderId(), ex);
            }
        }

        if (newStatus == FAILED || newStatus == CANCELLED) {
            try {
                orderClient.updateOrderStatus(
                        payment.getOrderId(), "PAYMENT_FAILED",
                        "Payment " + newStatus.name().toLowerCase());
                payment.setOrderSyncPending(false);
                paymentRepository.save(payment);
            } catch (Exception ex) {
                log.error("Failed to sync order {} after payment {}. Will retry via scheduler.",
                        payment.getOrderId(), newStatus, ex);
            }
        }

        // 12. Audit
        paymentAuditService.writeAudit(paymentUuid, null, null,
                "WEBHOOK_RECEIVED", oldStatus.name(), newStatus.name(),
                "payhere", null, null, rawPayload);
    }

    // ── Query Methods ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(UUID orderId) {
        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getPaymentDetail(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        return toPaymentDetailResponse(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPaymentsForCustomer(String keycloakId, Pageable pageable) {
        return paymentRepository.findByCustomerKeycloakId(keycloakId, pageable)
                .map(this::toPaymentResponse);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listAllPayments(UUID customerId, PaymentStatus status, Pageable pageable) {
        return paymentRepository.findFiltered(customerId, status, pageable)
                .map(this::toPaymentResponse);
    }

    // ── Admin Verify ───────────────────────────────────────────────────

    @Transactional
    public PaymentDetailResponse verifyPaymentWithPayHere(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        Map<String, Object> response = payHereClient.retrievePayment("ORD-" + paymentId);
        log.info("PayHere verify response for payment {}: {}", paymentId, response);

        paymentAuditService.writeAudit(paymentId, null, null,
                "ADMIN_VERIFY", null, null,
                "admin", null, null, null);

        return getPaymentDetail(paymentId);
    }

    // ── Audit Trail ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentAuditResponse> getAuditTrail(UUID paymentId) {
        return auditRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId)
                .stream()
                .map(this::toAuditResponse)
                .toList();
    }

    // ── Private Helpers ────────────────────────────────────────────────

    private PaymentResponse toPaymentResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getOrderId(),
                p.getCustomerId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getPaymentMethod(),
                p.getCardNoMasked(),
                p.getPayherePaymentId(),
                p.getPaidAt(),
                p.getCreatedAt()
        );
    }

    private PaymentDetailResponse toPaymentDetailResponse(Payment p) {
        return new PaymentDetailResponse(
                p.getId(),
                p.getOrderId(),
                p.getCustomerId(),
                p.getCustomerKeycloakId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getPayhereStatusCode(),
                p.getStatusMessage(),
                p.getPayherePaymentId(),
                p.getPaymentMethod(),
                p.getCardHolderName(),
                p.getCardNoMasked(),
                p.getCardExpiry(),
                p.getItemsDescription(),
                p.getPaidAt(),
                p.getExpiresAt(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private PaymentAuditResponse toAuditResponse(PaymentAudit a) {
        return new PaymentAuditResponse(
                a.getId(),
                a.getPaymentId(),
                a.getRefundRequestId(),
                a.getPayoutId(),
                a.getEventType(),
                a.getFromStatus(),
                a.getToStatus(),
                a.getActorType(),
                a.getActorId(),
                a.getNote(),
                a.getCreatedAt()
        );
    }

}
