package com.rumal.order_service.config;

import com.rumal.order_service.entity.*;
import com.rumal.order_service.repo.OrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleOrderDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleOrderDataSeeder.class);

    // Vendor IDs (consistent with vendor-service seeder)
    private static final UUID NOVA_TECH = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID URBAN_STYLE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOME_CRAFT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    // Customer IDs (consistent with customer-service seeder)
    private static final UUID ALICE = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("aaaa2222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("aaaa3333-3333-3333-3333-333333333333");
    private static final UUID DAVE = UUID.fromString("aaaa4444-4444-4444-4444-444444444444");

    // Address IDs (consistent with customer-service seeder)
    private static final UUID ALICE_ADDR = UUID.fromString("bbbb1111-1111-1111-1111-111111111111");
    private static final UUID BOB_ADDR = UUID.fromString("bbbb2222-1111-1111-1111-111111111111");
    private static final UUID CAROL_ADDR = UUID.fromString("bbbb3333-1111-1111-1111-111111111111");
    private static final UUID DAVE_ADDR = UUID.fromString("bbbb4444-1111-1111-1111-111111111111");

    // Sample product IDs (for reference only — not linked to product-service auto-generated IDs)
    private static final UUID P_EARBUDS = UUID.fromString("00ab0001-0001-0001-0001-000000000001");
    private static final UUID P_CHARGER = UUID.fromString("00ab0001-0001-0001-0001-000000000002");
    private static final UUID P_SNEAKERS = UUID.fromString("00ab0001-0001-0001-0001-000000000003");
    private static final UUID P_LAMP = UUID.fromString("00ab0001-0001-0001-0001-000000000004");
    private static final UUID P_PAN_SET = UUID.fromString("00ab0001-0001-0001-0001-000000000005");
    private static final UUID P_TSHIRT = UUID.fromString("00ab0001-0001-0001-0001-000000000006");
    private static final UUID P_PROTECTOR = UUID.fromString("00ab0001-0001-0001-0001-000000000007");
    private static final UUID P_SPEAKER = UUID.fromString("00ab0001-0001-0001-0001-000000000008");
    private static final UUID P_CASE = UUID.fromString("00ab0001-0001-0001-0001-000000000009");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal SHIPPING = bd("5.00");

    private final OrderRepository orderRepository;
    private final EntityManager em;

    @Value("${sample.order.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample order seed (sample.order.seed.enabled=false)");
            return;
        }
        if (orderRepository.count() > 0) {
            log.info("Skipping sample order seed because orders already exist.");
            return;
        }

        log.info("Seeding sample orders...");
        Instant now = Instant.now();

        OrderAddressSnapshot aliceAddr = addr("Home", "Alice Johnson", "+1-555-1001",
                "123 Maple Street", "Apt 4B", "New York", "NY", "10001", "US");
        OrderAddressSnapshot bobAddr = addr("Home", "Bob Smith", "+1-555-2001",
                "789 Oak Avenue", null, "Los Angeles", "CA", "90001", "US");
        OrderAddressSnapshot carolAddr = addr("Home", "Carol Rivera", "+1-555-3001",
                "321 Pine Road", "Unit 12", "Chicago", "IL", "60601", "US");
        OrderAddressSnapshot daveAddr = addr("Home", "Dave Park", "+1-555-4001",
                "654 Elm Street", null, "Houston", "TX", "77001", "US");

        // ── 1. DELIVERED — Alice (multi-vendor: Nova + Urban) ──────────────
        Order o1 = persistOrder(oid(1), ALICE, ALICE_ADDR, aliceAddr, OrderStatus.DELIVERED,
                "EchoBuds Wireless Earbuds + 1 more", 2, 2,
                bd("158.00"), bd("163.00"),
                "PAY-0001", "CARD", "stripe_ch_0001", now.minus(10, ChronoUnit.DAYS));
        VendorOrder vo1 = persistVendorOrder(vid(1), o1, NOVA_TECH, OrderStatus.DELIVERED,
                1, 1, bd("61.50"), bd("2.50"), bd("5.90"), bd("55.60"));
        setTracking(vo1, "TRK-NOVA-0001", "https://track.example/TRK-NOVA-0001", "FEDEX",
                LocalDate.now().minusDays(5));
        persistItem(iid(1), o1, vo1, P_EARBUDS, NOVA_TECH, "ELEC-EBUDS-001",
                "EchoBuds Wireless Earbuds", 1, bd("59.00"));

        VendorOrder vo2 = persistVendorOrder(vid(2), o1, URBAN_STYLE, OrderStatus.DELIVERED,
                1, 1, bd("101.50"), bd("2.50"), bd("9.90"), bd("91.60"));
        setTracking(vo2, "TRK-URBAN-0001", "https://track.example/TRK-URBAN-0001", "UPS",
                LocalDate.now().minusDays(6));
        persistItem(iid(2), o1, vo2, P_SNEAKERS, URBAN_STYLE, "FASH-SNK-BLK-42",
                "Urban Pulse Sneakers - Black / 42", 1, bd("99.00"));

        // ── 2. SHIPPED — Alice (Nova, with tracking) ──────────────────────
        Order o2 = persistOrder(oid(2), ALICE, ALICE_ADDR, aliceAddr, OrderStatus.SHIPPED,
                "NovaCharge 65W Fast Charger + 1 more", 2, 2,
                bd("98.00"), bd("103.00"),
                "PAY-0002", "CARD", "stripe_ch_0002", now.minus(5, ChronoUnit.DAYS));
        VendorOrder vo3 = persistVendorOrder(vid(3), o2, NOVA_TECH, OrderStatus.SHIPPED,
                2, 2, bd("103.00"), SHIPPING, bd("9.80"), bd("93.20"));
        setTracking(vo3, "TRK-NOVA-0002", "https://track.example/TRK-NOVA-0002", "FEDEX",
                LocalDate.now().plusDays(2));
        persistItem(iid(3), o2, vo3, P_CHARGER, NOVA_TECH, "ELEC-CHARGER-001",
                "NovaCharge 65W Fast Charger", 1, bd("29.00"));
        persistItem(iid(4), o2, vo3, P_SPEAKER, NOVA_TECH, "ELEC-SPEAKER-001",
                "AeroSound Bluetooth Speaker", 1, bd("69.00"));

        // ── 3. PENDING — Bob (Urban, awaiting payment) ────────────────────
        Order o3 = persistOrder(oid(3), BOB, BOB_ADDR, bobAddr, OrderStatus.PENDING,
                "FitStride Training T-Shirt", 1, 2,
                bd("38.00"), bd("43.00"),
                null, null, null, null);
        o3.setExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        VendorOrder vo4 = persistVendorOrder(vid(4), o3, URBAN_STYLE, OrderStatus.PENDING,
                1, 2, bd("43.00"), SHIPPING, bd("3.80"), bd("39.20"));
        persistItem(iid(5), o3, vo4, P_TSHIRT, URBAN_STYLE, "FASH-TEE-001",
                "FitStride Training T-Shirt", 2, bd("19.00"));

        // ── 4. CONFIRMED — Bob (Home Craft, payment done) ────────────────
        Order o4 = persistOrder(oid(4), BOB, BOB_ADDR, bobAddr, OrderStatus.CONFIRMED,
                "AetherSmart LED Desk Lamp", 1, 1,
                bd("39.00"), bd("44.00"),
                "PAY-0004", "CARD", "stripe_ch_0004", now.minus(2, ChronoUnit.DAYS));
        VendorOrder vo5 = persistVendorOrder(vid(5), o4, HOME_CRAFT, OrderStatus.CONFIRMED,
                1, 1, bd("44.00"), SHIPPING, bd("3.90"), bd("40.10"));
        persistItem(iid(6), o4, vo5, P_LAMP, HOME_CRAFT, "HOME-LAMP-001",
                "AetherSmart LED Desk Lamp", 1, bd("39.00"));

        // ── 5. PROCESSING — Carol (Home Craft, being packed) ─────────────
        Order o5 = persistOrder(oid(5), CAROL, CAROL_ADDR, carolAddr, OrderStatus.PROCESSING,
                "HomeChef Non-Stick Pan Set", 1, 1,
                bd("79.00"), bd("84.00"),
                "PAY-0005", "CARD", "stripe_ch_0005", now.minus(1, ChronoUnit.DAYS));
        VendorOrder vo6 = persistVendorOrder(vid(6), o5, HOME_CRAFT, OrderStatus.PROCESSING,
                1, 1, bd("84.00"), SHIPPING, bd("7.90"), bd("76.10"));
        persistItem(iid(7), o5, vo6, P_PAN_SET, HOME_CRAFT, "HOME-PAN-001",
                "HomeChef Non-Stick Pan Set", 1, bd("79.00"));

        // ── 6. CANCELLED — Carol (Nova, cancelled before shipment) ───────
        Order o6 = persistOrder(oid(6), CAROL, CAROL_ADDR, carolAddr, OrderStatus.CANCELLED,
                "PulseGuard Screen Protector", 1, 1,
                bd("14.00"), bd("19.00"),
                "PAY-0006", "CARD", "stripe_ch_0006", now.minus(7, ChronoUnit.DAYS));
        VendorOrder vo7 = persistVendorOrder(vid(7), o6, NOVA_TECH, OrderStatus.CANCELLED,
                1, 1, bd("19.00"), SHIPPING, bd("1.40"), bd("17.60"));
        persistItem(iid(8), o6, vo7, P_PROTECTOR, NOVA_TECH, "ELEC-PROTECT-001",
                "PulseGuard Screen Protector", 1, bd("14.00"));

        // ── 7. PAYMENT_FAILED — Dave (Nova) ──────────────────────────────
        Order o7 = persistOrder(oid(7), DAVE, DAVE_ADDR, daveAddr, OrderStatus.PAYMENT_FAILED,
                "NovaShield Phone Case", 1, 1,
                bd("19.00"), bd("24.00"),
                null, null, null, null);
        VendorOrder vo8 = persistVendorOrder(vid(8), o7, NOVA_TECH, OrderStatus.PAYMENT_FAILED,
                1, 1, bd("24.00"), SHIPPING, bd("1.90"), bd("22.10"));
        persistItem(iid(9), o7, vo8, P_CASE, NOVA_TECH, "ELEC-CASE-IP15-BLK",
                "NovaShield Phone Case - iPhone 15 / Black", 1, bd("19.00"));

        // ── 8. PAYMENT_PENDING — Alice (Nova, payment initiated) ─────────
        Order o8 = persistOrder(oid(8), ALICE, ALICE_ADDR, aliceAddr, OrderStatus.PAYMENT_PENDING,
                "EchoBuds Wireless Earbuds", 1, 1,
                bd("59.00"), bd("64.00"),
                null, "CARD", null, null);
        o8.setExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        VendorOrder vo9 = persistVendorOrder(vid(9), o8, NOVA_TECH, OrderStatus.PAYMENT_PENDING,
                1, 1, bd("64.00"), SHIPPING, bd("5.90"), bd("58.10"));
        persistItem(iid(10), o8, vo9, P_EARBUDS, NOVA_TECH, "ELEC-EBUDS-001",
                "EchoBuds Wireless Earbuds", 1, bd("59.00"));

        // ── 9. ON_HOLD — Bob (Nova, flagged for review) ──────────────────
        Order o9 = persistOrder(oid(9), BOB, BOB_ADDR, bobAddr, OrderStatus.ON_HOLD,
                "AeroSound Bluetooth Speaker", 1, 1,
                bd("69.00"), bd("74.00"),
                "PAY-0009", "CARD", "stripe_ch_0009", now.minus(3, ChronoUnit.DAYS));
        o9.setAdminNote("Flagged for review — possible duplicate order.");
        VendorOrder vo10 = persistVendorOrder(vid(10), o9, NOVA_TECH, OrderStatus.ON_HOLD,
                1, 1, bd("74.00"), SHIPPING, bd("6.90"), bd("67.10"));
        persistItem(iid(11), o9, vo10, P_SPEAKER, NOVA_TECH, "ELEC-SPEAKER-001",
                "AeroSound Bluetooth Speaker", 1, bd("69.00"));

        // ── 10. RETURN_REQUESTED — Carol (Nova, customer wants return) ───
        Order o10 = persistOrder(oid(10), CAROL, CAROL_ADDR, carolAddr, OrderStatus.RETURN_REQUESTED,
                "NovaCharge 65W Fast Charger", 1, 1,
                bd("29.00"), bd("34.00"),
                "PAY-0010", "CARD", "stripe_ch_0010", now.minus(15, ChronoUnit.DAYS));
        VendorOrder vo11 = persistVendorOrder(vid(11), o10, NOVA_TECH, OrderStatus.RETURN_REQUESTED,
                1, 1, bd("34.00"), SHIPPING, bd("2.90"), bd("31.10"));
        persistItem(iid(12), o10, vo11, P_CHARGER, NOVA_TECH, "ELEC-CHARGER-001",
                "NovaCharge 65W Fast Charger", 1, bd("29.00"));

        // ── 11. RETURN_REJECTED — Dave (Home Craft, return denied) ───────
        Order o11 = persistOrder(oid(11), DAVE, DAVE_ADDR, daveAddr, OrderStatus.RETURN_REJECTED,
                "AetherSmart LED Desk Lamp", 1, 1,
                bd("39.00"), bd("44.00"),
                "PAY-0011", "CARD", "stripe_ch_0011", now.minus(20, ChronoUnit.DAYS));
        VendorOrder vo12 = persistVendorOrder(vid(12), o11, HOME_CRAFT, OrderStatus.RETURN_REJECTED,
                1, 1, bd("44.00"), SHIPPING, bd("3.90"), bd("40.10"));
        persistItem(iid(13), o11, vo12, P_LAMP, HOME_CRAFT, "HOME-LAMP-001",
                "AetherSmart LED Desk Lamp", 1, bd("39.00"));

        // ── 12. REFUND_PENDING — Alice (Urban, refund in progress) ───────
        Order o12 = persistOrder(oid(12), ALICE, ALICE_ADDR, aliceAddr, OrderStatus.REFUND_PENDING,
                "FitStride Training T-Shirt", 1, 1,
                bd("19.00"), bd("24.00"),
                "PAY-0012", "CARD", "stripe_ch_0012", now.minus(12, ChronoUnit.DAYS));
        o12.setRefundAmount(bd("24.00"));
        o12.setRefundReason("Product arrived damaged.");
        o12.setRefundInitiatedAt(now.minus(1, ChronoUnit.DAYS));

        VendorOrder vo13 = persistVendorOrder(vid(13), o12, URBAN_STYLE, OrderStatus.REFUND_PENDING,
                1, 1, bd("24.00"), SHIPPING, bd("1.90"), bd("22.10"));
        vo13.setRefundAmount(bd("24.00"));
        vo13.setRefundReason("Product arrived damaged.");
        vo13.setRefundInitiatedAt(now.minus(1, ChronoUnit.DAYS));
        persistItem(iid(14), o12, vo13, P_TSHIRT, URBAN_STYLE, "FASH-TEE-001",
                "FitStride Training T-Shirt", 1, bd("19.00"));

        // ── 13. REFUNDED — Bob (Home Craft, refund complete) ─────────────
        Order o13 = persistOrder(oid(13), BOB, BOB_ADDR, bobAddr, OrderStatus.REFUNDED,
                "HomeChef Non-Stick Pan Set", 1, 1,
                bd("79.00"), bd("84.00"),
                "PAY-0013", "CARD", "stripe_ch_0013", now.minus(25, ChronoUnit.DAYS));
        o13.setRefundAmount(bd("84.00"));
        o13.setRefundReason("Customer requested return — item defective.");
        o13.setRefundInitiatedAt(now.minus(5, ChronoUnit.DAYS));
        o13.setRefundCompletedAt(now.minus(3, ChronoUnit.DAYS));

        VendorOrder vo14 = persistVendorOrder(vid(14), o13, HOME_CRAFT, OrderStatus.REFUNDED,
                1, 1, bd("84.00"), SHIPPING, bd("7.90"), bd("76.10"));
        vo14.setRefundAmount(bd("84.00"));
        vo14.setRefundedAmount(bd("84.00"));
        vo14.setRefundedQuantity(1);
        vo14.setRefundReason("Customer requested return — item defective.");
        vo14.setRefundInitiatedAt(now.minus(5, ChronoUnit.DAYS));
        vo14.setRefundCompletedAt(now.minus(3, ChronoUnit.DAYS));
        persistItem(iid(15), o13, vo14, P_PAN_SET, HOME_CRAFT, "HOME-PAN-001",
                "HomeChef Non-Stick Pan Set", 1, bd("79.00"));

        // ── 14. CLOSED — Carol (Nova, fully completed and closed) ────────
        Order o14 = persistOrder(oid(14), CAROL, CAROL_ADDR, carolAddr, OrderStatus.CLOSED,
                "PulseGuard Screen Protector + 1 more", 2, 2,
                bd("73.00"), bd("78.00"),
                "PAY-0014", "CARD", "stripe_ch_0014", now.minus(30, ChronoUnit.DAYS));
        VendorOrder vo15 = persistVendorOrder(vid(15), o14, NOVA_TECH, OrderStatus.CLOSED,
                2, 2, bd("78.00"), SHIPPING, bd("7.30"), bd("70.70"));
        setTracking(vo15, "TRK-NOVA-0014", "https://track.example/TRK-NOVA-0014", "FEDEX",
                LocalDate.now().minusDays(25));
        persistItem(iid(16), o14, vo15, P_PROTECTOR, NOVA_TECH, "ELEC-PROTECT-001",
                "PulseGuard Screen Protector", 1, bd("14.00"));
        persistItem(iid(17), o14, vo15, P_EARBUDS, NOVA_TECH, "ELEC-EBUDS-001",
                "EchoBuds Wireless Earbuds", 1, bd("59.00"));

        log.info("Sample order seed completed: orders={}", orderRepository.count());
    }

    // ─── UUID generators ─────────────────────────────────────────────────

    private static UUID oid(int n) {
        return UUID.fromString(String.format("eeee%04d-0000-0000-0000-%012d", n, n));
    }

    private static UUID vid(int n) {
        return UUID.fromString(String.format("eeff%04d-0000-0000-0000-%012d", n, n));
    }

    private static UUID iid(int n) {
        return UUID.fromString(String.format("eef0%04d-0000-0000-0000-%012d", n, n));
    }

    // ─── Persist helpers ─────────────────────────────────────────────────

    private Order persistOrder(UUID id, UUID customerId, UUID addressId,
                               OrderAddressSnapshot address, OrderStatus status,
                               String itemSummary, int itemCount, int quantity,
                               BigDecimal subtotal, BigDecimal orderTotal,
                               String paymentId, String paymentMethod,
                               String paymentGatewayRef, Instant paidAt) {
        Order o = Order.builder()
                .id(id)
                .version(0L)
                .customerId(customerId)
                .shippingAddressId(addressId)
                .billingAddressId(addressId)
                .shippingAddress(address)
                .billingAddress(address)
                .status(status)
                .item(itemSummary)
                .itemCount(itemCount)
                .quantity(quantity)
                .subtotal(subtotal)
                .lineDiscountTotal(ZERO)
                .cartDiscountTotal(ZERO)
                .shippingAmount(SHIPPING)
                .shippingDiscountTotal(ZERO)
                .totalDiscount(ZERO)
                .orderTotal(orderTotal)
                .currency("USD")
                .paymentId(paymentId)
                .paymentMethod(paymentMethod)
                .paymentGatewayRef(paymentGatewayRef)
                .paidAt(paidAt)
                .build();
        return em.merge(o);
    }

    private VendorOrder persistVendorOrder(UUID id, Order order, UUID vendorId,
                                           OrderStatus status, int itemCount, int quantity,
                                           BigDecimal orderTotal, BigDecimal shippingAmount,
                                           BigDecimal platformFee, BigDecimal payoutAmount) {
        VendorOrder vo = VendorOrder.builder()
                .id(id)
                .version(0L)
                .order(order)
                .vendorId(vendorId)
                .status(status)
                .itemCount(itemCount)
                .quantity(quantity)
                .orderTotal(orderTotal)
                .currency("USD")
                .discountAmount(ZERO)
                .shippingAmount(shippingAmount)
                .platformFee(platformFee)
                .payoutAmount(payoutAmount)
                .build();
        return em.merge(vo);
    }

    private void persistItem(UUID id, Order order, VendorOrder vendorOrder,
                             UUID productId, UUID vendorId, String sku, String name,
                             int quantity, BigDecimal unitPrice) {
        em.merge(OrderItem.builder()
                .id(id)
                .order(order)
                .vendorOrder(vendorOrder)
                .productId(productId)
                .vendorId(vendorId)
                .productSku(sku)
                .item(name)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP))
                .discountAmount(ZERO)
                .build());
    }

    private void setTracking(VendorOrder vo, String trackingNumber, String trackingUrl,
                             String carrierCode, LocalDate estimatedDelivery) {
        vo.setTrackingNumber(trackingNumber);
        vo.setTrackingUrl(trackingUrl);
        vo.setCarrierCode(carrierCode);
        vo.setEstimatedDeliveryDate(estimatedDelivery);
    }

    private static OrderAddressSnapshot addr(String label, String recipientName, String phone,
                                             String line1, String line2, String city,
                                             String state, String postalCode, String countryCode) {
        return OrderAddressSnapshot.builder()
                .label(label)
                .recipientName(recipientName)
                .phone(phone)
                .line1(line1)
                .line2(line2)
                .city(city)
                .state(state)
                .postalCode(postalCode)
                .countryCode(countryCode)
                .build();
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
