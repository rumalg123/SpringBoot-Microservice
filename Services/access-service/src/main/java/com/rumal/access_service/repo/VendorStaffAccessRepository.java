package com.rumal.access_service.repo;

import com.rumal.access_service.entity.VendorStaffAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorStaffAccessRepository extends JpaRepository<VendorStaffAccess, UUID> {
    List<VendorStaffAccess> findByDeletedFalseOrderByVendorIdAscEmailAsc();
    Page<VendorStaffAccess> findByDeletedFalse(Pageable pageable);
    List<VendorStaffAccess> findByDeletedTrueOrderByUpdatedAtDesc();
    Page<VendorStaffAccess> findByDeletedTrue(Pageable pageable);
    List<VendorStaffAccess> findByVendorIdAndDeletedFalseOrderByEmailAsc(UUID vendorId);
    Page<VendorStaffAccess> findByVendorIdAndDeletedFalse(UUID vendorId, Pageable pageable);
    Optional<VendorStaffAccess> findByIdAndDeletedFalse(UUID id);
    Optional<VendorStaffAccess> findByIdAndVendorIdAndDeletedFalse(UUID id, UUID vendorId);
    Optional<VendorStaffAccess> findByVendorIdAndKeycloakUserIdIgnoreCaseAndDeletedFalse(UUID vendorId, String keycloakUserId);
    boolean existsByVendorIdAndKeycloakUserIdIgnoreCase(UUID vendorId, String keycloakUserId);
    boolean existsByVendorIdAndKeycloakUserIdIgnoreCaseAndIdNot(UUID vendorId, String keycloakUserId, UUID id);
    List<VendorStaffAccess> findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalseOrderByVendorIdAsc(String keycloakUserId);
    List<VendorStaffAccess> findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(Instant now);
    Page<VendorStaffAccess> findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(Instant now, Pageable pageable);
}
