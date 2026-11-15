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
    /** IN_PROGRESS, PUBLISHED, FAILED, SIMULATED or UNKNOWN. */
    public String status;
    /** FAILED only: TOKEN_EXPIRED, RATE_LIMITED, REJECTED or UNKNOWN_ERROR. */
    public String reason;
    @Column(name = "post_id") public String postId;
    @Column(name = "post_url") public String postUrl;
    @Column(name = "connection_id") public java.util.UUID connectionId;
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
