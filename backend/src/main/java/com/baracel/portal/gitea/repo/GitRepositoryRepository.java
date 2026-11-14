package com.baracel.portal.gitea.repo;

import com.baracel.portal.gitea.domain.GitRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitRepositoryRepository extends JpaRepository<GitRepository, Long> {
    List<GitRepository> findByProjectIdIn(Collection<Long> projectIds);
    List<GitRepository> findByProjectId(Long projectId);
    Optional<GitRepository> findByProjectIdAndFullName(Long projectId, String fullName);
}
