package com.baracel.portal.common.repo;

import com.baracel.portal.common.domain.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByEmployeeId(UUID employeeId, Pageable pageable);
    Page<Notification> findByEmployeeIdAndReadAtIsNull(UUID employeeId, Pageable pageable);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.employeeId = :employeeId and n.readAt is null")
    int markAllRead(UUID employeeId, java.time.Instant now);
    long countByEmployeeIdAndReadAtIsNull(UUID employeeId);
}
