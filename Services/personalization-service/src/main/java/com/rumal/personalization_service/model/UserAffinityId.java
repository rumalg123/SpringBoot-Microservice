package com.rumal.personalization_service.model;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserAffinityId implements Serializable {
    private UUID userId;
    private String affinityType;
    private String affinityKey;
}
