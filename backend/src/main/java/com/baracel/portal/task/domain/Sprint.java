package com.baracel.portal.task.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "sprint")
public class Sprint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "project_id") public Long projectId;
    public String name;
    @Column(name = "starts_on") public LocalDate startsOn;
    @Column(name = "ends_on") public LocalDate endsOn;
    public boolean active;
}
