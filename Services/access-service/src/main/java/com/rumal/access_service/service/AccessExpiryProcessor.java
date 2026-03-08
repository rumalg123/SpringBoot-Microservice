package com.rumal.access_service.service;

import com.rumal.access_service.entity.AccessChangeAction;
import com.rumal.access_service.entity.AccessAuditOutboxEvent;
import com.rumal.access_service.entity.ApiKey;
import com.rumal.access_service.entity.PlatformPermission;
import com.rumal.access_service.entity.PlatformStaffAccess;
import com.rumal.access_service.entity.VendorPermission;
import com.rumal.access_service.entity.VendorStaffAccess;
import com.rumal.access_service.repo.AccessAuditOutboxRepository;
import com.rumal.access_service.repo.ApiKeyRepository;
import com.rumal.access_service.repo.PlatformStaffAccessRepository;
import com.rumal.access_service.repo.VendorStaffAccessRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessExpiryProcessor {

    private static final Logger log = LoggerFactory.getLogger(AccessExpiryProcessor.class);

    private final PlatformStaffAccessRepository platformStaffAccessRepository;
    private final VendorStaffAccessRepository vendorStaffAccessRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AccessAuditOutboxRepository accessAuditOutboxRepository;
    private final CacheManager cacheManager;
    private final AccessAuditRequestContextResolver accessAuditRequestContextResolver;
    private final AccessAuditPayloadSanitizer accessAuditPayloadSanitizer;

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public int deactivateExpiredPlatformStaff() {
        Instant now = Instant.now();
        int count = 0;
        Pageable batch = PageRequest.of(0, 100);
        List<String> platformUserIds = new ArrayList<>();

        Page<PlatformStaffAccess> platformPage;
        do {
            platformPage = platformStaffAccessRepository
                    .findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(now, batch);
            platformPage.forEach(staff -> {
                staff.setActive(false);
                platformUserIds.add(staff.getKeycloakUserId());
                recordPlatformExpiryAudit(staff);
            });
            platformStaffAccessRepository.saveAll(platformPage.getContent());
            count += platformPage.getNumberOfElements();
        } while (platformPage.hasNext());

        if (count > 0) {
            log.info("Deactivated {} expired platform staff records", count);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                platformUserIds.forEach(uid -> evictCacheKey("platformAccessLookup", uid));
            }
        });

        return count;
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public int deactivateExpiredVendorStaff() {
        Instant now = Instant.now();
        int count = 0;
        Pageable batch = PageRequest.of(0, 100);
        List<String> vendorUserIds = new ArrayList<>();

        Page<VendorStaffAccess> vendorPage;
        do {
            vendorPage = vendorStaffAccessRepository
                    .findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(now, batch);
            vendorPage.forEach(staff -> {
                staff.setActive(false);
                vendorUserIds.add(staff.getKeycloakUserId());
                recordVendorExpiryAudit(staff);
            });
            vendorStaffAccessRepository.saveAll(vendorPage.getContent());
            count += vendorPage.getNumberOfElements();
        } while (vendorPage.hasNext());

        if (count > 0) {
            log.info("Deactivated {} expired vendor staff records", count);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                vendorUserIds.forEach(uid -> evictCacheKey("vendorAccessLookup", uid));
            }
        });

        return count;
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public int deactivateExpiredApiKeys() {
        Instant now = Instant.now();
        int count = 0;
        Pageable batch = PageRequest.of(0, 100);

        Page<ApiKey> keyPage;
        do {
            keyPage = apiKeyRepository.findByActiveTrueAndExpiresAtBefore(now, batch);
            keyPage.forEach(key -> key.setActive(false));
            apiKeyRepository.saveAll(keyPage.getContent());
            count += keyPage.getNumberOfElements();
        } while (keyPage.hasNext());

        if (count > 0) {
            log.info("Deactivated {} expired API keys", count);
        }

        return count;
    }

    private void recordPlatformExpiryAudit(PlatformStaffAccess staff) {
        AccessAuditRequestContext context = accessAuditRequestContextResolver.resolve(null, null, "SCHEDULED_EXPIRY");
        accessAuditOutboxRepository.save(AccessAuditOutboxEvent.builder()
                .targetType("PLATFORM_STAFF")
                .targetId(staff.getId())
                .vendorId(null)
                .keycloakUserId(staff.getKeycloakUserId())
                .email(accessAuditPayloadSanitizer.sanitizeEmail(staff.getEmail()))
                .action(AccessChangeAction.EXPIRED)
                .activeAfter(false)
                .deletedAfter(staff.isDeleted())
                .permissionsSnapshot(joinPlatformPermissions(staff.getPermissions()))
                .actorSub(null)
                .actorTenantId(context.actorTenantId())
                .actorRoles(null)
                .actorType("SYSTEM")
                .changeSource("SCHEDULED_EXPIRY")
                .reason("Automated expiry")
                .changeSet(accessAuditPayloadSanitizer.buildChangeSet(null, Map.of(
                        "active", false,
                        "deleted", staff.isDeleted(),
                        "accessExpiresAt", staff.getAccessExpiresAt()
                )))
                .clientIp(context.clientIp())
                .userAgent(context.userAgent())
                .requestId(context.requestId())
                .availableAt(Instant.now())
                .build());
    }

    private void recordVendorExpiryAudit(VendorStaffAccess staff) {
        AccessAuditRequestContext context = accessAuditRequestContextResolver.resolve(null, null, "SCHEDULED_EXPIRY");
        accessAuditOutboxRepository.save(AccessAuditOutboxEvent.builder()
                .targetType("VENDOR_STAFF")
                .targetId(staff.getId())
                .vendorId(staff.getVendorId())
                .keycloakUserId(staff.getKeycloakUserId())
                .email(accessAuditPayloadSanitizer.sanitizeEmail(staff.getEmail()))
                .action(AccessChangeAction.EXPIRED)
                .activeAfter(false)
                .deletedAfter(staff.isDeleted())
                .permissionsSnapshot(joinVendorPermissions(staff.getPermissions()))
                .actorSub(null)
                .actorTenantId(context.actorTenantId())
                .actorRoles(null)
                .actorType("SYSTEM")
                .changeSource("SCHEDULED_EXPIRY")
                .reason("Automated expiry")
                .changeSet(accessAuditPayloadSanitizer.buildChangeSet(null, Map.of(
                        "active", false,
                        "deleted", staff.isDeleted(),
                        "accessExpiresAt", staff.getAccessExpiresAt()
                )))
                .clientIp(context.clientIp())
                .userAgent(context.userAgent())
                .requestId(context.requestId())
                .availableAt(Instant.now())
                .build());
    }

    private String joinPlatformPermissions(Set<PlatformPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        return permissions.stream().filter(Objects::nonNull).map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private String joinVendorPermissions(Set<VendorPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        return permissions.stream().filter(Objects::nonNull).map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private void evictCacheKey(String cacheName, String keycloakUserId) {
        if (!StringUtils.hasText(keycloakUserId)) {
            return;
        }
        String key = keycloakUserId.trim().toLowerCase(Locale.ROOT);
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
