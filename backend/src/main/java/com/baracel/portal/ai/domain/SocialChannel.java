package com.baracel.portal.ai.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "social_channel")
public class SocialChannel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String code;
    public String name;
    public String type;      // COMPANY or PERSONAL
    public String provider;  // INSTAGRAM, LINKEDIN or FACEBOOK
    public boolean connected;
    @Column(name = "owner_id") public UUID ownerId;
}
