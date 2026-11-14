package com.baracel.portal.leave.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "leave_balance")
public class LeaveBalance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "employee_id") public UUID employeeId;
    @Column(name = "leave_type_id") public Long leaveTypeId;
    public int year;
    public int entitled;
    public int used;
}
