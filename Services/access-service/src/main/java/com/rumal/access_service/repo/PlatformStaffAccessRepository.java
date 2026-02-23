package com.rumal.access_service.repo;

import com.rumal.access_service.entity.PlatformStaffAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformStaffAccessRepository extends JpaRepository<PlatformStaffAccess, UUID> {
    List<PlatformStaffAccess> findByDeletedFalseOrderByEmailAsc();
    List<PlatformStaffAccess> findByDeletedTrueOrderByUpdatedAtDesc();
    Optional<PlatformStaffAccess> findByIdAndDeletedFalse(UUID id);
    Optional<PlatformStaffAccess> findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalse(String keycloakUserId);
    boolean existsByKeycloakUserIdIgnoreCase(String keycloakUserId);
    boolean existsByKeycloakUserIdIgnoreCaseAndIdNot(String keycloakUserId, UUID id);
}
