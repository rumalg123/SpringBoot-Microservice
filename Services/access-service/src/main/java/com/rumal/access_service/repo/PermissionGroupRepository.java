package com.rumal.access_service.repo;

import com.rumal.access_service.entity.PermissionGroup;
import com.rumal.access_service.entity.PermissionGroupScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, UUID> {
    List<PermissionGroup> findByScopeOrderByNameAsc(PermissionGroupScope scope);
    List<PermissionGroup> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCaseAndScope(String name, PermissionGroupScope scope);
    boolean existsByNameIgnoreCaseAndScopeAndIdNot(String name, PermissionGroupScope scope, UUID id);
}
