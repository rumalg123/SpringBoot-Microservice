package com.rumal.personalization_service.model;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CoPurchaseId implements Serializable {
    private UUID productIdA;
    private UUID productIdB;
}
