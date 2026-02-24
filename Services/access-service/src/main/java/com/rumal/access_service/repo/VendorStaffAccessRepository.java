package com.rumal.access_service.repo;

import com.rumal.access_service.entity.VendorStaffAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorStaffAccessRepository extends JpaRepository<VendorStaffAccess, UUID> {
    List<VendorStaffAccess> findByDeletedFalseOrderByVendorIdAscEmailAsc();
    List<VendorStaffAccess> findByDeletedTrueOrderByUpdatedAtDesc();
    List<VendorStaffAccess> findByVendorIdAndDeletedFalseOrderByEmailAsc(UUID vendorId);
    Optional<VendorStaffAccess> findByIdAndDeletedFalse(UUID id);
    Optional<VendorStaffAccess> findByIdAndVendorIdAndDeletedFalse(UUID id, UUID vendorId);
    Optional<VendorStaffAccess> findByVendorIdAndKeycloakUserIdIgnoreCaseAndDeletedFalse(UUID vendorId, String keycloakUserId);
    boolean existsByVendorIdAndKeycloakUserIdIgnoreCase(UUID vendorId, String keycloakUserId);
    boolean existsByVendorIdAndKeycloakUserIdIgnoreCaseAndIdNot(UUID vendorId, String keycloakUserId, UUID id);
    List<VendorStaffAccess> findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalseOrderByVendorIdAsc(String keycloakUserId);
    List<VendorStaffAccess> findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(Instant now);
}
