package com.baracel.portal.task.repo;

import com.baracel.portal.task.domain.Sprint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintRepository extends JpaRepository<Sprint, Long> {
    Optional<Sprint> findFirstByProjectIdAndActiveTrue(Long projectId);
    List<Sprint> findByProjectIdOrderByStartsOnDesc(Long projectId);
}
