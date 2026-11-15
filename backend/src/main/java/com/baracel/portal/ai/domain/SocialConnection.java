package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A linked social account. The token columns hold AES-GCM ciphertext and are never put in a DTO. */
@Entity
@Table(name = "social_connection")
public class SocialConnection {
    @Id public UUID id;
    @Column(name = "owner_id") public UUID ownerId;           // null = company connection
    public String provider;
    @Column(name = "account_urn") public String accountUrn;
    @Column(name = "display_name") public String displayName;
    @Column(name = "access_token_enc") public String accessTokenEnc;
    @Column(name = "refresh_token_enc") public String refreshTokenEnc;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "refresh_expires_at") public Instant refreshExpiresAt;
    public String scopes = "";
    public String status = "CONNECTED";                       // CONNECTED, EXPIRED or REVOKED
    @Column(name = "created_by") public UUID createdBy;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();

    @Override public String toString() { return "SocialConnection[" + id + " " + provider + " " + status + "]"; }   // never the tokens
}
