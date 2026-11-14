package com.baracel.portal.ai.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "knowledge_collection")
public class KnowledgeCollection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String code;
    public String name;
    @Column(name = "doc_count") public int docCount;
    public String permission;
    @Column(name = "sort_order") public int sortOrder;
}
