package com.baracel.portal.employee.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee")
public class Employee {
    @Id
    public UUID id;
    @Column(name = "keycloak_id") public String keycloakId;
    public String code;
    @Column(name = "first_name") public String firstName;
    @Column(name = "last_name") public String lastName = "";
    public String email;
    @Column(name = "job_title") public String jobTitle;
    public String department;
    @Column(name = "manager_id") public UUID managerId;
    @Enumerated(EnumType.STRING) public EmployeeStatus status;
    @Column(name = "primary_role") public String primaryRole;
    @Column(name = "joined_on") public LocalDate joinedOn;
    public String phone;
    public String location;
    @Column(name = "gitea_username") public String giteaUsername;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public String fullName() { return (firstName + " " + (lastName == null ? "" : lastName)).trim(); }
}
