package com.rumal.vendor_service.repo;

import com.rumal.vendor_service.entity.VendorUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorUserRepository extends JpaRepository<VendorUser, UUID> {
    @EntityGraph(attributePaths = {"vendor"})
    List<VendorUser> findByVendorIdOrderByRoleAscCreatedAtAsc(UUID vendorId);
    @EntityGraph(attributePaths = {"vendor"})
    Page<VendorUser> findByVendorIdOrderByRoleAscCreatedAtAsc(UUID vendorId, Pageable pageable);
    boolean existsByVendorIdAndKeycloakUserId(UUID vendorId, String keycloakUserId);
    Optional<VendorUser> findByIdAndVendorId(UUID id, UUID vendorId);

    @Query("SELECT vu FROM VendorUser vu JOIN FETCH vu.vendor v " +
            "WHERE LOWER(vu.keycloakUserId) = LOWER(:keycloakUserId) " +
            "AND vu.active = true AND v.deleted = false AND v.active = true " +
            "ORDER BY vu.createdAt ASC")
    List<VendorUser> findAccessibleMembershipsByKeycloakUser(@Param("keycloakUserId") String keycloakUserId);
}
