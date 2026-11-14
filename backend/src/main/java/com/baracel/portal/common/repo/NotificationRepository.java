package com.baracel.portal.common.repo;

import com.baracel.portal.common.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    long countByEmployeeIdAndReadAtIsNull(UUID employeeId);
}
