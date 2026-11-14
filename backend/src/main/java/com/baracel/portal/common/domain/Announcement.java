package com.baracel.portal.common.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcement")
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String title;
    public String body;
    @Column(name = "posted_by") public String postedBy;
    @Column(name = "author_id") public UUID authorId;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
