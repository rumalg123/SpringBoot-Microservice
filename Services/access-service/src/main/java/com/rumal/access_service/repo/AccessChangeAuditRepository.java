package com.rumal.access_service.repo;

import com.rumal.access_service.entity.AccessChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AccessChangeAuditRepository extends JpaRepository<AccessChangeAudit, UUID>, JpaSpecificationExecutor<AccessChangeAudit> {
}
