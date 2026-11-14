package com.baracel.portal.leave.repo;

import com.baracel.portal.leave.domain.LeaveBalance;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    List<LeaveBalance> findByEmployeeIdAndYear(UUID employeeId, int year);
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(UUID employeeId, Long leaveTypeId, int year);
    List<LeaveBalance> findByEmployeeIdInAndLeaveTypeIdAndYear(Collection<UUID> ids, Long leaveTypeId, int year);
}
