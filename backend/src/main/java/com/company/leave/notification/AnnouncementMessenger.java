package com.company.leave.notification;

import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.LeaveMessenger;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.AnnouncementMail;
import com.company.leave.mail.AnnouncementMailTemplates;
import com.company.leave.mail.AnnouncementMailTemplates.Change;
import com.company.leave.mail.AnnouncementMailTemplates.Schedule;
import com.company.leave.mail.LeaveMailTemplates.Handler;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 블랙아웃(연차 사용 금지 기간)·캘린더 일정의 추가·변경·삭제를 받는 사람에게 알림 + 메일로 알린다.
 * <ul>
 *   <li>블랙아웃: 재직 중인 전 직원</li>
 *   <li>전체 일정: 재직 중인 전 직원 / 부서 일정: 그 부서 소속 재직자(캘린더에서 그 일정을 보는 사람) / 개인 일정: 안 보냄</li>
 *   <li>변경으로 범위가 바뀌면 바뀌기 전·후 받는 사람 모두</li>
 * </ul>
 * 처리한 본인과 관리 전용 계정은 받지 않는다. 메일은 커밋 뒤 숨은 참조로 한 통만 보낸다.
 */
@Component
public class AnnouncementMessenger {

    /**
     * 일정 내용. 부서 일정이면 departmentId 가 있다.
     */
    public record EventView(String title, LocalDate start, LocalDate end, CalendarEventScope scope,
                            Long departmentId) {
    }

    private static final String ALL_STAFF = "재직 중인 전 직원";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountMailProperties mailProperties;

    public AnnouncementMessenger(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                                 NotificationService notificationService, ApplicationEventPublisher eventPublisher,
                                 AccountMailProperties mailProperties) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.mailProperties = mailProperties;
    }

    /** @param before 변경 전(변경일 때만, 아니면 null). 블랙아웃은 범위가 없어 scopeLabel 은 null */
    public void blackout(Change change, Schedule now, Schedule before, Long actorId) {
        if (change == Change.UPDATED && now.equals(before)) {
            return;
        }
        Employee actor = employeeRepository.findById(actorId).orElse(null);
        AnnouncementMailTemplates.Mail mail = AnnouncementMailTemplates.blackout(change, now, before,
                handler(actor), ALL_STAFF, mailProperties.linkBaseUrl());
        deliver(companyWide(), actorId, "BLACKOUT_" + change.name(), "연차 사용 금지 기간 " + change.label(),
                now.name() + " (" + period(now.start(), now.end()) + ")", mail);
    }

    /** @param before 변경 전(변경일 때만, 아니면 null) */
    public void event(Change change, EventView now, EventView before, Long actorId) {
        if (change == Change.UPDATED && now.equals(before)) {
            return;
        }
        Map<Long, Employee> recipients = new LinkedHashMap<>();
        audience(now).forEach(e -> recipients.putIfAbsent(e.getId(), e));
        if (before != null) {
            audience(before).forEach(e -> recipients.putIfAbsent(e.getId(), e));
        }
        if (recipients.isEmpty()) {
            return;
        }
        Schedule nowSchedule = schedule(now);
        Employee actor = employeeRepository.findById(actorId).orElse(null);
        AnnouncementMailTemplates.Mail mail = AnnouncementMailTemplates.event(change, nowSchedule,
                before != null ? schedule(before) : null, handler(actor),
                audienceLabel(now, before), mailProperties.linkBaseUrl());
        deliver(recipients.values(), actorId, "CALENDAR_EVENT_" + change.name(),
                nowSchedule.scopeLabel() + " " + change.label(),
                now.title() + " (" + period(now.start(), now.end()) + ")", mail);
    }

    private void deliver(Collection<Employee> recipients, Long actorId, String type, String title, String message,
                         AnnouncementMailTemplates.Mail mail) {
        List<Employee> targets = recipients.stream()
                .filter(e -> !e.getId().equals(actorId))
                .filter(e -> !e.isSystemAccount() && e.isActive())
                .toList();
        targets.forEach(e -> notificationService.notify(e.getId(), type, title, message, "/calendar"));
        List<String> bcc = targets.stream()
                .map(Employee::getEmail)
                .filter(email -> email != null && email.contains("@"))
                .distinct()
                .toList();
        if (!bcc.isEmpty()) {
            eventPublisher.publishEvent(new AnnouncementMail(bcc, mail.subject(), mail.content().text(),
                    mail.content().html()));
        }
    }

    private List<Employee> audience(EventView view) {
        return switch (view.scope()) {
            case COMPANY -> companyWide();
            case DEPARTMENT -> view.departmentId() == null ? List.of()
                    : employeeRepository.findByDepartmentId(view.departmentId());
            case PERSONAL -> List.of();
        };
    }

    /** 메일 표의 수신자 줄: 전체 일정이 끼면 "재직 중인 전 직원", 아니면 "개발팀·QA팀 소속 직원". */
    private String audienceLabel(EventView now, EventView before) {
        List<EventView> views = before != null ? List.of(now, before) : List.of(now);
        if (views.stream().anyMatch(v -> v.scope() == CalendarEventScope.COMPANY)) {
            return ALL_STAFF;
        }
        return views.stream()
                .filter(v -> v.scope() == CalendarEventScope.DEPARTMENT)
                .map(v -> departmentName(v.departmentId()))
                .distinct()
                .collect(Collectors.joining("·")) + " 소속 직원";
    }

    private List<Employee> companyWide() {
        return employeeRepository.findByStatusAndSystemAccountFalse(EmployeeStatus.ACTIVE);
    }

    private Schedule schedule(EventView view) {
        String scope = switch (view.scope()) {
            case COMPANY -> "전체 일정";
            case DEPARTMENT -> departmentName(view.departmentId()) + " 일정";
            case PERSONAL -> "개인 일정";
        };
        return new Schedule(view.title(), view.start(), view.end(), scope);
    }

    private String departmentName(Long departmentId) {
        return departmentRepository.findById(Objects.requireNonNullElse(departmentId, -1L))
                .map(Department::getName).orElse("부서");
    }

    private static Handler handler(Employee actor) {
        return actor != null ? LeaveMessenger.handler(actor) : new Handler("관리자", "알 수 없음");
    }

    private static String period(LocalDate start, LocalDate end) {
        return start.equals(end) ? start.toString() : start + " ~ " + end;
    }
}
