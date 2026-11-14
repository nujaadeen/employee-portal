package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.AiMessageSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageSourceRepository extends JpaRepository<AiMessageSource, Long> {
    List<AiMessageSource> findByMessageIdIn(Collection<UUID> messageIds);
}
