package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_message")
public class AiMessage {
    @Id public UUID id;
    @Column(name = "conversation_id") public UUID conversationId;
    public String role;
    public String content;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
