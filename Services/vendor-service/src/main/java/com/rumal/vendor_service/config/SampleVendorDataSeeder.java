package com.rumal.vendor_service.config;

import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorStatus;
import com.rumal.vendor_service.entity.VendorUserRole;
import com.rumal.vendor_service.repo.VendorRepository;
import com.rumal.vendor_service.service.VendorService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleVendorDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleVendorDataSeeder.class);
    public static final UUID NOVA_TECH_VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID URBAN_STYLE_VENDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID HOME_CRAFT_VENDOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final VendorService vendorService;
    private final VendorRepository vendorRepository;
    private final EntityManager entityManager;

    @Value("${sample.vendor.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Skipping sample vendor seed (sample.vendor.seed.enabled=false)");
            return;
        }
        if (vendorRepository.count() > 0) {
            log.info("Skipping sample vendor seed because vendors already exist.");
            return;
        }

        log.info("Seeding sample vendors...");

        seedVendor(
                NOVA_TECH_VENDOR_ID,
                "Nova Tech Store",
                "nova-tech-store",
                "owner@novatech.example",
                "support@novatech.example",
                "+1-555-0100",
                "Nora Vendor",
                "https://novatech.example",
                "Electronics-focused marketplace seller for testing multi-vendor scoping.",
                VendorStatus.ACTIVE,
                true
        );
        vendorService.addVendorUser(NOVA_TECH_VENDOR_ID, new UpsertVendorUserRequest(
                "kc-vendor-nova-owner",
                "owner@novatech.example",
                "Nora Vendor",
                VendorUserRole.OWNER,
                true
        ));
        vendorService.addVendorUser(NOVA_TECH_VENDOR_ID, new UpsertVendorUserRequest(
                "kc-vendor-nova-manager",
                "manager@novatech.example",
                "Nova Manager",
                VendorUserRole.MANAGER,
                true
        ));

        seedVendor(
                URBAN_STYLE_VENDOR_ID,
                "Urban Style House",
                "urban-style-house",
                "owner@urbanstyle.example",
                "support@urbanstyle.example",
                "+1-555-0101",
                "Uma Style",
                "https://urbanstyle.example",
                "Fashion and lifestyle seller used for vendor onboarding and admin tests.",
                VendorStatus.PENDING,
                true
        );
        vendorService.addVendorUser(URBAN_STYLE_VENDOR_ID, new UpsertVendorUserRequest(
                "kc-vendor-style-owner",
                "owner@urbanstyle.example",
                "Uma Style",
                VendorUserRole.OWNER,
                true
        ));

        seedVendor(
                HOME_CRAFT_VENDOR_ID,
                "Home Craft Market",
                "home-craft-market",
                "owner@homecraft.example",
                "support@homecraft.example",
                "+1-555-0102",
                "Hina Craft",
                "https://homecraft.example",
                "Home goods vendor sample for public vendor listing filters.",
                VendorStatus.ACTIVE,
                true
        );

        log.info("Sample vendor seed completed: vendors={}", vendorRepository.count());
    }

    private void seedVendor(
            UUID id,
            String name,
            String slug,
            String contactEmail,
            String supportEmail,
            String contactPhone,
            String contactPersonName,
            String websiteUrl,
            String description,
            VendorStatus status,
            boolean active
    ) {
        entityManager.persist(Vendor.builder()
                .id(id)
                .name(name)
                .normalizedName(name.toLowerCase(Locale.ROOT))
                .slug(slug)
                .contactEmail(contactEmail.toLowerCase(Locale.ROOT))
                .supportEmail(supportEmail.toLowerCase(Locale.ROOT))
                .contactPhone(contactPhone)
                .contactPersonName(contactPersonName)
                .websiteUrl(websiteUrl)
                .description(description)
                .status(status)
                .active(active)
                .deleted(false)
                .deletedAt(null)
                .build());
    }
}
