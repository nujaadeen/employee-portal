package com.baracel.portal.task.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "project")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String key;
    public String name;
    public String description;
    @Column(name = "task_seq") public int taskSeq;
}
