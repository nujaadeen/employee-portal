package com.baracel.portal.employee.service;

import com.baracel.portal.employee.domain.Employee;
import com.baracel.portal.employee.domain.EmployeeStatus;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only snapshot of an employee. Other modules get this instead of the JPA entity. */
public record EmployeeView(UUID id, String keycloakId, String code, String firstName, String lastName, String email,
                           String jobTitle, String department, UUID managerId, EmployeeStatus status, String primaryRole,
                           LocalDate joinedOn, String phone, String location, String giteaUsername) {
    public static EmployeeView of(Employee e) {
        return new EmployeeView(e.id, e.keycloakId, e.code, e.firstName, e.lastName, e.email, e.jobTitle, e.department,
                e.managerId, e.status, e.primaryRole, e.joinedOn, e.phone, e.location, e.giteaUsername);
    }

    public String fullName() { return (firstName + " " + (lastName == null ? "" : lastName)).trim(); }

    public String initials() {
        String a = firstName == null || firstName.isEmpty() ? "" : firstName.substring(0, 1);
        String b = lastName == null || lastName.isEmpty() ? "" : lastName.substring(0, 1);
        return (a + b).toUpperCase();
    }
}
