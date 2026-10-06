package com.company.leave.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;

/**
 * 공휴일. 연차 일수 계산 시 제외되며 캘린더에도 표시된다.
 */
@Entity
@Table(name = "holidays")
@Getter
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate date;

    @Column(nullable = false, length = 60)
    private String name;

    protected Holiday() {
    }

    public Holiday(LocalDate date, String name) {
        this.date = date;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }
}
