package com.baracel.portal.leave.repo;

import com.baracel.portal.leave.domain.LeaveRequest;
import com.baracel.portal.leave.domain.LeaveStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {
    List<LeaveRequest> findByEmployeeIdOrderByFromDateDesc(UUID employeeId);
    List<LeaveRequest> findTop5ByEmployeeIdAndStatusInOrderByFromDateDesc(UUID employeeId, Collection<LeaveStatus> statuses);

    @Query("select count(r) > 0 from LeaveRequest r where r.employeeId = :employeeId and r.status in :statuses "
            + "and r.fromDate <= :to and r.toDate >= :from")
    boolean existsOverlap(UUID employeeId, LocalDate from, LocalDate to, Collection<LeaveStatus> statuses);

    @Query("select r from LeaveRequest r where r.employeeId in :ids and r.status in :statuses "
            + "and r.fromDate <= :to and r.toDate >= :from order by r.fromDate")
    List<LeaveRequest> findOverlapping(Collection<UUID> ids, LocalDate from, LocalDate to, Collection<LeaveStatus> statuses);

    @Query("select r from LeaveRequest r where r.status = :status and r.fromDate <= :date and r.toDate >= :date")
    List<LeaveRequest> findCovering(LocalDate date, LeaveStatus status);

    @Query("select coalesce(sum(r.days), 0) from LeaveRequest r where r.employeeId = :employeeId and r.leaveTypeId = :typeId "
            + "and r.status = 'PENDING' and r.fromDate >= :yearStart and r.fromDate <= :yearEnd")
    long pendingDays(UUID employeeId, Long typeId, LocalDate yearStart, LocalDate yearEnd);
}
