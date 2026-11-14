package com.baracel.portal.access.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "custom_role")
public class CustomRole {
    @Id public String name;
    public String description;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
