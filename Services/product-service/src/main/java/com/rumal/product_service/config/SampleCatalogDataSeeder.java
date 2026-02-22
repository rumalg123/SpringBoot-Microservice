package com.rumal.product_service.config;

import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductVariationAttributeRequest;
import com.rumal.product_service.dto.UpsertCategoryRequest;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.entity.CategoryType;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.repo.CategoryRepository;
import com.rumal.product_service.repo.ProductRepository;
import com.rumal.product_service.service.CategoryService;
import com.rumal.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleCatalogDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleCatalogDataSeeder.class);
    private static final UUID NOVA_TECH_VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID URBAN_STYLE_VENDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOME_CRAFT_VENDOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final List<String> IMAGE_POOL = List.of(
            "2e462316-ad68-4eb4-80dc-9ced0547aa0d.jpg",
            "356b853e-5d39-471e-b4e1-446862f05728.jpeg",
            "35fd4211-8e84-40f4-991e-5aa60de88e8c.jpeg",
            "4e7e5a30-5461-418e-a2d3-e0f625e4863f.png",
            "4fe8b68e-8cc8-47dc-bf1f-b3953ad4a030.jpg",
            "514dc848-8f4f-45da-bfb5-b862965e5e20.png",
            "b87075b8-4ebd-4099-83fc-2581bf70ec50.png",
            "c1729814-e83e-46f8-9496-aca8c4c32e70.jpeg",
            "c607a57b-3415-4d74-8541-4add54ddfc4c.jpg",
            "d74710f0-44fd-4453-a269-f3506c877a00.jpeg",
            "e87e890b-86e2-43af-a7fc-4ebc6c4de3e1.jpeg",
            "f24add21-5543-4eaf-a2f9-2ddf173e1d37.jpeg",
            "f55dc7ac-ca6c-45e0-8719-a3c1354a3c8b.jpg"
    );

    private final CategoryService categoryService;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Value("${sample.catalog.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample catalog seed (sample.catalog.seed.enabled=false)");
            return;
        }

        if (categoryRepository.count() > 0 || productRepository.count() > 0) {
            log.info("Skipping sample catalog seed because data already exists.");
            return;
        }

        log.info("Seeding sample categories and products...");

        CategoryResponse electronics = createCategory("Electronics", "electronics", CategoryType.PARENT, null);
        CategoryResponse fashion = createCategory("Fashion", "fashion", CategoryType.PARENT, null);
        CategoryResponse homeLiving = createCategory("Home & Living", "home-living", CategoryType.PARENT, null);

        createCategory("Smartphones", "smartphones", CategoryType.SUB, electronics.id());
        createCategory("Audio", "audio", CategoryType.SUB, electronics.id());
        createCategory("Accessories", "accessories", CategoryType.SUB, electronics.id());
        createCategory("Shoes", "shoes", CategoryType.SUB, fashion.id());
        createCategory("Men Clothing", "men-clothing", CategoryType.SUB, fashion.id());
        createCategory("Kitchen", "kitchen", CategoryType.SUB, homeLiving.id());
        createCategory("Decor", "decor", CategoryType.SUB, homeLiving.id());

        createSingle(
                "EchoBuds Wireless Earbuds",
                "echobuds-wireless-earbuds",
                "Compact earbuds with noise cancellation and deep bass.",
                "EchoBuds Wireless Earbuds deliver balanced sound, noise reduction and all-day comfort for calls, music and workouts.",
                "ELEC-EBUDS-001",
                "2e462316-ad68-4eb4-80dc-9ced0547aa0d.jpg",
                "79.00",
                "59.00",
                Set.of("Electronics", "Audio")
        );

        createSingle(
                "NovaCharge 65W Fast Charger",
                "novacharge-65w-fast-charger",
                "GaN charger with dual output for fast charging.",
                "NovaCharge 65W uses GaN technology for efficient charging of phones, tablets and lightweight laptops with stable thermal performance.",
                "ELEC-CHARGER-001",
                "356b853e-5d39-471e-b4e1-446862f05728.jpeg",
                "39.00",
                "29.00",
                Set.of("Electronics", "Accessories")
        );

        createSingle(
                "AetherSmart LED Desk Lamp",
                "aethersmart-led-desk-lamp",
                "Eye-friendly desk lamp with adjustable brightness.",
                "AetherSmart LED Desk Lamp provides flexible angle control, multiple light temperatures and low flicker illumination for workspaces.",
                "HOME-LAMP-001",
                "35fd4211-8e84-40f4-991e-5aa60de88e8c.jpeg",
                "49.00",
                "39.00",
                Set.of("Home & Living", "Decor")
        );

        createSingle(
                "HomeChef Non-Stick Pan Set",
                "homechef-non-stick-pan-set",
                "Durable non-stick cookware set for daily use.",
                "HomeChef Non-Stick Pan Set includes versatile sizes for frying and sauteing with easy-clean coating and even heat distribution.",
                "HOME-PAN-001",
                "4e7e5a30-5461-418e-a2d3-e0f625e4863f.png",
                "99.00",
                "79.00",
                Set.of("Home & Living", "Kitchen")
        );

        createSingle(
                "FitStride Training T-Shirt",
                "fitstride-training-t-shirt",
                "Breathable activewear tee for everyday training.",
                "FitStride Training T-Shirt uses lightweight moisture-wicking fabric to keep airflow comfortable during running, gym sessions and outdoor workouts.",
                "FASH-TEE-001",
                "4fe8b68e-8cc8-47dc-bf1f-b3953ad4a030.jpg",
                "29.00",
                "19.00",
                Set.of("Fashion", "Men Clothing")
        );

        createSingle(
                "PulseGuard Screen Protector",
                "pulseguard-screen-protector",
                "Tempered glass screen protection with easy install kit.",
                "PulseGuard Screen Protector offers scratch resistance and smooth touch sensitivity while keeping smartphone displays clear and durable.",
                "ELEC-PROTECT-001",
                "514dc848-8f4f-45da-bfb5-b862965e5e20.png",
                "19.00",
                "14.00",
                Set.of("Electronics", "Accessories")
        );

        createSingle(
                "AeroSound Bluetooth Speaker",
                "aerosound-bluetooth-speaker",
                "Portable speaker with punchy sound and long battery life.",
                "AeroSound Bluetooth Speaker combines rich audio, sturdy build and portable design for indoor and outdoor listening with reliable wireless connectivity.",
                "ELEC-SPEAKER-001",
                "b87075b8-4ebd-4099-83fc-2581bf70ec50.png",
                "89.00",
                "69.00",
                Set.of("Electronics", "Audio")
        );

        ProductResponse sneakersParent = createParent(
                "Urban Pulse Sneakers",
                "urban-pulse-sneakers",
                "Lifestyle sneakers available in multiple colors and sizes.",
                "Urban Pulse Sneakers blend everyday comfort and modern style with durable sole grip and multiple variation combinations.",
                "FASH-SNK-PARENT-001",
                "c1729814-e83e-46f8-9496-aca8c4c32e70.jpeg",
                "129.00",
                "99.00",
                Set.of("Fashion", "Shoes"),
                List.of("size", "color")
        );

        createVariation(
                sneakersParent.id(),
                "Urban Pulse Sneakers - Black / 42",
                "urban-pulse-sneakers-black-42",
                "Variation: black color, size 42.",
                "Black Urban Pulse Sneakers in size 42, tuned for all-day urban movement and casual comfort.",
                "FASH-SNK-BLK-42",
                "c607a57b-3415-4d74-8541-4add54ddfc4c.jpg",
                "129.00",
                "99.00",
                List.of(
                        new ProductVariationAttributeRequest("size", "42"),
                        new ProductVariationAttributeRequest("color", "black")
                )
        );

        createVariation(
                sneakersParent.id(),
                "Urban Pulse Sneakers - White / 43",
                "urban-pulse-sneakers-white-43",
                "Variation: white color, size 43.",
                "White Urban Pulse Sneakers in size 43 with breathable finish and lightweight support for everyday wear.",
                "FASH-SNK-WHT-43",
                "d74710f0-44fd-4453-a269-f3506c877a00.jpeg",
                "129.00",
                "89.00",
                List.of(
                        new ProductVariationAttributeRequest("size", "43"),
                        new ProductVariationAttributeRequest("color", "white")
                )
        );

        createVariation(
                sneakersParent.id(),
                "Urban Pulse Sneakers - Red / 41",
                "urban-pulse-sneakers-red-41",
                "Variation: red color, size 41.",
                "Red Urban Pulse Sneakers in size 41 for bold everyday looks with stable fit and balanced cushioning.",
                "FASH-SNK-RED-41",
                "e87e890b-86e2-43af-a7fc-4ebc6c4de3e1.jpeg",
                "129.00",
                "95.00",
                List.of(
                        new ProductVariationAttributeRequest("size", "41"),
                        new ProductVariationAttributeRequest("color", "red")
                )
        );

        ProductResponse phoneCaseParent = createParent(
                "NovaShield Phone Case",
                "novashield-phone-case",
                "Protective case series with phone model and color options.",
                "NovaShield Phone Case series is designed for shock absorption and grip comfort, with variation options by model and color.",
                "ELEC-CASE-PARENT-001",
                "f24add21-5543-4eaf-a2f9-2ddf173e1d37.jpeg",
                "29.00",
                "19.00",
                Set.of("Electronics", "Accessories", "Smartphones"),
                List.of("model", "color")
        );

        createVariation(
                phoneCaseParent.id(),
                "NovaShield Phone Case - iPhone 15 / Black",
                "novashield-phone-case-iphone-15-black",
                "Variation: iPhone 15 model in black.",
                "NovaShield Phone Case for iPhone 15 in black finish with reinforced edge protection and precise cutouts.",
                "ELEC-CASE-IP15-BLK",
                "f55dc7ac-ca6c-45e0-8719-a3c1354a3c8b.jpg",
                "29.00",
                "19.00",
                List.of(
                        new ProductVariationAttributeRequest("model", "iphone-15"),
                        new ProductVariationAttributeRequest("color", "black")
                )
        );

        seedPaginationSingles();

        log.info("Sample catalog seed completed: categories={}, products={}", categoryRepository.count(), productRepository.count());
    }

    private void seedPaginationSingles() {
        int cursor = 0;

        for (int i = 1; i <= 12; i++) {
            BigDecimal regular = BigDecimal.valueOf(34 + i);
            BigDecimal discounted = regular.subtract(BigDecimal.valueOf(7)).setScale(2, RoundingMode.HALF_UP);
            createSingle(
                    "VoltEdge Gadget " + i,
                    String.format("seed-voltedge-gadget-%02d", i),
                    "Portable electronics gadget for daily convenience.",
                    "VoltEdge Gadget " + i + " is part of the sample catalog batch used for pagination, filters and listing behavior tests.",
                    String.format("SEED-ELEC-%03d", i),
                    pickImage(cursor++),
                    regular.toPlainString(),
                    discounted.toPlainString(),
                    Set.of("Electronics", (i % 2 == 0) ? "Accessories" : "Smartphones")
            );
        }

        for (int i = 1; i <= 12; i++) {
            BigDecimal regular = BigDecimal.valueOf(42 + i);
            BigDecimal discounted = regular.subtract(BigDecimal.valueOf(9)).setScale(2, RoundingMode.HALF_UP);
            createSingle(
                    "StreetForm Fashion Basic " + i,
                    String.format("seed-streetform-fashion-%02d", i),
                    "Everyday fashion item for fit and comfort.",
                    "StreetForm Fashion Basic " + i + " is seeded for storefront pagination and sorting tests with consistent product schema.",
                    String.format("SEED-FASH-%03d", i),
                    pickImage(cursor++),
                    regular.toPlainString(),
                    discounted.toPlainString(),
                    Set.of("Fashion", (i % 2 == 0) ? "Shoes" : "Men Clothing")
            );
        }

        for (int i = 1; i <= 12; i++) {
            BigDecimal regular = BigDecimal.valueOf(38 + i);
            BigDecimal discounted = regular.subtract(BigDecimal.valueOf(6)).setScale(2, RoundingMode.HALF_UP);
            createSingle(
                    "CasaCraft Home Essential " + i,
                    String.format("seed-casacraft-home-%02d", i),
                    "Home utility product with practical design.",
                    "CasaCraft Home Essential " + i + " is seeded to increase catalog depth for pagination and category page validation.",
                    String.format("SEED-HOME-%03d", i),
                    pickImage(cursor++),
                    regular.toPlainString(),
                    discounted.toPlainString(),
                    Set.of("Home & Living", (i % 2 == 0) ? "Kitchen" : "Decor")
            );
        }
    }

    private String pickImage(int index) {
        return IMAGE_POOL.get(Math.floorMod(index, IMAGE_POOL.size()));
    }

    private CategoryResponse createCategory(String name, String slug, CategoryType type, UUID parentCategoryId) {
        return categoryService.create(new UpsertCategoryRequest(name, slug, type, parentCategoryId));
    }

    private ProductResponse createSingle(
            String name,
            String slug,
            String shortDescription,
            String description,
            String sku,
            String image,
            String regularPrice,
            String discountedPrice,
            Set<String> categories
    ) {
        return productService.create(new UpsertProductRequest(
                name,
                slug,
                shortDescription,
                description,
                List.of(image),
                new BigDecimal(regularPrice),
                new BigDecimal(discountedPrice),
                resolveSeedVendorId(categories),
                categories,
                ProductType.SINGLE,
                List.of(),
                sku,
                true
        ));
    }

    private ProductResponse createParent(
            String name,
            String slug,
            String shortDescription,
            String description,
            String sku,
            String image,
            String regularPrice,
            String discountedPrice,
            Set<String> categories,
            List<String> variationNames
    ) {
        List<ProductVariationAttributeRequest> attributes = variationNames.stream()
                .map(attribute -> new ProductVariationAttributeRequest(attribute, ""))
                .toList();
        return productService.create(new UpsertProductRequest(
                name,
                slug,
                shortDescription,
                description,
                List.of(image),
                new BigDecimal(regularPrice),
                new BigDecimal(discountedPrice),
                resolveSeedVendorId(categories),
                categories,
                ProductType.PARENT,
                attributes,
                sku,
                true
        ));
    }

    private ProductResponse createVariation(
            UUID parentId,
            String name,
            String slug,
            String shortDescription,
            String description,
            String sku,
            String image,
            String regularPrice,
            String discountedPrice,
            List<ProductVariationAttributeRequest> attributes
    ) {
        return productService.createVariation(parentId, new UpsertProductRequest(
                name,
                slug,
                shortDescription,
                description,
                List.of(image),
                new BigDecimal(regularPrice),
                new BigDecimal(discountedPrice),
                null,
                null,
                ProductType.VARIATION,
                attributes,
                sku,
                true
        ));
    }

    private UUID resolveSeedVendorId(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return NOVA_TECH_VENDOR_ID;
        }
        if (categories.contains("Fashion")) {
            return URBAN_STYLE_VENDOR_ID;
        }
        if (categories.contains("Home & Living")) {
            return HOME_CRAFT_VENDOR_ID;
        }
        return NOVA_TECH_VENDOR_ID;
    }
}
