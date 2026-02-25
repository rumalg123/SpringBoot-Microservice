package com.rumal.wishlist_service.config;

import com.rumal.wishlist_service.entity.WishlistCollection;
import com.rumal.wishlist_service.entity.WishlistItem;
import com.rumal.wishlist_service.repo.WishlistCollectionRepository;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleWishlistDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleWishlistDataSeeder.class);

    // Sample product IDs (for reference — not linked to product-service auto-generated IDs)
    private static final UUID P_EARBUDS = UUID.fromString("00ab0001-0001-0001-0001-000000000001");
    private static final UUID P_CHARGER = UUID.fromString("00ab0001-0001-0001-0001-000000000002");
    private static final UUID P_SNEAKERS = UUID.fromString("00ab0001-0001-0001-0001-000000000003");
    private static final UUID P_LAMP = UUID.fromString("00ab0001-0001-0001-0001-000000000004");
    private static final UUID P_PAN_SET = UUID.fromString("00ab0001-0001-0001-0001-000000000005");
    private static final UUID P_SPEAKER = UUID.fromString("00ab0001-0001-0001-0001-000000000008");

    private final WishlistCollectionRepository collectionRepository;
    private final EntityManager em;

    @Value("${sample.wishlist.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample wishlist seed (sample.wishlist.seed.enabled=false)");
            return;
        }
        if (collectionRepository.count() > 0) {
            log.info("Skipping sample wishlist seed because collections already exist.");
            return;
        }

        log.info("Seeding sample wishlists...");

        // Alice — default collection (private) + shared collection
        WishlistCollection aliceDefault = persistCollection(
                UUID.fromString("ac000001-0001-0001-0001-000000000001"),
                "kc-customer-alice", "My Wishlist", null,
                true, false, null);
        persistWishlistItem(UUID.fromString("a1000001-0001-0001-0001-000000000001"),
                "kc-customer-alice", aliceDefault, P_SPEAKER,
                "aerosound-bluetooth-speaker", "AeroSound Bluetooth Speaker",
                "SINGLE", "514dc848-8f4f-45da-bfb5-b862965e5e20.png",
                bd("69.00"), "Love the sound quality!");
        persistWishlistItem(UUID.fromString("a1000002-0001-0001-0001-000000000002"),
                "kc-customer-alice", aliceDefault, P_PAN_SET,
                "homechef-non-stick-pan-set", "HomeChef Non-Stick Pan Set",
                "SINGLE", "4e7e5a30-5461-418e-a2d3-e0f625e4863f.png",
                bd("79.00"), null);

        WishlistCollection aliceShared = persistCollection(
                UUID.fromString("ac000002-0001-0001-0001-000000000002"),
                "kc-customer-alice", "Gift Ideas", "Things I'd love to receive!",
                false, true, "alice-gift-ideas-2025");
        persistWishlistItem(UUID.fromString("a1000003-0001-0001-0001-000000000003"),
                "kc-customer-alice", aliceShared, P_SNEAKERS,
                "urban-pulse-sneakers-black-42", "Urban Pulse Sneakers - Black / 42",
                "VARIATION", "4fe8b68e-8cc8-47dc-bf1f-b3953ad4a030.jpg",
                bd("99.00"), "Size 42, black please");

        // Bob — default collection only
        WishlistCollection bobDefault = persistCollection(
                UUID.fromString("ac000003-0002-0002-0002-000000000003"),
                "kc-customer-bob", "My Wishlist", null,
                true, false, null);
        persistWishlistItem(UUID.fromString("a1000004-0002-0002-0002-000000000004"),
                "kc-customer-bob", bobDefault, P_EARBUDS,
                "echobuds-wireless-earbuds", "EchoBuds Wireless Earbuds",
                "SINGLE", "2e462316-ad68-4eb4-80dc-9ced0547aa0d.jpg",
                bd("59.00"), null);
        persistWishlistItem(UUID.fromString("a1000005-0002-0002-0002-000000000005"),
                "kc-customer-bob", bobDefault, P_LAMP,
                "aethersmart-led-desk-lamp", "AetherSmart LED Desk Lamp",
                "SINGLE", "35fd4211-8e84-40f4-991e-5aa60de88e8c.jpeg",
                bd("39.00"), "For the home office");

        // Carol — default collection + second private collection
        WishlistCollection carolDefault = persistCollection(
                UUID.fromString("ac000004-0003-0003-0003-000000000004"),
                "kc-customer-carol", "My Wishlist", null,
                true, false, null);
        persistWishlistItem(UUID.fromString("a1000006-0003-0003-0003-000000000006"),
                "kc-customer-carol", carolDefault, P_CHARGER,
                "novacharge-65w-fast-charger", "NovaCharge 65W Fast Charger",
                "SINGLE", "356b853e-5d39-471e-b4e1-446862f05728.jpeg",
                bd("29.00"), null);

        WishlistCollection carolKitchen = persistCollection(
                UUID.fromString("ac000005-0003-0003-0003-000000000005"),
                "kc-customer-carol", "Kitchen Upgrades", "Home kitchen essentials",
                false, false, null);
        persistWishlistItem(UUID.fromString("a1000007-0003-0003-0003-000000000007"),
                "kc-customer-carol", carolKitchen, P_PAN_SET,
                "homechef-non-stick-pan-set", "HomeChef Non-Stick Pan Set",
                "SINGLE", "4e7e5a30-5461-418e-a2d3-e0f625e4863f.png",
                bd("79.00"), "Need a good pan set");

        log.info("Sample wishlist seed completed: collections={}, items={}",
                collectionRepository.count(), em.createQuery("SELECT COUNT(w) FROM WishlistItem w", Long.class).getSingleResult());
    }

    private WishlistCollection persistCollection(UUID id, String keycloakId, String name,
                                                  String description, boolean isDefault,
                                                  boolean shared, String shareToken) {
        WishlistCollection c = WishlistCollection.builder()
                .id(id)
                .keycloakId(keycloakId)
                .name(name)
                .description(description)
                .isDefault(isDefault)
                .shared(shared)
                .shareToken(shareToken)
                .build();
        return em.merge(c);
    }

    private void persistWishlistItem(UUID id, String keycloakId, WishlistCollection collection,
                                     UUID productId, String productSlug, String productName,
                                     String productType, String mainImage,
                                     BigDecimal sellingPrice, String note) {
        em.merge(WishlistItem.builder()
                .id(id)
                .keycloakId(keycloakId)
                .collection(collection)
                .productId(productId)
                .productSlug(productSlug)
                .productName(productName)
                .productType(productType)
                .mainImage(mainImage)
                .sellingPriceSnapshot(sellingPrice)
                .note(note)
                .build());
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
