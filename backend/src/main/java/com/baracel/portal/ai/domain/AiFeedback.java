package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_feedback")
public class AiFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "message_id") public UUID messageId;
    @Column(name = "user_id") public UUID userId;
    public String rating;   // UP or DOWN
    public String comment;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
