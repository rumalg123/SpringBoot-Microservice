package com.rumal.admin_service.repo;

import com.rumal.admin_service.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByFlagKey(String flagKey);

    List<FeatureFlag> findByEnabled(boolean enabled);

    boolean existsByFlagKey(String flagKey);
}
