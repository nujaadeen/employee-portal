package com.baracel.portal.task.repo;

import com.baracel.portal.task.domain.Project;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByKeyIgnoreCase(String key);

    /** Locks the row so the per-project task number cannot be handed out twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> lockById(Long id);
}
