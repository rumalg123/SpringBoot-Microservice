package com.rumal.admin_service.config;

import com.rumal.admin_service.entity.FeatureFlag;
import com.rumal.admin_service.entity.SystemConfig;
import com.rumal.admin_service.repo.FeatureFlagRepository;
import com.rumal.admin_service.repo.SystemConfigRepository;
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
public class SampleAdminDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleAdminDataSeeder.class);

    private final SystemConfigRepository systemConfigRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final EntityManager em;

    @Value("${sample.admin.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample admin seed (sample.admin.seed.enabled=false)");
            return;
        }
        if (systemConfigRepository.count() > 0 || featureFlagRepository.count() > 0) {
            log.info("Skipping sample admin seed because data already exists.");
            return;
        }

        log.info("Seeding sample admin data...");

        // ── System Config entries ────────────────────────────────────────
        persistConfig(UUID.fromString("ad0c0001-0001-0001-0001-000000000001"),
                "platform.name", "Rumal Marketplace",
                "Display name of the platform", "STRING");
        persistConfig(UUID.fromString("ad0c0002-0001-0001-0001-000000000002"),
                "platform.support.email", "support@rumal.example",
                "Customer support email address", "STRING");
        persistConfig(UUID.fromString("ad0c0003-0001-0001-0001-000000000003"),
                "platform.commission.rate", "10",
                "Default platform commission percentage for vendor payouts", "INTEGER");
        persistConfig(UUID.fromString("ad0c0004-0001-0001-0001-000000000004"),
                "order.payment.timeout.minutes", "30",
                "Minutes before an unpaid order expires", "INTEGER");
        persistConfig(UUID.fromString("ad0c0005-0001-0001-0001-000000000005"),
                "order.return.window.days", "30",
                "Number of days after delivery that returns are accepted", "INTEGER");
        persistConfig(UUID.fromString("ad0c0006-0001-0001-0001-000000000006"),
                "inventory.low.stock.threshold", "10",
                "Default low stock threshold for new stock items", "INTEGER");
        persistConfig(UUID.fromString("ad0c0007-0001-0001-0001-000000000007"),
                "cart.max.items", "50",
                "Maximum number of distinct items allowed in a cart", "INTEGER");
        persistConfig(UUID.fromString("ad0c0008-0001-0001-0001-000000000008"),
                "platform.maintenance.mode", "false",
                "When true, the platform shows a maintenance page", "BOOLEAN");

        // ── Feature Flag entries ─────────────────────────────────────────
        persistFlag(UUID.fromString("ad0f0001-0001-0001-0001-000000000001"),
                "feature.wishlist.sharing", "Allow customers to share wishlist collections via public links",
                true, null, null);
        persistFlag(UUID.fromString("ad0f0002-0001-0001-0001-000000000002"),
                "feature.backorder.enabled", "Allow backorder purchases when stock is zero but item is backorderable",
                true, null, null);
        persistFlag(UUID.fromString("ad0f0003-0001-0001-0001-000000000003"),
                "feature.bulk.stock.import", "Enable bulk stock import for vendors and admins",
                true, "ROLE_ADMIN,ROLE_VENDOR", null);
        persistFlag(UUID.fromString("ad0f0004-0001-0001-0001-000000000004"),
                "feature.new.checkout.flow", "Redesigned checkout experience with address validation",
                false, null, 25);
        persistFlag(UUID.fromString("ad0f0005-0001-0001-0001-000000000005"),
                "feature.ai.recommendations", "AI-powered product recommendations on product detail pages",
                false, null, 0);
        persistFlag(UUID.fromString("ad0f0006-0001-0001-0001-000000000006"),
                "feature.vendor.analytics.dashboard", "Enhanced analytics dashboard for vendors",
                true, "ROLE_VENDOR", 100);

        log.info("Sample admin seed completed: systemConfigs={}, featureFlags={}",
                systemConfigRepository.count(), featureFlagRepository.count());
    }

    private void persistConfig(UUID id, String key, String value, String description, String valueType) {
        em.persist(SystemConfig.builder()
                .id(id)
                .configKey(key)
                .configValue(value)
                .description(description)
                .valueType(valueType)
                .active(true)
                .build());
    }

    private void persistFlag(UUID id, String flagKey, String description,
                             boolean enabled, String enabledForRoles, Integer rolloutPercentage) {
        em.persist(FeatureFlag.builder()
                .id(id)
                .flagKey(flagKey)
                .description(description)
                .enabled(enabled)
                .enabledForRoles(enabledForRoles)
                .rolloutPercentage(rolloutPercentage)
                .build());
    }
}
