package com.baracel.portal.task.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_activity")
public class TaskActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "task_id") public Long taskId;
    @Column(name = "actor_id") public UUID actorId;
    @Column(name = "actor_name") public String actorName;
    public String kind;
    public String message;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
