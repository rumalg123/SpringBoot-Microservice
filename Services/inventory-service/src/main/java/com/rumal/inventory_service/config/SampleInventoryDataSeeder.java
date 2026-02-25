package com.rumal.inventory_service.config;

import com.rumal.inventory_service.entity.*;
import com.rumal.inventory_service.repo.StockItemRepository;
import com.rumal.inventory_service.repo.StockMovementRepository;
import com.rumal.inventory_service.repo.WarehouseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleInventoryDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleInventoryDataSeeder.class);

    private static final UUID NOVA_TECH_VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID URBAN_STYLE_VENDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOME_CRAFT_VENDOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    public static final UUID PLATFORM_WAREHOUSE_ID = UUID.fromString("cccc1111-1111-1111-1111-111111111111");
    public static final UUID NOVA_WAREHOUSE_ID = UUID.fromString("cccc2222-2222-2222-2222-222222222222");
    public static final UUID URBAN_WAREHOUSE_ID = UUID.fromString("cccc3333-3333-3333-3333-333333333333");
    public static final UUID HOME_CRAFT_WAREHOUSE_ID = UUID.fromString("cccc4444-4444-4444-4444-444444444444");

    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EntityManager entityManager;

    @Value("${sample.inventory.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample inventory seed (sample.inventory.seed.enabled=false)");
            return;
        }
        if (warehouseRepository.count() > 0) {
            log.info("Skipping sample inventory seed because warehouses already exist.");
            return;
        }

        log.info("Seeding sample warehouses...");

        // Platform-managed warehouse
        Warehouse platformWarehouse = persistWarehouse(PLATFORM_WAREHOUSE_ID, "Central Platform Warehouse",
                "Main distribution center managed by the platform.",
                null, WarehouseType.PLATFORM_MANAGED,
                "100 Distribution Blvd", null, "Dallas", "TX", "75201", "US",
                "Platform Ops", "+1-555-0001", "ops@platform.example", true);

        // Nova Tech vendor warehouse
        Warehouse novaWarehouse = persistWarehouse(NOVA_WAREHOUSE_ID, "Nova Tech Electronics Hub",
                "Primary electronics storage for Nova Tech Store.",
                NOVA_TECH_VENDOR_ID, WarehouseType.VENDOR_OWNED,
                "200 Tech Park Drive", "Building A", "San Jose", "CA", "95101", "US",
                "Nora Vendor", "+1-555-0100", "warehouse@novatech.example", true);

        // Urban Style vendor warehouse
        Warehouse urbanWarehouse = persistWarehouse(URBAN_WAREHOUSE_ID, "Urban Style Fulfillment Center",
                "Fashion and lifestyle product storage.",
                URBAN_STYLE_VENDOR_ID, WarehouseType.VENDOR_OWNED,
                "350 Fashion Avenue", null, "New York", "NY", "10018", "US",
                "Uma Style", "+1-555-0101", "warehouse@urbanstyle.example", true);

        // Home Craft vendor warehouse (inactive — demonstrates inactive warehouse)
        Warehouse homeCraftWarehouse = persistWarehouse(HOME_CRAFT_WAREHOUSE_ID, "Home Craft Storage",
                "Home goods storage — currently undergoing renovation.",
                HOME_CRAFT_VENDOR_ID, WarehouseType.VENDOR_OWNED,
                "500 Homeware Lane", null, "Portland", "OR", "97201", "US",
                "Hina Craft", "+1-555-0102", "warehouse@homecraft.example", false);

        log.info("Sample inventory seed completed: warehouses={}", warehouseRepository.count());
    }

    private Warehouse persistWarehouse(UUID id, String name, String description, UUID vendorId,
                                       WarehouseType type, String addressLine1, String addressLine2,
                                       String city, String state, String postalCode, String countryCode,
                                       String contactName, String contactPhone, String contactEmail,
                                       boolean active) {
        Warehouse w = Warehouse.builder()
                .id(id)
                .name(name)
                .description(description)
                .vendorId(vendorId)
                .warehouseType(type)
                .addressLine1(addressLine1)
                .addressLine2(addressLine2)
                .city(city)
                .state(state)
                .postalCode(postalCode)
                .countryCode(countryCode)
                .contactName(contactName)
                .contactPhone(contactPhone)
                .contactEmail(contactEmail)
                .active(active)
                .build();
        entityManager.persist(w);
        return w;
    }
}
