package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.AiDraft;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiDraftRepository extends JpaRepository<AiDraft, UUID> {
    List<AiDraft> findByMessageIdIn(Collection<UUID> messageIds);
}
