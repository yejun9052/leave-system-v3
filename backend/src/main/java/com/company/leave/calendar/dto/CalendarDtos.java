package com.company.leave.calendar.dto;

import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import com.company.leave.leave.dto.LeaveRequestDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class CalendarDtos {

    private CalendarDtos() {
    }

    /** FullCalendar 이벤트 형식. allDay 이벤트의 end 는 배타적(다음 날)으로 내려준다. */
    public record EventResponse(
            String id,
            String title,
            LocalDate start,
            LocalDate end,
            boolean allDay,
            CalendarEventScope scope,
            CalendarEventSource source,
            String colorHex,
            Long departmentId,
            boolean editable) {

        public static EventResponse fromEvent(CalendarEvent e, boolean editable) {
            return new EventResponse(
                    "E" + e.getId(),
                    e.getTitle(),
                    e.getStartDate(),
                    e.isAllDay() ? e.getEndDate().plusDays(1) : e.getEndDate(),
                    e.isAllDay(),
                    e.getScope(),
                    e.getSource(),
                    e.getColorHex(),
                    e.getDepartmentId(),
                    editable);
        }

        public static EventResponse holiday(Long id, String name, LocalDate date) {
            return new EventResponse(
                    "H" + id,
                    name,
                    date,
                    date.plusDays(1),
                    true,
                    CalendarEventScope.COMPANY,
                    CalendarEventSource.HOLIDAY,
                    "#ef4444",
                    null,
                    false);
        }
    }

    /**
     * 캘린더 날짜 상세(휴가·일정이 몰린 날 확인용).
     *
     * @param holidayName 공휴일이면 이름(여러 개면 ", " 로 연결), 아니면 null
     * @param leaves      그날에 걸친 휴가(부서·이름 순, 사유 없음)
     * @param events      관리자·팀장이 등록한 일정(휴가·공휴일 제외)
     */
    public record DayDetail(
            LocalDate date,
            String holidayName,
            List<LeaveRequestDtos.DayLeave> leaves,
            List<DayEvent> events) {
    }

    /** 날짜 상세의 등록 일정. end 는 포함(마지막 날). */
    public record DayEvent(
            String id,
            String title,
            LocalDate start,
            LocalDate end,
            CalendarEventScope scope,
            String colorHex) {

        public static DayEvent from(CalendarEvent e) {
            return new DayEvent("E" + e.getId(), e.getTitle(), e.getStartDate(), e.getEndDate(),
                    e.getScope(), e.getColorHex());
        }
    }

    public record CreateEvent(
            @NotBlank String title,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            boolean allDay,
            @NotNull CalendarEventScope scope,
            Long departmentId,
            String colorHex) {
    }
}
