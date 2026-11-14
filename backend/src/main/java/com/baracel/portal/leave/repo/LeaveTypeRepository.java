package com.baracel.portal.leave.repo;

import com.baracel.portal.leave.domain.LeaveType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Optional<LeaveType> findByCodeIgnoreCase(String code);
    List<LeaveType> findAllByOrderBySortOrderAsc();
}
