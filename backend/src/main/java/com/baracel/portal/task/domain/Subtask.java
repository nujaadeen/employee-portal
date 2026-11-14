package com.baracel.portal.task.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "subtask")
public class Subtask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "task_id") public Long taskId;
    public String title;
    public boolean done;
    public int position;
}
