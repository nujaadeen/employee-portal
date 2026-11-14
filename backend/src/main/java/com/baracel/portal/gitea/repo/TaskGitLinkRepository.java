package com.baracel.portal.gitea.repo;

import com.baracel.portal.gitea.domain.TaskGitLink;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskGitLinkRepository extends JpaRepository<TaskGitLink, Long> {
    Optional<TaskGitLink> findByTaskIdAndRepoAndBranch(Long taskId, String repo, String branch);
    List<TaskGitLink> findByTaskIdIn(Collection<Long> taskIds);
    List<TaskGitLink> findByTaskIdOrderByUpdatedAtDesc(Long taskId);
    List<TaskGitLink> findByRepoAndLastSha(String repo, String lastSha);
}
