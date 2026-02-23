package com.rumal.access_service.config;

import com.rumal.access_service.dto.UpsertPlatformStaffAccessRequest;
import com.rumal.access_service.dto.UpsertVendorStaffAccessRequest;
import com.rumal.access_service.entity.PlatformPermission;
import com.rumal.access_service.entity.VendorPermission;
import com.rumal.access_service.repo.PlatformStaffAccessRepository;
import com.rumal.access_service.repo.VendorStaffAccessRepository;
import com.rumal.access_service.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleAccessDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleAccessDataSeeder.class);
    private static final UUID NOVA_TECH_VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID URBAN_STYLE_VENDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AccessService accessService;
    private final PlatformStaffAccessRepository platformStaffAccessRepository;
    private final VendorStaffAccessRepository vendorStaffAccessRepository;

    @Value("${sample.access.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        if (platformStaffAccessRepository.count() > 0 || vendorStaffAccessRepository.count() > 0) {
            return;
        }
        log.info("Seeding sample access records...");

        accessService.createPlatformStaff(new UpsertPlatformStaffAccessRequest(
                "kc-platform-orders",
                "orders.staff@example.com",
                "Platform Orders Staff",
                Set.of(PlatformPermission.ORDERS_READ, PlatformPermission.ORDERS_MANAGE),
                true
        ));

        accessService.createPlatformStaff(new UpsertPlatformStaffAccessRequest(
                "kc-platform-catalog",
                "catalog.staff@example.com",
                "Platform Catalog Staff",
                Set.of(PlatformPermission.PRODUCTS_MANAGE, PlatformPermission.CATEGORIES_MANAGE),
                true
        ));

        accessService.createVendorStaff(new UpsertVendorStaffAccessRequest(
                NOVA_TECH_VENDOR_ID,
                "kc-vendor-nova-catalog-staff",
                "catalog@novatech.example",
                "Nova Catalog Staff",
                Set.of(VendorPermission.PRODUCTS_MANAGE),
                true
        ));

        accessService.createVendorStaff(new UpsertVendorStaffAccessRequest(
                URBAN_STYLE_VENDOR_ID,
                "kc-vendor-style-order-staff",
                "orders@urbanstyle.example",
                "Urban Order Staff",
                Set.of(VendorPermission.ORDERS_READ, VendorPermission.ORDERS_MANAGE),
                true
        ));

        log.info("Sample access seed complete: platformStaff={}, vendorStaff={}",
                platformStaffAccessRepository.count(), vendorStaffAccessRepository.count());
    }
}
