package com.baracel.portal.gitea.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_commit")
public class TaskCommit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "task_id") public Long taskId;
    public String repo;
    public String sha;
    public String message;
    @Column(name = "author_name") public String authorName;
    @Column(name = "committed_at") public Instant committedAt;
    public String url;
}
