package com.baracel.portal.leave.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "leave_type")
public class LeaveType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String code;
    public String name;
    /** null means no allowance limit (Unpaid). */
    @Column(name = "default_days") public Integer defaultDays;
    @Column(name = "sort_order") public int sortOrder;
}
