package com.baracel.portal.gitea.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_git_link")
public class TaskGitLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "task_id") public Long taskId;
    public String repo;
    public String branch;
    @Column(name = "pr_number") public Integer prNumber;
    @Column(name = "pr_title") public String prTitle;
    @Enumerated(EnumType.STRING) @Column(name = "pr_state") public PrState prState;
    @Column(name = "pr_url") public String prUrl;
    @Enumerated(EnumType.STRING) @Column(name = "checks_status") public ChecksStatus checksStatus;
    public int approvals;
    @Column(name = "last_sha") public String lastSha;
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();
}
