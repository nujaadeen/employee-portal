package com.baracel.portal.task.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_comment")
public class TaskComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "task_id") public Long taskId;
    @Column(name = "author_id") public UUID authorId;
    public String body;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
