package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.AiConversation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
}
