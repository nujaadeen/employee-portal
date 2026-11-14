package com.baracel.portal.access.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "permission")
public class Permission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String module;
    public String action;
    public String label;
    public String hint;
    @Column(name = "ui_group") public String uiGroup;
    @Column(name = "ui_row") public String uiRow;
    @Column(name = "ui_column") public String uiColumn;
    @Column(name = "sort_order") public int sortOrder;

    public String key() { return module + ":" + action; }
}
