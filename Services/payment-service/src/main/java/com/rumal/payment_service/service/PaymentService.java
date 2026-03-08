package com.rumal.payment_service.service;

import com.rumal.payment_service.client.CustomerClient;
import com.rumal.payment_service.client.InventoryClient;
import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.config.PayHereProperties;
import com.rumal.payment_service.dto.CustomerAddressSummary;
import com.rumal.payment_service.dto.CustomerSummary;
import com.rumal.payment_service.dto.OrderSummary;
import com.rumal.payment_service.dto.PayHereCheckoutFormData;
import com.rumal.payment_service.dto.PaymentAuditResponse;
import com.rumal.payment_service.dto.PaymentDetailResponse;
import com.rumal.payment_service.dto.PaymentResponse;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.rumal.payment_service.entity.PaymentStatus.CANCELLED;
import static com.rumal.payment_service.entity.PaymentStatus.CHARGEBACKED;
import static com.rumal.payment_service.entity.PaymentStatus.EXPIRED;
import static com.rumal.payment_service.entity.PaymentStatus.FAILED;
import static com.rumal.payment_service.entity.PaymentStatus.INITIATED;
import static com.rumal.payment_service.entity.PaymentStatus.PENDING;
import static com.rumal.payment_service.entity.PaymentStatus.SUCCESS;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditRepository auditRepository;
    private final PaymentAuditService paymentAuditService;
    private final PayHereProperties payHereProperties;
    private final OrderClient orderClient;
    private final InventoryClient inventoryClient;
    private final CustomerClient customerClient;
    private final PayHereClient payHereClient;
    private final PaymentInitiationLockService paymentInitiationLockService;

    @Value("${payment.expiry.ttl:30m}")
    private Duration expiryTtl;

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PayHereCheckoutFormData initiatePayment(String keycloakId, UUID orderId) {
        PaymentInitiationLockService.LockHandle lockHandle = paymentInitiationLockService.acquire(orderId);
        try {
            OrderSummary order = orderClient.getOrder(orderId);

            CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
            if (!customer.id().equals(order.customerId())) {
                throw new com.rumal.payment_service.exception.UnauthorizedException(
                        "You are not authorized to pay for this order");
            }
            String customerEmail = requireNonBlank(
                    customer.email(),
                    "Customer email is required to initiate payment. Please update your profile and retry."
            );
            String customerPhone = trimToNull(customer.phone());

            if (!"PENDING".equals(order.status())
                    && !"PAYMENT_PENDING".equals(order.status())
                    && !"PAYMENT_FAILED".equals(order.status())) {
                throw new ValidationException("Order is not eligible for payment (status: " + order.status() + ")");
            }

            assertInventoryReservationReady(orderId);
            if ("PENDING".equals(order.status()) || "PAYMENT_FAILED".equals(order.status())) {
                orderClient.updateOrderStatus(orderId, "PAYMENT_PENDING", "Payment initiated by customer");
            }

            Optional<Payment> existing = paymentRepository.findByOrderIdAndStatusInForUpdate(
                    orderId, List.of(INITIATED, PENDING));

            Payment payment;

            if (existing.isPresent()) {
                Payment found = existing.get();
                if (found.getStatus() == INITIATED) {
                    found.setExpiresAt(Instant.now().plus(expiryTtl));
                    payment = paymentRepository.save(found);
                } else {
                    throw new ValidationException("Payment is already being processed");
                }
            } else {
                List<CustomerAddressSummary> addresses = customerClient.getCustomerAddresses(keycloakId);
                CustomerAddressSummary address = addresses.stream()
                        .filter(a -> a.defaultBilling() && !a.deleted())
                        .findFirst()
                        .orElseGet(() -> addresses.stream()
                                .filter(a -> !a.deleted())
                                .findFirst()
                                .orElse(null));

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

                String itemsDesc = order.item() != null
                        ? (order.item().length() > 500 ? order.item().substring(0, 500) : order.item())
                        : "";

                String customerAddress = null;
                String customerCity = null;
                String customerCountry = null;
                String fallbackAddressPhone = null;
                if (address != null) {
                    customerAddress = address.line1();
                    if (address.line2() != null && !address.line2().isBlank()) {
                        customerAddress += ", " + address.line2();
                    }
                    customerCity = address.city();
                    customerCountry = address.countryCode();
                    fallbackAddressPhone = trimToNull(address.phone());
                }

                String resolvedCustomerPhone = customerPhone != null ? customerPhone : fallbackAddressPhone;
                if (resolvedCustomerPhone == null) {
                    throw new ValidationException(
                            "Customer phone number is required to initiate payment. Add a phone in profile or address and retry."
                    );
                }

                String currency = order.currency() != null ? order.currency() : "LKR";

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
                        .customerEmail(customerEmail)
                        .customerPhone(resolvedCustomerPhone)
                        .customerAddress(customerAddress)
                        .customerCity(customerCity)
                        .customerCountry(customerCountry)
                        .expiresAt(Instant.now().plus(expiryTtl))
                        .build();

                payment = paymentRepository.save(payment);
                paymentAuditService.writeAudit(payment.getId(), null, null,
                        "PAYMENT_INITIATED", null, "INITIATED",
                        "customer", keycloakId, null, null);
            }

            String payhereOrderId = "ORD-" + payment.getId();
            String hash = PayHereHashUtil.generateCheckoutHash(
                    payHereProperties.getMerchantId(),
                    payhereOrderId,
                    payment.getAmount(),
                    payment.getCurrency(),
                    payHereProperties.getMerchantSecret());

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
        } finally {
            paymentInitiationLockService.release(lockHandle);
        }
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void processWebhook(String merchantId, String orderId, String paymentId,
                               String payhereAmount, String currency, int statusCode,
                               String md5sig, String method, String cardHolderName,
                               String cardNo, String cardExpiry, String rawPayload) {

        if (!payHereProperties.getMerchantId().equals(merchantId)) {
            log.warn("Webhook merchant_id mismatch: expected={}, received={}",
                    payHereProperties.getMerchantId(), merchantId);
            return;
        }

        boolean validSig = PayHereHashUtil.verifyWebhookSignature(
                merchantId, orderId, payhereAmount, currency, statusCode,
                payHereProperties.getMerchantSecret(), md5sig);
        if (!validSig) {
            log.error("Webhook md5sig verification failed for order_id={}", orderId);
            return;
        }

        UUID paymentUuid;
        try {
            String uuidStr = orderId.startsWith("ORD-") ? orderId.substring(4) : orderId;
            paymentUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ex) {
            log.error("Failed to parse payment UUID from order_id={}", orderId);
            return;
        }

        Optional<Payment> opt = paymentRepository.findByIdForUpdate(paymentUuid);
        if (opt.isEmpty()) {
            log.error("Payment not found for id={} (order_id={})", paymentUuid, orderId);
            return;
        }
        Payment payment = opt.get();

        PaymentStatus oldStatus = payment.getStatus();
        if (isTerminalStatus(oldStatus)) {
            log.info("Webhook for payment {} already in terminal state {}. Skipping.", paymentUuid, oldStatus);
            return;
        }

        try {
            BigDecimal receivedAmount = new BigDecimal(payhereAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expectedAmount = payment.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (receivedAmount.compareTo(expectedAmount) != 0) {
                log.error("PAYMENT AMOUNT MISMATCH for payment {}: expected={}, received={}",
                        paymentUuid, expectedAmount, receivedAmount);
                paymentAuditService.writeAudit(paymentUuid, null, null,
                        "AMOUNT_MISMATCH", oldStatus.name(), null,
                        "payhere", null, null,
                        "Expected: " + expectedAmount + ", Received: " + receivedAmount);
                return;
            }
        } catch (NumberFormatException ex) {
            log.error("Invalid payhere_amount format for payment {}: '{}'", paymentUuid, payhereAmount);
            return;
        }

        String webhookHash = computeWebhookHash(merchantId, orderId, payhereAmount, currency, statusCode);
        if (webhookHash.equals(payment.getWebhookIdempotencyHash())) {
            log.info("Duplicate webhook detected for payment {} (hash={}). Skipping.", paymentUuid, webhookHash);
            return;
        }
        payment.setWebhookIdempotencyHash(webhookHash);

        payment.setPayherePaymentId(paymentId);
        payment.setPayhereStatusCode(statusCode);
        payment.setPaymentMethod(method);
        payment.setCardHolderName(cardHolderName);
        payment.setCardNoMasked(cardNo);
        payment.setCardExpiry(cardExpiry);

        PaymentStatus newStatus = switch (statusCode) {
            case 2 -> SUCCESS;
            case 0 -> PENDING;
            case -1 -> CANCELLED;
            case -2 -> FAILED;
            case -3 -> CHARGEBACKED;
            default -> FAILED;
        };

        payment.setStatus(newStatus);

        if (requiresOrderSync(newStatus)) {
            markOrderSyncRequired(payment);
        } else {
            payment.setOrderSyncPending(false);
            payment.setOrderSyncRetryCount(0);
            payment.setOrderSyncFailed(false);
        }

        if (newStatus == SUCCESS) {
            payment.setPaidAt(Instant.now());
        }

        paymentRepository.save(payment);

        if (payment.isOrderSyncPending()) {
            try {
                synchronizeOrderState(payment);
                markOrderSyncCompleted(payment);
                paymentRepository.save(payment);
            } catch (Exception ex) {
                log.error("Failed to sync order {} after payment {}. Will retry via scheduler.",
                        payment.getOrderId(), newStatus, ex);
            }
        }

        paymentAuditService.writeAudit(paymentUuid, null, null,
                "WEBHOOK_RECEIVED", oldStatus.name(), newStatus.name(),
                "payhere", null, null, rawPayload);
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void reconcilePendingOrderSync(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        if (!payment.isOrderSyncPending()) {
            return;
        }

        try {
            synchronizeOrderState(payment);
            markOrderSyncCompleted(payment);
            paymentRepository.save(payment);
        } catch (Exception ex) {
            registerOrderSyncFailure(payment, ex);
            paymentRepository.save(payment);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId, String keycloakId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        assertPaymentOwnership(payment, keycloakId);
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(UUID orderId, String keycloakId) {
        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        assertPaymentOwnership(payment, keycloakId);
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(UUID orderId) {
        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        return toPaymentResponse(payment);
    }

    private void assertPaymentOwnership(Payment payment, String keycloakId) {
        if (!keycloakId.equals(payment.getCustomerKeycloakId())) {
            throw new ResourceNotFoundException("Payment not found");
        }
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

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PaymentDetailResponse verifyPaymentWithPayHere(UUID paymentId) {
        paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        Map<String, Object> response = payHereClient.retrievePayment("ORD-" + paymentId);
        log.info("PayHere verify for payment {} completed, status={}", paymentId,
                response != null ? response.get("status") : "null");

        paymentAuditService.writeAudit(paymentId, null, null,
                "ADMIN_VERIFY", null, null,
                "admin", null, null, null);

        return getPaymentDetail(paymentId);
    }

    @Transactional(readOnly = true)
    public Page<PaymentAuditResponse> getAuditTrail(UUID paymentId, Pageable pageable) {
        return auditRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId, pageable)
                .map(this::toAuditResponse);
    }

    private void assertInventoryReservationReady(UUID orderId) {
        boolean ready = inventoryClient.isReservationReadyForPayment(orderId);
        if (!ready) {
            throw new ValidationException(
                    "Order inventory reservation is not ready yet. Please retry in a few seconds."
            );
        }
    }

    private void synchronizeOrderState(Payment payment) {
        if (payment.getStatus() == SUCCESS) {
            orderClient.setPaymentInfo(
                    payment.getOrderId(),
                    payment.getId().toString(),
                    payment.getPaymentMethod(),
                    payment.getPayherePaymentId());
            orderClient.updateOrderStatus(
                    payment.getOrderId(), "CONFIRMED", "Payment confirmed via PayHere");
            return;
        }

        if (payment.getStatus() == FAILED || payment.getStatus() == CANCELLED || payment.getStatus() == EXPIRED) {
            orderClient.updateOrderStatus(
                    payment.getOrderId(),
                    "PAYMENT_FAILED",
                    buildFailureReason(payment.getStatus()));
            return;
        }

        throw new ValidationException("Payment status does not require downstream synchronization: " + payment.getStatus());
    }

    private String buildFailureReason(PaymentStatus status) {
        if (status == null) {
            return "Payment failed";
        }
        return switch (status) {
            case EXPIRED -> "Payment expired";
            case CANCELLED -> "Payment cancelled";
            case FAILED -> "Payment failed";
            default -> "Payment " + status.name().toLowerCase();
        };
    }

    private boolean requiresOrderSync(PaymentStatus status) {
        return status == SUCCESS || status == FAILED || status == CANCELLED || status == EXPIRED;
    }

    private boolean isTerminalStatus(PaymentStatus status) {
        return status == SUCCESS
                || status == FAILED
                || status == CANCELLED
                || status == CHARGEBACKED
                || status == EXPIRED;
    }

    private void markOrderSyncRequired(Payment payment) {
        payment.setOrderSyncPending(true);
        payment.setOrderSyncRetryCount(0);
        payment.setOrderSyncFailed(false);
    }

    private void markOrderSyncCompleted(Payment payment) {
        payment.setOrderSyncPending(false);
        payment.setOrderSyncRetryCount(0);
        payment.setOrderSyncFailed(false);
    }

    private void registerOrderSyncFailure(Payment payment, Exception ex) {
        int nextRetryCount = payment.getOrderSyncRetryCount() + 1;
        payment.setOrderSyncRetryCount(nextRetryCount);
        if (nextRetryCount >= payment.getOrderSyncMaxRetries()) {
            payment.setOrderSyncPending(false);
            payment.setOrderSyncFailed(true);
            log.error("CRITICAL: Order sync permanently failed for payment {} (order {}) after {} retries.",
                    payment.getId(), payment.getOrderId(), nextRetryCount, ex);
            paymentAuditService.writeAudit(payment.getId(), null, null,
                    "ORDER_SYNC_PERMANENT_FAILURE", payment.getStatus().name(), null,
                    "system", null, null,
                    "Sync abandoned after " + nextRetryCount + " retries for order " + payment.getOrderId());
            return;
        }
        payment.setOrderSyncFailed(false);
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getPaymentMethod(),
                payment.getCardNoMasked(),
                payment.getPayherePaymentId(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }

    private PaymentDetailResponse toPaymentDetailResponse(Payment payment) {
        return new PaymentDetailResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getCustomerKeycloakId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getPayhereStatusCode(),
                payment.getStatusMessage(),
                payment.getPayherePaymentId(),
                payment.getPaymentMethod(),
                payment.getCardHolderName(),
                payment.getCardNoMasked(),
                payment.getCardExpiry(),
                payment.getItemsDescription(),
                payment.getPaidAt(),
                payment.getExpiresAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    private String computeWebhookHash(String merchantId, String orderId,
                                      String amount, String currency, int statusCode) {
        try {
            String data = merchantId + "|" + orderId + "|" + amount + "|" + currency + "|" + statusCode;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private PaymentAuditResponse toAuditResponse(PaymentAudit audit) {
        return new PaymentAuditResponse(
                audit.getId(),
                audit.getPaymentId(),
                audit.getRefundRequestId(),
                audit.getPayoutId(),
                audit.getEventType(),
                audit.getFromStatus(),
                audit.getToStatus(),
                audit.getActorType(),
                audit.getActorId(),
                audit.getNote(),
                audit.getCreatedAt()
        );
    }
}
