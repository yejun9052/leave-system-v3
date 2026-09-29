package com.company.leave.calendar.dto;

import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

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
