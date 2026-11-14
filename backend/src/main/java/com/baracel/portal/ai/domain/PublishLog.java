package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publish_log")
public class PublishLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "draft_id") public UUID draftId;
    @Column(name = "channel_code") public String channelCode;
    @Column(name = "user_id") public UUID userId;
    @Column(name = "user_name") public String userName;
    public String status;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
