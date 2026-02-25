package com.rumal.personalization_service.repository;

import com.rumal.personalization_service.model.CoPurchase;
import com.rumal.personalization_service.model.CoPurchaseId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CoPurchaseRepository extends JpaRepository<CoPurchase, CoPurchaseId> {

    @Query("""
            SELECT c FROM CoPurchase c
            WHERE c.productIdA = :productId OR c.productIdB = :productId
            ORDER BY c.coPurchaseCount DESC
            """)
    List<CoPurchase> findByProductId(UUID productId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM CoPurchase c WHERE c.lastComputedAt < :before")
    int deleteStaleEntries(Instant before);
}
