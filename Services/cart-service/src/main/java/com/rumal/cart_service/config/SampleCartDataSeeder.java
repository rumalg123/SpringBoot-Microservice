package com.rumal.cart_service.config;

import com.rumal.cart_service.entity.Cart;
import com.rumal.cart_service.entity.CartItem;
import com.rumal.cart_service.repo.CartRepository;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleCartDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleCartDataSeeder.class);

    // Sample product IDs (for reference — not linked to product-service auto-generated IDs)
    private static final UUID P_EARBUDS = UUID.fromString("00pp0001-0001-0001-0001-000000000001");
    private static final UUID P_CHARGER = UUID.fromString("00pp0001-0001-0001-0001-000000000002");
    private static final UUID P_SNEAKERS = UUID.fromString("00pp0001-0001-0001-0001-000000000003");
    private static final UUID P_LAMP = UUID.fromString("00pp0001-0001-0001-0001-000000000004");
    private static final UUID P_PAN_SET = UUID.fromString("00pp0001-0001-0001-0001-000000000005");
    private static final UUID P_TSHIRT = UUID.fromString("00pp0001-0001-0001-0001-000000000006");

    private final CartRepository cartRepository;
    private final EntityManager em;

    @Value("${sample.cart.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample cart seed (sample.cart.seed.enabled=false)");
            return;
        }
        if (cartRepository.count() > 0) {
            log.info("Skipping sample cart seed because carts already exist.");
            return;
        }

        log.info("Seeding sample carts...");

        // Alice — active cart with 2 items + 1 saved for later
        Cart aliceCart = persistCart(
                UUID.fromString("cc000001-0001-0001-0001-000000000001"),
                "kc-customer-alice", "Birthday gift shopping");
        persistItem(UUID.fromString("cc100001-0001-0001-0001-000000000001"),
                aliceCart, P_EARBUDS, "echobuds-wireless-earbuds",
                "EchoBuds Wireless Earbuds", "ELEC-EBUDS-001",
                "2e462316-ad68-4eb4-80dc-9ced0547aa0d.jpg", null,
                bd("59.00"), 1, false);
        persistItem(UUID.fromString("cc100002-0001-0001-0001-000000000002"),
                aliceCart, P_SNEAKERS, "urban-pulse-sneakers-black-42",
                "Urban Pulse Sneakers - Black / 42", "FASH-SNK-BLK-42",
                "4fe8b68e-8cc8-47dc-bf1f-b3953ad4a030.jpg", null,
                bd("99.00"), 1, false);
        persistItem(UUID.fromString("cc100003-0001-0001-0001-000000000003"),
                aliceCart, P_LAMP, "aethersmart-led-desk-lamp",
                "AetherSmart LED Desk Lamp", "HOME-LAMP-001",
                "35fd4211-8e84-40f4-991e-5aa60de88e8c.jpeg", null,
                bd("39.00"), 1, true);

        // Bob — cart with 1 item (quantity 2)
        Cart bobCart = persistCart(
                UUID.fromString("cc000002-0002-0002-0002-000000000002"),
                "kc-customer-bob", null);
        persistItem(UUID.fromString("cc100004-0001-0001-0001-000000000004"),
                bobCart, P_TSHIRT, "fitstride-training-t-shirt",
                "FitStride Training T-Shirt", "FASH-TEE-001",
                "4fe8b68e-8cc8-47dc-bf1f-b3953ad4a030.jpg", null,
                bd("19.00"), 2, false);

        // Carol — cart with 2 items
        Cart carolCart = persistCart(
                UUID.fromString("cc000003-0003-0003-0003-000000000003"),
                "kc-customer-carol", null);
        persistItem(UUID.fromString("cc100005-0001-0001-0001-000000000005"),
                carolCart, P_PAN_SET, "homechef-non-stick-pan-set",
                "HomeChef Non-Stick Pan Set", "HOME-PAN-001",
                "4e7e5a30-5461-418e-a2d3-e0f625e4863f.png", null,
                bd("79.00"), 1, false);
        persistItem(UUID.fromString("cc100006-0001-0001-0001-000000000006"),
                carolCart, P_CHARGER, "novacharge-65w-fast-charger",
                "NovaCharge 65W Fast Charger", "ELEC-CHARGER-001",
                "356b853e-5d39-471e-b4e1-446862f05728.jpeg", null,
                bd("29.00"), 1, false);

        log.info("Sample cart seed completed: carts={}", cartRepository.count());
    }

    private Cart persistCart(UUID id, String keycloakId, String note) {
        Cart c = Cart.builder()
                .id(id)
                .keycloakId(keycloakId)
                .note(note)
                .lastActivityAt(Instant.now())
                .build();
        em.persist(c);
        return c;
    }

    private void persistItem(UUID id, Cart cart, UUID productId, String productSlug,
                             String productName, String productSku, String mainImage,
                             String categoryIds, BigDecimal unitPrice, int quantity,
                             boolean savedForLater) {
        em.persist(CartItem.builder()
                .id(id)
                .cart(cart)
                .productId(productId)
                .productSlug(productSlug)
                .productName(productName)
                .productSku(productSku)
                .mainImage(mainImage)
                .categoryIds(categoryIds)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .lineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP))
                .savedForLater(savedForLater)
                .build());
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
