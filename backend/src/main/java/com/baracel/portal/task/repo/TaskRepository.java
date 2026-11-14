package com.baracel.portal.task.repo;

import com.baracel.portal.task.domain.Task;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByTaskKeyIgnoreCase(String taskKey);
    List<Task> findByTaskKeyIn(Collection<String> keys);
    List<Task> findBySprintIdOrderByNumberAsc(Long sprintId);
    List<Task> findByProjectIdAndSprintIdIsNullOrderByNumberAsc(Long projectId);
    List<Task> findByAssigneeId(UUID assigneeId);
}
