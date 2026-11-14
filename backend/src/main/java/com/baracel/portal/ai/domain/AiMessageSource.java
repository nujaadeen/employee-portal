package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ai_message_source")
public class AiMessageSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "message_id") public UUID messageId;
    public String title;
    public String collection;
}
