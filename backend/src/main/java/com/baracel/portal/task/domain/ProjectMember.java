package com.baracel.portal.task.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "project_member")
public class ProjectMember {
    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "projectId", column = @Column(name = "project_id")),
            @AttributeOverride(name = "employeeId", column = @Column(name = "employee_id"))})
    public ProjectMemberId id;

    public ProjectMember() {}
    public ProjectMember(ProjectMemberId id) { this.id = id; }
}
