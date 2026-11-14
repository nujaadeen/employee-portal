package com.baracel.portal.task.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "task")
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "project_id") public Long projectId;
    public int number;
    @Column(name = "task_key") public String taskKey;
    public String title;
    public String description;
    @Enumerated(EnumType.STRING) public TaskStatus status;
    @Enumerated(EnumType.STRING) public Priority priority = Priority.MEDIUM;
    @Column(name = "assignee_id") public UUID assigneeId;
    @Column(name = "reporter_id") public UUID reporterId;
    @Column(name = "due_date") public LocalDate dueDate;
    public String label;
    @Column(name = "sprint_id") public Long sprintId;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
