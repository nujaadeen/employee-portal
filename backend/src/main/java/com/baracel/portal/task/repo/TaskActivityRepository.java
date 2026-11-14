package com.baracel.portal.task.repo;

import com.baracel.portal.task.domain.TaskActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {
    List<TaskActivity> findByTaskIdOrderByCreatedAtDescIdDesc(Long taskId);
}
