package com.baracel.portal.task.repo;

import com.baracel.portal.task.domain.ProjectMember;
import com.baracel.portal.task.domain.ProjectMemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {
    List<ProjectMember> findByIdEmployeeId(UUID employeeId);
    List<ProjectMember> findByIdProjectId(Long projectId);
    boolean existsByIdProjectIdAndIdEmployeeId(Long projectId, UUID employeeId);
}
