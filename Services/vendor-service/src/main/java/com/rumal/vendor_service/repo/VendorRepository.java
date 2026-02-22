package com.rumal.vendor_service.repo;

import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    Optional<Vendor> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    List<Vendor> findByDeletedFalseOrderByNameAsc();
    List<Vendor> findByDeletedTrueOrderByUpdatedAtDesc();
    List<Vendor> findByDeletedFalseAndActiveTrueAndStatusOrderByNameAsc(VendorStatus status);
}
