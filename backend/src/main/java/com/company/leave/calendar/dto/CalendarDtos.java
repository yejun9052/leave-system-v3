package com.company.leave.calendar.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import com.company.leave.common.dto.DateSpan;
import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import com.company.leave.leave.dto.LeaveRequestDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class CalendarDtos {

    private CalendarDtos() {
    }

    /**
     * FullCalendar 이벤트 형식. allDay 이벤트의 end 는 배타적(다음 날)으로 내려준다.
     *
     * @param employeeId 휴가 일정이면 휴가를 낸 직원(캘린더 "개인" 탭에서 내 휴가를 고를 때 씀), 그 외 null
     */
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
            Long employeeId,
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
                    e.getEmployeeId(),
                    editable);
        }

        /** 휴가 종류(병가·경조사)를 가린 같은 일정. 제목은 "이름 - 휴가", 색은 중립 색. */
        public EventResponse withHiddenType(String hiddenTitle) {
            return new EventResponse(id, hiddenTitle, start, end, allDay, scope, source,
                    LeaveRequestDtos.HIDDEN_TYPE_COLOR, departmentId, employeeId, editable);
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

    /**
     * 일정 등록 화면의 범위 선택지. 전사면 departmentId 가 null.
     *
     * @param label 화면 표시용("전체 일정", "{부서명} 일정")
     */
    public record EventScopeOption(CalendarEventScope scope, Long departmentId, String label) {
    }

    /**
     * 달력에서 여러 날짜를 골라 일정 추가: 이어진 묶음(spans, 달력 날짜가 바로 이어진 것끼리)마다 일정 하나.
     * 하나라도 걸리면 아무것도 저장하지 않는다. 다른 칸은 {@link CreateEvent}.
     */
    public record CreateEvents(
            @NotBlank @Size(max = 200) String title,
            @NotEmpty @Size(max = DateSpan.MAX_SPANS) List<@Valid @NotNull DateSpan> spans,
            boolean allDay,
            @NotNull CalendarEventScope scope,
            Long departmentId,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "색은 #RRGGBB 형식이어야 합니다.") String colorHex) {

        public CreateEvent toCreate(DateSpan span) {
            return new CreateEvent(title, span.startDate(), span.endDate(), allDay, scope, departmentId, colorHex);
        }
    }

    public record CreateEvent(
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            boolean allDay,
            @NotNull CalendarEventScope scope,
            Long departmentId,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "색은 #RRGGBB 형식이어야 합니다.") String colorHex) {
    }
}
