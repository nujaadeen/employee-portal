package com.baracel.portal.leave.service;

import com.baracel.portal.leave.domain.PublicHoliday;
import com.baracel.portal.leave.repo.PublicHolidayRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Working days exclude the weekend (configurable, default Saturday and Sunday) and public holidays. */
@Component
public class WorkingDays {
    private final Set<DayOfWeek> weekend;
    private final PublicHolidayRepository holidays;

    public WorkingDays(@Value("${portal.leave.weekend-days:SATURDAY,SUNDAY}") String weekendDays, PublicHolidayRepository holidays) {
        this.weekend = Arrays.stream(weekendDays.split(",")).map(String::trim).map(DayOfWeek::valueOf).collect(Collectors.toSet());
        this.holidays = holidays;
    }

    public int count(LocalDate from, LocalDate to) {
        Set<LocalDate> off = holidays.findByDateBetween(from, to).stream().map(h -> h.date).collect(Collectors.toSet());
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1))
            if (!weekend.contains(d.getDayOfWeek()) && !off.contains(d)) n++;
        return n;
    }

    /** First working day after the given date (when the person is back). */
    public LocalDate nextWorkingDay(LocalDate date) {
        LocalDate d = date.plusDays(1);
        for (int i = 0; i < 30; i++, d = d.plusDays(1)) {
            LocalDate day = d;
            if (!weekend.contains(day.getDayOfWeek()) && holidays.findByDateBetween(day, day).isEmpty()) return day;
        }
        return d;
    }
}
