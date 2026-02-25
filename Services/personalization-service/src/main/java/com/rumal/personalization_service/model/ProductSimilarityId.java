package com.rumal.personalization_service.model;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductSimilarityId implements Serializable {
    private UUID productId;
    private UUID similarProductId;
}
