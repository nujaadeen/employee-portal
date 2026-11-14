package com.baracel.portal.employee.repo;

import com.baracel.portal.employee.domain.Employee;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
    Optional<Employee> findByKeycloakId(String keycloakId);
    Optional<Employee> findByEmailIgnoreCase(String email);
    Optional<Employee> findFirstByGiteaUsernameIgnoreCase(String giteaUsername);
    List<Employee> findByManagerIdOrderByFirstNameAsc(UUID managerId);
    List<Employee> findByDepartment(String department);

    @Query("select e.status, count(e) from Employee e group by e.status")
    List<Object[]> countByStatus();

    @Query(value = "select nextval('employee_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
