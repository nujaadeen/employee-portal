package com.baracel.portal.gitea.repo;

import com.baracel.portal.gitea.domain.TaskCommit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommitRepository extends JpaRepository<TaskCommit, Long> {
    boolean existsByTaskIdAndRepoAndSha(Long taskId, String repo, String sha);
    List<TaskCommit> findTop10ByTaskIdOrderByCommittedAtDesc(Long taskId);
    long countByTaskId(Long taskId);
}
