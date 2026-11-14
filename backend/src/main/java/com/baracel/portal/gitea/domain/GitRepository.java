package com.baracel.portal.gitea.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "project_repository")
public class GitRepository {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "project_id") public Long projectId;
    @Column(name = "full_name") public String fullName;
    @Column(name = "default_branch") public String defaultBranch = "main";
    @Column(name = "html_url") public String htmlUrl;
}
