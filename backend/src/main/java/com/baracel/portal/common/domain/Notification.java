package com.baracel.portal.common.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "employee_id") public UUID employeeId;
    public String type;
    public String title;
    public String body;
    public String link;
    @Column(name = "read_at") public Instant readAt;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
