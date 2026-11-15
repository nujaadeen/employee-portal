package com.baracel.portal.ai.repo;

import com.baracel.portal.ai.domain.KnowledgeActivity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeActivityRepository extends JpaRepository<KnowledgeActivity, Long> {
    List<KnowledgeActivity> findTop20ByOrderByCreatedAtDesc();
    Optional<KnowledgeActivity> findByJobId(String jobId);
}
