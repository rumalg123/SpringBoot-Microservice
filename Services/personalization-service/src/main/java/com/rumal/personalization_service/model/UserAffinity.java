package com.rumal.personalization_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_affinity")
@IdClass(UserAffinityId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAffinity {

    @Id
    private UUID userId;

    @Id
    @Column(length = 16)
    private String affinityType;

    @Id
    @Column(length = 128)
    private String affinityKey;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private int eventCount;

    @Column(nullable = false)
    private Instant lastUpdatedAt;
}
