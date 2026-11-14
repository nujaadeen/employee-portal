package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversation")
public class AiConversation {
    @Id public UUID id;
    @Column(name = "owner_id") public UUID ownerId;
    public String title;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();
}
