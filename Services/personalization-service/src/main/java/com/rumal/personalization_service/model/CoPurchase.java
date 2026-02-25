package com.rumal.personalization_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "co_purchase", indexes = {
        @Index(name = "idx_co_purchase_product_b", columnList = "product_id_b"),
        @Index(name = "idx_co_purchase_last_computed", columnList = "last_computed_at")
})
@IdClass(CoPurchaseId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoPurchase {

    @Id
    private UUID productIdA;

    @Id
    private UUID productIdB;

    @Column(nullable = false)
    private int coPurchaseCount;

    @Column(nullable = false)
    private Instant lastComputedAt;
}
