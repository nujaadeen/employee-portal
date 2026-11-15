package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_activity")
public class KnowledgeActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "job_id") public String jobId;
    public String collection;
    public String kind;
    public String label;
    @Column(name = "user_id") public UUID userId;
    @Column(name = "user_name") public String userName;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
