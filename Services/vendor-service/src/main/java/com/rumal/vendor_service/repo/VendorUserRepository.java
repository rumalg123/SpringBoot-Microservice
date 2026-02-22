package com.rumal.vendor_service.repo;

import com.rumal.vendor_service.entity.VendorUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorUserRepository extends JpaRepository<VendorUser, UUID> {
    List<VendorUser> findByVendorIdOrderByRoleAscCreatedAtAsc(UUID vendorId);
    boolean existsByVendorIdAndKeycloakUserId(UUID vendorId, String keycloakUserId);
    Optional<VendorUser> findByIdAndVendorId(UUID id, UUID vendorId);
    List<VendorUser> findByKeycloakUserIdIgnoreCaseAndActiveTrueAndVendorDeletedFalseAndVendorActiveTrueOrderByCreatedAtAsc(String keycloakUserId);
}
