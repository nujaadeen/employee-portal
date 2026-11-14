package com.baracel.portal.leave.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_request")
public class LeaveRequest {
    @Id
    public UUID id;
    @Column(name = "employee_id") public UUID employeeId;
    @Column(name = "leave_type_id") public Long leaveTypeId;
    @Column(name = "from_date") public LocalDate fromDate;
    @Column(name = "to_date") public LocalDate toDate;
    public int days;
    public String reason;
    @Enumerated(EnumType.STRING) public LeaveStatus status;
    @Column(name = "decided_by") public UUID decidedBy;
    @Column(name = "decided_at") public Instant decidedAt;
    public String note;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
}
