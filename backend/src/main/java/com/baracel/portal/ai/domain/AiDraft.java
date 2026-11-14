package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ai_draft")
public class AiDraft {
    @Id public UUID id;
    @Column(name = "message_id") public UUID messageId;
    public String kind;
    public String headline;
    public String caption;
    /** Space separated. */
    public String hashtags = "";
    /** Comma separated channel codes. */
    @Column(name = "suggested_channels") public String suggestedChannels = "";
}
