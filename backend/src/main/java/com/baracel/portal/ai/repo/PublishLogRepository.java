package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.PublishLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishLogRepository extends JpaRepository<PublishLog, Long> {
    List<PublishLog> findByDraftId(UUID draftId);
}
