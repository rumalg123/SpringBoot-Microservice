package com.rumal.personalization_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_similarity", indexes = {
        @Index(name = "idx_product_similarity_last_computed", columnList = "last_computed_at")
})
@IdClass(ProductSimilarityId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSimilarity {

    @Id
    private UUID productId;

    @Id
    private UUID similarProductId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private Instant lastComputedAt;
}
