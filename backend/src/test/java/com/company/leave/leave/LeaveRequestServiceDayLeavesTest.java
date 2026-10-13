package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.EmployeeService;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 캘린더 날짜 상세의 휴가 목록 보이는 범위.
 * 승인 휴가는 모두에게, 결재 대기 휴가는 본인·해당 팀장·인사관리자에게만 보이고, 사유는 응답에 없다.
 * 조직: 개발팀(팀장 2, 팀원 6·8), 영업팀(팀장 4, 팀원 9), 경영지원팀(인사관리자 11).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("캘린더 날짜 상세 휴가 목록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServiceDayLeavesTest {

    private static final LocalDate TUE = LocalDate.of(2027, 5, 4);

    @Mock
    private LeaveRequestRepository requestRepository;
    @Mock
    private LeaveTypeService leaveTypeService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private LeaveBalanceService balanceService;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private PolicyService policyService;
    @Mock
    private LeaveAccrualCalculator accrualCalculator;
    @Mock
    private CalendarEventRepository calendarEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private BlackoutPeriodRepository blackoutPeriodRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LeaveRequestService service;

    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);

    private Employee 개발팀장;
    private Employee 개발팀원;
    private Employee 개발동료;
    private Employee 영업팀장;
    private Employee 영업팀원;
    private Employee 인사관리자;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService,
                new LeavePeriodCalculator(new LeaveAccrualCalculator(), new WorkdayCalculator()),
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher,
                new LeaveMessenger(notificationService, eventPublisher, new com.company.leave.mail.AccountMailProperties(
                        "noreply@company.com", "http://localhost:5173")),
                org.mockito.Mockito.mock(com.company.leave.audit.AuditService.class));

        Department 개발팀 = 부서(2L, "개발팀");
        Department 영업팀 = 부서(4L, "영업팀");
        Department 경영지원팀 = 부서(5L, "경영지원팀");
        개발팀장 = 직원(2L, 개발팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        개발팀원 = 직원(6L, 개발팀, Role.EMPLOYEE);
        개발동료 = 직원(8L, 개발팀, Role.EMPLOYEE);
        영업팀장 = 직원(4L, 영업팀, Role.EMPLOYEE, Role.TEAM_LEAD);
        영업팀원 = 직원(9L, 영업팀, Role.EMPLOYEE);
        인사관리자 = 직원(11L, 경영지원팀, Role.EMPLOYEE, Role.HR_ADMIN);
        lenient().when(departmentRepository.findByLeadId(2L)).thenReturn(List.of(개발팀));
        lenient().when(departmentRepository.findByLeadId(4L)).thenReturn(List.of(영업팀));
        lenient().when(departmentRepository.findSubtreeIds(2L)).thenReturn(List.of(2L));
        lenient().when(departmentRepository.findSubtreeIds(4L)).thenReturn(List.of(4L));

        LeaveRequest 승인 = 신청(800L, 개발동료, LeaveRequestStatus.APPROVED);
        LeaveRequest 대기 = 신청(801L, 개발팀원, LeaveRequestStatus.PENDING);
        lenient().when(requestRepository.findByStatusInOverlapping(any(), eq(TUE), eq(TUE)))
                .thenReturn(List.of(승인, 대기));
    }

    @Test
    void 승인된_휴가는_다른_팀_일반_직원에게도_보이지만_결재_대기_휴가는_안_보인다() {
        assertThat(보이는_신청(영업팀원)).containsExactly(800L);
    }

    @Test
    void 결재_대기_휴가는_본인에게_보이고_나로_표시된다() {
        List<LeaveRequestDtos.DayLeave> leaves = service.leavesOnDay(개발팀원.getId(), TUE);

        assertThat(leaves).extracting(LeaveRequestDtos.DayLeave::id).containsExactlyInAnyOrder(800L, 801L);
        assertThat(leaves).filteredOn(LeaveRequestDtos.DayLeave::mine)
                .extracting(LeaveRequestDtos.DayLeave::id).containsExactly(801L);
    }

    @Test
    void 결재_대기_휴가는_해당_팀장에게_보인다() {
        assertThat(보이는_신청(개발팀장)).containsExactlyInAnyOrder(800L, 801L);
    }

    @Test
    void 결재_대기_휴가는_인사관리자에게_보인다() {
        assertThat(보이는_신청(인사관리자)).containsExactlyInAnyOrder(800L, 801L);
    }

    @Test
    void 결재_대기_휴가는_같은_팀_동료에게_안_보인다() {
        assertThat(보이는_신청(개발동료)).containsExactly(800L);
    }

    @Test
    void 결재_대기_휴가는_다른_팀_팀장에게_안_보인다() {
        assertThat(보이는_신청(영업팀장)).containsExactly(800L);
    }

    @Test
    void 응답에_휴가_사유가_없다() {
        List<String> fields = Arrays.stream(LeaveRequestDtos.DayLeave.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertThat(fields).noneMatch(name -> name.toLowerCase().contains("reason"));

        assertThat(service.leavesOnDay(개발팀장.getId(), TUE).toString()).doesNotContain("개인 사정");
    }

    // --- 병가·경조사 종류 가리기 ---

    private final LeaveType 병가 = new LeaveType("SICK", "병가", BigDecimal.ZERO, false, false, false, "#ef4444", 5);
    private final LeaveType 경조사 = new LeaveType("CONDOLENCE", "경조사 휴가", BigDecimal.ZERO, true, false, false,
            "#f59e0b", 4);

    @Test
    void 동료의_병가는_다른_직원에게_종류_없이_휴가로만_보인다() {
        병가와_경조사가_있는_날();

        assertThat(service.leavesOnDay(영업팀원.getId(), TUE))
                .filteredOn(l -> l.id() >= 802L)
                .allSatisfy(l -> {
                    assertThat(l.leaveTypeName()).isEqualTo("휴가");
                    assertThat(l.leaveTypeColor()).isEqualTo(LeaveRequestDtos.HIDDEN_TYPE_COLOR);
                })
                .hasSize(2);
        assertThat(service.leavesOnDay(개발동료.getId(), TUE)).filteredOn(l -> l.id() == 803L)
                .extracting(LeaveRequestDtos.DayLeave::leaveTypeName).containsExactly("휴가"); // 같은 팀 동료도
    }

    @Test
    void 병가_경조사는_본인_맡은_팀장_인사관리자에게는_그대로_보인다() {
        병가와_경조사가_있는_날();

        assertThat(종류(개발팀원, 802L)).isEqualTo("병가");
        assertThat(종류(개발팀장, 802L)).isEqualTo("병가");
        assertThat(종류(인사관리자, 802L)).isEqualTo("병가");
        assertThat(종류(영업팀장, 802L)).isEqualTo("휴가"); // 다른 팀 팀장은 맡은 사람이 아님
    }

    @Test
    void 연차_같은_일반_휴가는_누구에게나_종류가_보인다() {
        assertThat(service.leavesOnDay(영업팀원.getId(), TUE)).filteredOn(l -> l.id() == 800L)
                .extracting(LeaveRequestDtos.DayLeave::leaveTypeName).containsExactly("연차");
    }

    @Test
    void 캘린더_일정에서_가릴_휴가는_이름_휴가_제목으로_돌려준다() {
        LeaveRequest 병가신청 = 신청(802L, 개발팀원, LeaveRequestStatus.APPROVED, 병가);
        LeaveRequest 연차신청 = 신청(800L, 개발동료, LeaveRequestStatus.APPROVED, 연차);
        lenient().when(requestRepository.findAllById(any())).thenReturn(List.of(병가신청, 연차신청));

        assertThat(service.hiddenLeaveTitles(영업팀원.getId(), List.of(800L, 802L)))
                .containsExactly(java.util.Map.entry(802L, "직원6 - 휴가"));
        assertThat(service.hiddenLeaveTitles(개발팀장.getId(), List.of(800L, 802L))).isEmpty();
    }

    @Test
    void 가린_제목에도_반차_표시는_남긴다() {
        LeaveRequest 생일반차 = 신청(803L, 개발팀원, LeaveRequestStatus.APPROVED, 경조사);
        ReflectionTestUtils.setField(생일반차, "halfDayPart", com.company.leave.leave.domain.HalfDayPart.PM);
        lenient().when(requestRepository.findAllById(any())).thenReturn(List.of(생일반차));

        assertThat(service.hiddenLeaveTitles(영업팀원.getId(), List.of(803L)))
                .containsExactly(java.util.Map.entry(803L, "직원6 - 휴가 오후 반차"));
    }

    private void 병가와_경조사가_있는_날() {
        lenient().when(requestRepository.findByStatusInOverlapping(any(), eq(TUE), eq(TUE))).thenReturn(List.of(
                신청(800L, 개발동료, LeaveRequestStatus.APPROVED),
                신청(802L, 개발팀원, LeaveRequestStatus.APPROVED, 병가),
                신청(803L, 개발팀원, LeaveRequestStatus.APPROVED, 경조사)));
    }

    private String 종류(Employee caller, long requestId) {
        return service.leavesOnDay(caller.getId(), TUE).stream().filter(l -> l.id() == requestId)
                .map(LeaveRequestDtos.DayLeave::leaveTypeName).findFirst().orElseThrow();
    }

    // --- helpers ---

    private List<Long> 보이는_신청(Employee caller) {
        return service.leavesOnDay(caller.getId(), TUE).stream().map(LeaveRequestDtos.DayLeave::id).toList();
    }

    private Department 부서(Long id, String name) {
        Department department = new Department(name, null, 0);
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    private Employee 직원(Long id, Department department, Role... roles) {
        Employee employee = Employee.builder()
                .email("e" + id + "@company.com").passwordHash("h").name("직원" + id)
                .department(department).role(Role.highest(roles))
                .build();
        ReflectionTestUtils.setField(employee, "id", id);
        lenient().when(employeeService.getEntity(id)).thenReturn(employee);
        return employee;
    }

    private LeaveRequest 신청(long id, Employee employee, LeaveRequestStatus status) {
        return 신청(id, employee, status, 연차);
    }

    private LeaveRequest 신청(long id, Employee employee, LeaveRequestStatus status, LeaveType type) {
        LeaveRequest request = new LeaveRequest(employee, type, TUE, TUE,
                BigDecimal.ONE, BigDecimal.ONE, 2027, "개인 사정");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "status", status);
        return request;
    }
}
