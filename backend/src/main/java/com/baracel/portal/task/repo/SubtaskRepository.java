package com.baracel.portal.task.repo;

import com.baracel.portal.task.domain.Subtask;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubtaskRepository extends JpaRepository<Subtask, Long> {
    List<Subtask> findByTaskIdOrderByPositionAscIdAsc(Long taskId);
    List<Subtask> findByTaskIdIn(Collection<Long> taskIds);
}
