package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_oauth_state")
public class SocialOAuthState {
    @Id public String state;
    @Column(name = "user_id") public UUID userId;
    public String purpose;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "used_at") public Instant usedAt;
}
