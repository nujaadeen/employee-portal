package com.baracel.portal.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baracel.portal.leave.domain.PublicHoliday;
import com.baracel.portal.leave.repo.PublicHolidayRepository;
import com.baracel.portal.leave.service.WorkingDays;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkingDaysTest {
    private PublicHolidayRepository holidays(LocalDate... days) {
        PublicHolidayRepository repo = mock(PublicHolidayRepository.class);
        List<PublicHoliday> list = java.util.Arrays.stream(days).map(d -> { PublicHoliday h = new PublicHoliday(); h.date = d; h.name = "h"; return h; }).toList();
        when(repo.findByDateBetween(any(), any())).thenAnswer(i -> list.stream()
                .filter(h -> !h.date.isBefore(i.getArgument(0)) && !h.date.isAfter(i.getArgument(1))).toList());
        return repo;
    }

    @Test
    void weekendsAreSkipped() {
        WorkingDays w = new WorkingDays("SATURDAY,SUNDAY", holidays());
        assertEquals(5, w.count(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27)));
        assertEquals(5, w.count(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29)));
        assertEquals(0, w.count(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 22)));
        assertEquals(1, w.count(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19)));
    }

    @Test
    void publicHolidaysAreSkipped() {
        WorkingDays w = new WorkingDays("SATURDAY,SUNDAY", holidays(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 3)));
        assertEquals(2, w.count(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4)));
        // a holiday on a weekend is not counted twice
        assertEquals(5, new WorkingDays("SATURDAY,SUNDAY", holidays(LocalDate.of(2026, 11, 28))).count(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29)));
    }

    @Test
    void weekendIsConfigurable() {
        WorkingDays friSat = new WorkingDays("FRIDAY,SATURDAY", holidays());
        assertEquals(5, friSat.count(LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 28)));   // Sun..Sat minus Fri and Sat
        assertEquals(LocalDate.of(2026, 11, 22), friSat.nextWorkingDay(LocalDate.of(2026, 11, 20)));
    }

    @Test
    void nextWorkingDaySkipsTheWeekendAndHolidays() {
        WorkingDays w = new WorkingDays("SATURDAY,SUNDAY", holidays(LocalDate.of(2026, 11, 16)));
        assertEquals(LocalDate.of(2026, 11, 17), w.nextWorkingDay(LocalDate.of(2026, 11, 13)));
        assertEquals(LocalDate.of(2026, 11, 18), w.nextWorkingDay(LocalDate.of(2026, 11, 17)));
    }
}
