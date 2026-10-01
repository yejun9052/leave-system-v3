package com.company.leave.calendar;

import com.company.leave.calendar.domain.CalendarEvent;
import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.domain.CalendarEventSource;
import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.dto.CalendarDtos;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    private final CalendarEventRepository eventRepository;
    private final HolidayRepository holidayRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestService leaveRequestService;

    public CalendarService(CalendarEventRepository eventRepository,
                           HolidayRepository holidayRepository,
                           DepartmentRepository departmentRepository,
                           LeaveRequestService leaveRequestService) {
        this.eventRepository = eventRepository;
        this.holidayRepository = holidayRepository;
        this.departmentRepository = departmentRepository;
        this.leaveRequestService = leaveRequestService;
    }

    /** 기간 내 사용자가 볼 수 있는 모든 일정(휴가/관리자이벤트/공휴일). */
    @Transactional(readOnly = true)
    public List<CalendarDtos.EventResponse> getEvents(LocalDate start, LocalDate end, UserPrincipal user) {
        boolean admin = isAdmin(user);
        List<CalendarDtos.EventResponse> result = new ArrayList<>();

        for (CalendarEvent e : eventRepository.findBetween(start, end)) {
            if (!isVisible(e, user, admin)) {
                continue;
            }
            boolean editable = e.getSource() == CalendarEventSource.ADMIN_EVENT
                    && (admin || (e.getCreatedBy() != null && e.getCreatedBy().equals(user.getId())));
            result.add(CalendarDtos.EventResponse.fromEvent(e, editable));
        }

        holidayRepository.findByDateBetweenOrderByDateAsc(start, end).forEach(h ->
                result.add(CalendarDtos.EventResponse.holiday(h.getId(), h.getName(), h.getDate())));

        return result;
    }

    /**
     * 날짜 상세: 그날의 휴가(보이는 범위는 {@link LeaveRequestService#leavesOnDay}), 볼 수 있는 등록 일정, 공휴일 이름.
     */
    @Transactional(readOnly = true)
    public CalendarDtos.DayDetail getDay(LocalDate date, UserPrincipal user) {
        boolean admin = isAdmin(user);
        List<CalendarDtos.DayEvent> events = eventRepository.findBetween(date, date).stream()
                .filter(e -> e.getSource() == CalendarEventSource.ADMIN_EVENT && isVisible(e, user, admin))
                .map(CalendarDtos.DayEvent::from)
                .toList();
        List<String> holidayNames = holidayRepository.findByDateBetweenOrderByDateAsc(date, date).stream()
                .map(Holiday::getName)
                .toList();
        return new CalendarDtos.DayDetail(date,
                holidayNames.isEmpty() ? null : String.join(", ", holidayNames),
                leaveRequestService.leavesOnDay(user.getId(), date),
                events);
    }

    @Transactional
    public CalendarDtos.EventResponse create(CalendarDtos.CreateEvent req, UserPrincipal user) {
        if (req.scope() == CalendarEventScope.DEPARTMENT && req.departmentId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "부서 일정은 부서를 지정해야 합니다.");
        }
        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종료일이 시작일보다 빠릅니다.");
        }
        authorizeScope(user, req.scope(), req.departmentId());
        CalendarEvent event = CalendarEvent.builder()
                .title(req.title())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .allDay(req.allDay())
                .scope(req.scope())
                .source(CalendarEventSource.ADMIN_EVENT)
                .colorHex(req.colorHex() != null ? req.colorHex() : "#4f46e5")
                .departmentId(req.scope() == CalendarEventScope.DEPARTMENT ? req.departmentId() : null)
                .createdBy(user.getId())
                .build();
        eventRepository.save(event);
        return CalendarDtos.EventResponse.fromEvent(event, true);
    }

    @Transactional
    public CalendarDtos.EventResponse update(Long id, CalendarDtos.CreateEvent req, UserPrincipal user) {
        CalendarEvent event = getEditableAdminEvent(id, user);
        authorizeScope(user, req.scope(), req.departmentId());
        event.update(req.title(), req.startDate(), req.endDate(), req.allDay(),
                req.scope(),
                req.colorHex() != null ? req.colorHex() : event.getColorHex(),
                req.scope() == CalendarEventScope.DEPARTMENT ? req.departmentId() : null);
        return CalendarDtos.EventResponse.fromEvent(event, true);
    }

    @Transactional
    public void delete(Long id, UserPrincipal user) {
        CalendarEvent event = getEditableAdminEvent(id, user);
        eventRepository.delete(event);
    }

    private CalendarEvent getEditableAdminEvent(Long id, UserPrincipal user) {
        CalendarEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        if (event.getSource() != CalendarEventSource.ADMIN_EVENT) {
            throw new BusinessException(ErrorCode.CONFLICT, "휴가/공휴일 일정은 직접 수정할 수 없습니다.");
        }
        boolean allowed = isAdmin(user)
                || (event.getCreatedBy() != null && event.getCreatedBy().equals(user.getId()));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return event;
    }

    private boolean isVisible(CalendarEvent e, UserPrincipal user, boolean admin) {
        return switch (e.getScope()) {
            case COMPANY -> true;
            case DEPARTMENT -> admin
                    || (user.getDepartmentId() != null && user.getDepartmentId().equals(e.getDepartmentId()));
            case PERSONAL -> admin
                    || (e.getEmployeeId() != null && e.getEmployeeId().equals(user.getId()));
        };
    }

    private boolean isAdmin(UserPrincipal user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_HR_ADMIN"));
    }

    /**
     * 일정 scope 등록/수정 권한 검증.
     * - 관리자: 모든 scope 허용
     * - 팀장 등 비관리자: 전사(COMPANY) 금지, 부서(DEPARTMENT)는 본인이 리드하는 부서(하위 포함)만, 개인(PERSONAL) 허용
     */
    private void authorizeScope(UserPrincipal user, CalendarEventScope scope, Long departmentId) {
        if (isAdmin(user)) {
            return;
        }
        switch (scope) {
            case COMPANY -> throw new BusinessException(ErrorCode.FORBIDDEN,
                    "전사 일정은 관리자만 등록할 수 있습니다.");
            case DEPARTMENT -> {
                if (departmentId == null || !ledDeptIds(user.getId()).contains(departmentId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN,
                            "담당(하위 포함) 부서의 일정만 등록할 수 있습니다.");
                }
            }
            case PERSONAL -> { /* 개인 일정은 허용 */ }
        }
    }

    /** 해당 사용자가 리드하는 부서 + 그 하위 부서 id 집합. */
    private Set<Long> ledDeptIds(Long leadId) {
        Set<Long> ids = new HashSet<>();
        for (Department led : departmentRepository.findByLeadId(leadId)) {
            ids.addAll(departmentRepository.findSubtreeIds(led.getId()));
        }
        return ids;
    }
}
