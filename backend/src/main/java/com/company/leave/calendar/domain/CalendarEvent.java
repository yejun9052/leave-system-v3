package com.company.leave.calendar.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;

/**
 * 캘린더 일정. 승인된 휴가(LEAVE_REQUEST), 관리자 등록(ADMIN_EVENT), 공휴일(HOLIDAY)을 표현.
 */
@Entity
@Table(name = "calendar_events")
@Getter
public class CalendarEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "all_day", nullable = false)
    private boolean allDay = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEventScope scope = CalendarEventScope.COMPANY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEventSource source = CalendarEventSource.ADMIN_EVENT;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex = "#4f46e5";

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "leave_request_id")
    private Long leaveRequestId;

    @Column(name = "created_by")
    private Long createdBy;

    protected CalendarEvent() {
    }

    private CalendarEvent(Builder b) {
        this.title = b.title;
        this.startDate = b.startDate;
        this.endDate = b.endDate;
        this.allDay = b.allDay;
        this.scope = b.scope;
        this.source = b.source;
        this.colorHex = b.colorHex;
        this.departmentId = b.departmentId;
        this.employeeId = b.employeeId;
        this.leaveRequestId = b.leaveRequestId;
        this.createdBy = b.createdBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void update(String title, LocalDate startDate, LocalDate endDate, boolean allDay,
                       CalendarEventScope scope, String colorHex, Long departmentId) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allDay = allDay;
        this.scope = scope;
        this.colorHex = colorHex;
        this.departmentId = departmentId;
    }

    public static final class Builder {
        private String title;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean allDay = true;
        private CalendarEventScope scope = CalendarEventScope.COMPANY;
        private CalendarEventSource source = CalendarEventSource.ADMIN_EVENT;
        private String colorHex = "#4f46e5";
        private Long departmentId;
        private Long employeeId;
        private Long leaveRequestId;
        private Long createdBy;

        public Builder title(String v) { this.title = v; return this; }
        public Builder startDate(LocalDate v) { this.startDate = v; return this; }
        public Builder endDate(LocalDate v) { this.endDate = v; return this; }
        public Builder allDay(boolean v) { this.allDay = v; return this; }
        public Builder scope(CalendarEventScope v) { this.scope = v; return this; }
        public Builder source(CalendarEventSource v) { this.source = v; return this; }
        public Builder colorHex(String v) { this.colorHex = v; return this; }
        public Builder departmentId(Long v) { this.departmentId = v; return this; }
        public Builder employeeId(Long v) { this.employeeId = v; return this; }
        public Builder leaveRequestId(Long v) { this.leaveRequestId = v; return this; }
        public Builder createdBy(Long v) { this.createdBy = v; return this; }

        public CalendarEvent build() {
            return new CalendarEvent(this);
        }
    }
}
