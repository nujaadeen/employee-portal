package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.PublishLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishLogRepository extends JpaRepository<PublishLog, Long> {
    List<PublishLog> findByDraftId(UUID draftId);
    java.util.Optional<PublishLog> findByDraftIdAndChannelCode(UUID draftId, String channelCode);
    org.springframework.data.domain.Page<PublishLog> findByUserIdOrderByCreatedAtDesc(UUID userId, org.springframework.data.domain.Pageable page);
    /** Takes a FAILED row back for a new attempt. Returns 1 for exactly one caller, so two clicks cannot both retry. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update PublishLog p set p.status = 'IN_PROGRESS', p.reason = null where p.id = :id and p.status = 'FAILED'")
    int claimFailed(Long id);
}
