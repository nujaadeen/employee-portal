package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.AiMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
