package com.baracel.portal.leave.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "public_holiday")
public class PublicHoliday {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "holiday_date") public LocalDate date;
    public String name;
}
