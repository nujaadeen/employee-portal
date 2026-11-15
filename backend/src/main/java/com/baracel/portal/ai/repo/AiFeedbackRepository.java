package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.AiFeedback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
    Optional<AiFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);
}
