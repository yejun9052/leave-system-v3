package com.company.leave.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.dashboard.dto.DashboardDtos;
import com.company.leave.department.domain.Department;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.LeaveBalanceService;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대시보드: 관리자용(재직자 수·오늘 부재·결재 대기·직원별 지금 연차 기간 합계와 소진율·월별·부서별 사용)과
 * 개인용(지금 연차 기간 잔액·결재 대기 건수·다가오는 휴가 5건·오늘 팀 부재).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("대시보드")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final int YEAR = TODAY.getYear();

    @Mock private EmployeeRepository employeeRepository;
    @Mock private LeaveRequestRepository requestRepository;
    @Mock private LeaveBalanceService balanceService;

    private DashboardService service;

    // LeaveType(code, name, deductDays, paid, halfDay, deductFromAnnual, color, sortOrder)
    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final Department 개발팀 = 부서(1L, "개발팀");
    private final Department QA팀 = 부서(2L, "QA팀");

    @BeforeEach
    void setUp() {
        service = new DashboardService(employeeRepository, requestRepository, balanceService);
        lenient().when(requestRepository.findApprovedBetween(TODAY, TODAY)).thenReturn(List.of());
        lenient().when(requestRepository.findApprovedBetween(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .thenReturn(List.of());
        lenient().when(balanceService.balancesAsOf(TODAY, true, true)).thenReturn(List.of());
    }

    // --- 관리자 ---

    @Test
    void 관리자_대시보드는_재직자_수_오늘_부재_결재_대기를_보여준다() {
        when(employeeRepository.countByStatus(EmployeeStatus.ACTIVE)).thenReturn(20L);
        when(requestRepository.findApprovedBetween(TODAY, TODAY))
                .thenReturn(List.of(휴가(직원(1L, 개발팀), TODAY, TODAY, "1"), 휴가(직원(2L, QA팀), TODAY, TODAY, "1")));
        when(requestRepository.countByStatusIn(any())).thenReturn(3L);

        DashboardDtos.AdminDashboard d = service.admin();

        assertThat(d.totalEmployees()).isEqualTo(20L);
        assertThat(d.onLeaveToday()).isEqualTo(2);
        assertThat(d.pendingApprovals()).isEqualTo(3L);
    }

    @Test
    void 결재_대기는_신규_신청과_취소_요청을_함께_센다() {
        service.admin();

        verify(requestRepository).countByStatusIn(
                java.util.EnumSet.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.CANCEL_REQUESTED));
    }

    @Test
    void 부여와_사용은_직원마다_지금_연차_기간의_합계이고_소진율은_소수_첫째_자리까지다() {
        when(balanceService.balancesAsOf(TODAY, true, true)).thenReturn(List.of(기간_잔액("15", "5"), 기간_잔액("16", "3")));

        DashboardDtos.AdminDashboard d = service.admin();

        assertThat(d.totalGranted()).isEqualByComparingTo("31");
        assertThat(d.totalUsed()).isEqualByComparingTo("8");
        assertThat(d.usageRate()).isEqualTo(25.8); // 8 / 31 = 25.806…%
    }

    @Test
    void 부여된_연차가_없으면_소진율은_0이다() {
        when(balanceService.balancesAsOf(TODAY, true, true)).thenReturn(List.of(기간_잔액("0", "0")));

        DashboardDtos.AdminDashboard d = service.admin();

        assertThat(d.usageRate()).isZero();
        assertThat(d.totalGranted()).isZero();
    }

    @Test
    void 월별_사용은_올해_승인된_휴가를_시작일의_달로_모으고_12달을_모두_채운다() {
        when(requestRepository.findApprovedBetween(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31))).thenReturn(List.of(
                휴가(직원(1L, 개발팀), LocalDate.of(YEAR, 3, 2), LocalDate.of(YEAR, 3, 3), "2"),
                휴가(직원(2L, QA팀), LocalDate.of(YEAR, 3, 20), LocalDate.of(YEAR, 3, 20), "1"),
                휴가(직원(3L, null), LocalDate.of(YEAR, 5, 7), LocalDate.of(YEAR, 5, 7), "0.5")));

        List<DashboardDtos.MonthlyUsage> monthly = service.admin().monthlyUsage();

        assertThat(monthly).hasSize(12);
        assertThat(monthly).extracting(DashboardDtos.MonthlyUsage::month).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(monthly.get(2).days()).isEqualByComparingTo("3");
        assertThat(monthly.get(4).days()).isEqualByComparingTo("0.5");
        assertThat(monthly.get(0).days()).isZero();
    }

    @Test
    void 부서별_사용은_많이_쓴_부서부터이고_부서가_없으면_미배정으로_모은다() {
        when(requestRepository.findApprovedBetween(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31))).thenReturn(List.of(
                휴가(직원(3L, null), LocalDate.of(YEAR, 5, 7), LocalDate.of(YEAR, 5, 7), "0.5"),
                휴가(직원(2L, QA팀), LocalDate.of(YEAR, 3, 20), LocalDate.of(YEAR, 3, 20), "1"),
                휴가(직원(1L, 개발팀), LocalDate.of(YEAR, 3, 2), LocalDate.of(YEAR, 3, 3), "2"),
                휴가(직원(4L, 개발팀), LocalDate.of(YEAR, 4, 1), LocalDate.of(YEAR, 4, 1), "1")));

        List<DashboardDtos.DepartmentUsage> byDept = service.admin().departmentUsage();

        assertThat(byDept).extracting(DashboardDtos.DepartmentUsage::departmentName)
                .containsExactly("개발팀", "QA팀", "미배정");
        assertThat(byDept.get(0).days()).isEqualByComparingTo("3");
    }

    // --- 개인 ---

    @Test
    void 개인_대시보드는_그_직원의_지금_연차_기간_잔액을_보여준다() {
        LeaveBalanceResponse balance = 잔액_응답(2025);
        when(balanceService.currentYear(10L)).thenReturn(2025);
        when(balanceService.getResponse(10L, 2025)).thenReturn(balance);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(직원(10L, 개발팀)));

        DashboardDtos.PersonalDashboard d = service.personal(10L);

        assertThat(d.balance()).isSameAs(balance);
    }

    @Test
    void 개인_대시보드의_결재_대기는_본인의_대기_신청_건수다() {
        Employee me = 직원(10L, 개발팀);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(me));
        when(requestRepository.findByEmployeeIdAndStatusOrderByStartDateDesc(10L, LeaveRequestStatus.PENDING))
                .thenReturn(List.of(휴가(me, TODAY.plusDays(3), TODAY.plusDays(3), "1"),
                        휴가(me, TODAY.plusDays(9), TODAY.plusDays(9), "1")));

        assertThat(service.personal(10L).pendingCount()).isEqualTo(2);
    }

    @Test
    void 다가오는_휴가는_끝나지_않은_승인_휴가를_가까운_순으로_5건까지_보여준다() {
        Employee me = 직원(10L, 개발팀);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(me));
        List<LeaveRequest> approved = new ArrayList<>();
        approved.add(휴가(me, TODAY.minusDays(5), TODAY.minusDays(4), "2")); // 이미 끝남
        approved.add(휴가(me, TODAY.minusDays(1), TODAY.plusDays(1), "3")); // 진행 중
        for (int i = 6; i >= 2; i--) {
            approved.add(휴가(me, TODAY.plusDays(i * 7L), TODAY.plusDays(i * 7L), "1"));
        }
        // 같은 메서드를 결재 대기(PENDING)로도 부르므로 lenient
        lenient().when(requestRepository.findByEmployeeIdAndStatusOrderByStartDateDesc(10L, LeaveRequestStatus.APPROVED))
                .thenReturn(approved);

        List<LeaveRequestDtos.Response> upcoming = service.personal(10L).upcoming();

        assertThat(upcoming).extracting(LeaveRequestDtos.Response::startDate).containsExactly(
                TODAY.minusDays(1), TODAY.plusDays(14), TODAY.plusDays(21), TODAY.plusDays(28), TODAY.plusDays(35));
    }

    @Test
    void 오늘_팀_부재는_같은_부서의_다른_직원을_한_번씩만_센다() {
        Employee me = 직원(10L, 개발팀);
        Employee 동료 = 직원(11L, 개발팀);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(me));
        when(requestRepository.findApprovedBetween(TODAY, TODAY)).thenReturn(List.of(
                휴가(동료, TODAY, TODAY, "0.5"),
                휴가(동료, TODAY, TODAY, "0.5"), // 같은 날 반차 두 건
                휴가(me, TODAY, TODAY, "1"), // 본인 제외
                휴가(직원(12L, QA팀), TODAY, TODAY, "1"))); // 다른 부서

        assertThat(service.personal(10L).teamOnLeaveToday()).isEqualTo(1);
    }

    @Test
    void 부서가_없는_직원의_오늘_팀_부재는_0이다() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(직원(10L, null)));

        assertThat(service.personal(10L).teamOnLeaveToday()).isZero();
        verify(requestRepository, never()).findApprovedBetween(TODAY, TODAY);
    }

    // --- helpers ---

    private static Department 부서(Long id, String name) {
        Department d = new Department(name, null, 0);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private static Employee 직원(Long id, Department department) {
        Employee e = Employee.builder().email("e" + id + "@company.com").passwordHash("h").name("직원" + id)
                .department(department).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private LeaveRequest 휴가(Employee e, LocalDate start, LocalDate end, String days) {
        LeaveRequest r = new LeaveRequest(e, 연차, start, end, new BigDecimal(days), new BigDecimal(days),
                start.getYear(), "사유");
        r.approve(null, null);
        return r;
    }

    private static LeaveBalanceService.PeriodBalance 기간_잔액(String granted, String used) {
        LeaveBalance b = new LeaveBalance(1L, YEAR);
        b.setGranted(new BigDecimal(granted));
        b.addUsed(new BigDecimal(used));
        return new LeaveBalanceService.PeriodBalance(직원(1L, null),
                new LeavePeriodCalculator.Period(YEAR, LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)), b);
    }

    private static LeaveBalanceResponse 잔액_응답(int year) {
        return new LeaveBalanceResponse(year, LocalDate.of(year, 7, 5), LocalDate.of(year + 1, 7, 4),
                new BigDecimal("15"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("14"), BigDecimal.ZERO);
    }
}
