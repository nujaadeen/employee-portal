package com.baracel.portal.gitea.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pr_review_request")
public class PrReviewRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String repo;
    @Column(name = "pr_number") public int prNumber;
    @Column(name = "pr_title") public String prTitle;
    @Column(name = "pr_url") public String prUrl;
    @Column(name = "reviewer_username") public String reviewerUsername;
    public String state;
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();
}
