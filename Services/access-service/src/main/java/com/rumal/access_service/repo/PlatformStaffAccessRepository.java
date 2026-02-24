package com.rumal.access_service.repo;

import com.rumal.access_service.entity.PlatformStaffAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformStaffAccessRepository extends JpaRepository<PlatformStaffAccess, UUID> {
    List<PlatformStaffAccess> findByDeletedFalseOrderByEmailAsc();
    Page<PlatformStaffAccess> findByDeletedFalse(Pageable pageable);
    List<PlatformStaffAccess> findByDeletedTrueOrderByUpdatedAtDesc();
    Page<PlatformStaffAccess> findByDeletedTrue(Pageable pageable);
    Optional<PlatformStaffAccess> findByIdAndDeletedFalse(UUID id);
    Optional<PlatformStaffAccess> findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalse(String keycloakUserId);
    boolean existsByKeycloakUserIdIgnoreCase(String keycloakUserId);
    boolean existsByKeycloakUserIdIgnoreCaseAndIdNot(String keycloakUserId, UUID id);
    List<PlatformStaffAccess> findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(Instant now);
}
