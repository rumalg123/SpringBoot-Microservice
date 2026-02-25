package com.rumal.personalization_service.repository;

import com.rumal.personalization_service.model.AnonymousSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AnonymousSessionRepository extends JpaRepository<AnonymousSession, String> {

    Optional<AnonymousSession> findBySessionIdAndMergedAtIsNull(String sessionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AnonymousSession s WHERE s.mergedAt IS NULL AND s.lastActivityAt < :before")
    int deleteStaleUnmergedSessions(Instant before);
}
