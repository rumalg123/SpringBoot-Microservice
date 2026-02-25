package com.rumal.personalization_service.repository;

import com.rumal.personalization_service.model.UserAffinity;
import com.rumal.personalization_service.model.UserAffinityId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserAffinityRepository extends JpaRepository<UserAffinity, UserAffinityId> {

    List<UserAffinity> findByUserIdOrderByScoreDesc(UUID userId, Pageable pageable);

    List<UserAffinity> findByUserIdAndAffinityTypeOrderByScoreDesc(UUID userId, String affinityType, Pageable pageable);
}
