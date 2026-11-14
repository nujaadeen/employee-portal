package com.baracel.portal.leave.repo;

import com.baracel.portal.leave.domain.PublicHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    List<PublicHoliday> findByDateBetween(LocalDate from, LocalDate to);
}
