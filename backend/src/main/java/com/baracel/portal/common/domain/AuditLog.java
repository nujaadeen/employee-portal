package com.baracel.portal.common.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "actor_id") public UUID actorId;
    @Column(name = "actor_name") public String actorName;
    public String action;
    @Column(name = "target_type") public String targetType;
    @Column(name = "target_id") public String targetId;
    public String detail;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
