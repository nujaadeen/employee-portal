package com.baracel.portal.common.repo;

import com.baracel.portal.common.domain.Announcement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findTop20ByOrderByCreatedAtDesc();
}
