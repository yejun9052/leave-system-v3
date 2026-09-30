package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.company.leave.audit.AuditService;
import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.notification.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 공휴일 추가 시 기존 휴가 자동 재계산·환원. 일수 계산은 실제 WorkdayCalculator(휴가 신청과 같은 규칙)로 한다.
 * 기준: 2027-05-03(월) 대체공휴일(노동절) 추가.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("공휴일 추가에 따른 휴가 자동 조정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HolidayImpactServiceTest {

    private static final LocalDate MON = LocalDate.of(2027, 5, 3);
    private static final Map<LocalDate, String> 새_공휴일 = Map.of(MON, "대체공휴일(노동절)");

    @Mock
    private LeaveRequestRepository requestRepository;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private LeaveBalanceService balanceService;
    @Mock
    private CalendarEventRepository calendarEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    private final List<LeaveRequest> requests = new ArrayList<>();
    private final LeaveBalance balance = new LeaveBalance(1L, 2027);
    private HolidayImpactService service;

    private final LeaveType 연차 = new LeaveType("ANNUAL", "연차", new BigDecimal("1.0"), true, false, true, "#000", 1);
    private final LeaveType 오전반차 = new LeaveType("HALF_AM", "오전 반차", new BigDecimal("0.5"), true, true, true, "#000", 2);
    private final LeaveType 병가 = new LeaveType("SICK", "병가", new BigDecimal("0.0"), false, false, false, "#000", 5);

    @BeforeEach
    void setUp() {
        service = new HolidayImpactService(requestRepository, holidayRepository, new WorkdayCalculator(),
                balanceService, calendarEventRepository, notificationService, auditService);
        // 새 공휴일은 이미 holidays 에 저장된 상태(같은 트랜잭션)
        lenient().when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                .thenReturn(List.of(new Holiday(MON, "대체공휴일(노동절)")));
        lenient().when(requestRepository.findByStatusInOverlapping(any(), any(), any()))
                .thenAnswer(inv -> requests.stream()
                        .filter(r -> HolidayImpactService.TARGET_STATUSES.contains(r.getStatus())).toList());
        lenient().when(balanceService.getOrCreate(1L, 2027)).thenReturn(balance);
    }

    @Test
    void 승인된_3일_휴가_중_하루가_공휴일이_되면_2일로_줄고_1일을_돌려주고_알린다() {
        LeaveRequest request = 승인된_휴가(연차, MON, MON.plusDays(2), "3", "3");
        balance.addUsed(new BigDecimal("3"));

        HolidayImpactService.ImpactSummary summary = service.applyNewHolidays(새_공휴일);

        assertThat(request.getDays()).isEqualByComparingTo("2");
        assertThat(request.getDeductedDays()).isEqualByComparingTo("2");
        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(balance.getUsed()).isEqualByComparingTo("2");
        assertThat(summary.adjustedRequests()).isEqualTo(1);
        assertThat(summary.restoredDays()).isEqualByComparingTo("1");
        verify(notificationService, times(1)).notify(eq(1L), eq("LEAVE_HOLIDAY_ADJUSTED"), anyString(),
                anyString(), eq("/my-leaves"));
    }

    @Test
    void 승인된_1일_휴가가_공휴일이_되면_자동_취소하고_전액_돌려주고_캘린더_일정을_지운다() {
        LeaveRequest request = 승인된_휴가(연차, MON, MON, "1", "1");
        balance.addUsed(new BigDecimal("1"));

        HolidayImpactService.ImpactSummary summary = service.applyNewHolidays(새_공휴일);

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).isEqualTo("공휴일 지정으로 자동 취소");
        assertThat(balance.getUsed()).isEqualByComparingTo("0");
        assertThat(summary.restoredDays()).isEqualByComparingTo("1");
        verify(calendarEventRepository).deleteByLeaveRequestId(request.getId());
    }

    @Test
    void 반차_날짜가_공휴일이_되면_자동_취소하고_반차만큼_돌려준다() {
        LeaveRequest request = 승인된_휴가(오전반차, MON, MON, "0.5", "0.5");
        balance.addUsed(new BigDecimal("0.5"));

        service.applyNewHolidays(새_공휴일);

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(balance.getUsed()).isEqualByComparingTo("0");
    }

    @Test
    void 대기_중인_휴가는_신청_값만_바꾸고_잔액은_건드리지_않는다() {
        LeaveRequest request = 휴가(연차, MON, MON.plusDays(2), "3", "3");
        balance.addUsed(new BigDecimal("5")); // 다른 승인 건으로 쓴 잔액

        service.applyNewHolidays(새_공휴일);

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(request.getDays()).isEqualByComparingTo("2");
        assertThat(request.getDeductedDays()).isEqualByComparingTo("2");
        assertThat(balance.getUsed()).isEqualByComparingTo("5");
        verify(balanceService, never()).getOrCreate(any(), any(Integer.class));
    }

    @Test
    void 연차를_차감하지_않는_유형은_일수만_바꾸고_잔액은_그대로다() {
        LeaveRequest request = 승인된_휴가(병가, MON, MON.plusDays(2), "3", "0");
        balance.addUsed(new BigDecimal("4"));

        service.applyNewHolidays(새_공휴일);

        assertThat(request.getDays()).isEqualByComparingTo("2");
        assertThat(request.getDeductedDays()).isEqualByComparingTo("0");
        assertThat(balance.getUsed()).isEqualByComparingTo("4");
    }

    @Test
    void 같은_동기화를_다시_실행해도_두_번_돌려주지_않는다() {
        LeaveRequest request = 승인된_휴가(연차, MON, MON.plusDays(2), "3", "3");
        balance.addUsed(new BigDecimal("3"));

        service.applyNewHolidays(새_공휴일);
        HolidayImpactService.ImpactSummary second = service.applyNewHolidays(새_공휴일);

        assertThat(second.adjustedRequests()).isZero();
        assertThat(second.restoredDays()).isEqualByComparingTo("0");
        assertThat(request.getDays()).isEqualByComparingTo("2");
        assertThat(balance.getUsed()).isEqualByComparingTo("2");
        verify(notificationService, times(1)).notify(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 자동_취소된_휴가는_다시_실행해도_또_돌려주지_않는다() {
        승인된_휴가(연차, MON, MON, "1", "1");
        balance.addUsed(new BigDecimal("1"));

        service.applyNewHolidays(새_공휴일);
        HolidayImpactService.ImpactSummary second = service.applyNewHolidays(새_공휴일);

        assertThat(second.adjustedRequests()).isZero();
        assertThat(balance.getUsed()).isEqualByComparingTo("0");
    }

    @Test
    void 새_공휴일이_기간에_없는_휴가는_건드리지_않는다() {
        LeaveRequest request = 승인된_휴가(연차, MON.plusDays(1), MON.plusDays(2), "2", "2");
        balance.addUsed(new BigDecimal("2"));

        HolidayImpactService.ImpactSummary summary = service.applyNewHolidays(새_공휴일);

        assertThat(summary.adjustedRequests()).isZero();
        assertThat(request.getDays()).isEqualByComparingTo("2");
        assertThat(balance.getUsed()).isEqualByComparingTo("2");
    }

    @Test
    void 조정된_건마다_시스템_이름으로_감사_로그를_남긴다() {
        승인된_휴가(연차, MON, MON.plusDays(2), "3", "3");
        balance.addUsed(new BigDecimal("3"));

        service.applyNewHolidays(새_공휴일);

        verify(auditService).record(eq(null), eq("SYSTEM"), eq("HOLIDAY_ADJUST"), eq("leave_request"),
                eq(null), anyString(), eq(true));
    }

    @Test
    void 병가가_공휴일로_자동_취소되면_승인_때_소멸시킨_연차도_되돌린다() {
        LeaveRequest request = 승인된_휴가(병가, MON, MON, "1", "0");
        balance.forfeit(new BigDecimal("0.5"));
        request.recordForfeit(new BigDecimal("0.5"));

        service.applyNewHolidays(새_공휴일);

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(balance.getExpired()).isEqualByComparingTo("0");
        assertThat(request.getForfeitedDays()).isEqualByComparingTo("0");
    }

    @Test
    void 시간차는_공휴일이_기간_밖이면_시간_수를_유지한다() {
        LeaveType 시간차 = new LeaveType("HOURLY", "시간차", new BigDecimal("0.125"), true,
                com.company.leave.leave.domain.DayPortion.HOURLY, true, false, "#000", 3);
        LeaveRequest request = 승인된_휴가(시간차, MON.plusDays(1), MON.plusDays(1), "0.375", "0.375");
        balance.addUsed(new BigDecimal("0.375"));

        HolidayImpactService.ImpactSummary summary = service.applyNewHolidays(새_공휴일);

        assertThat(summary.adjustedRequests()).isZero();
        assertThat(request.getDays()).isEqualByComparingTo("0.375");
    }

    @Test
    void 취소_요청_중인_휴가도_승인_건처럼_잔액을_돌려준다() {
        LeaveRequest request = 승인된_휴가(연차, MON, MON.plusDays(2), "3", "3");
        request.requestCancel("개인 사정");
        balance.addUsed(new BigDecimal("3"));

        service.applyNewHolidays(새_공휴일);

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCEL_REQUESTED);
        assertThat(request.getDeductedDays()).isEqualByComparingTo("2");
        assertThat(balance.getUsed()).isEqualByComparingTo("2");
    }

    @Test
    void 한_휴가에_새_공휴일이_여러_날_걸리면_한_번에_모두_반영한다() {
        LocalDate wed = MON.plusDays(2);
        lenient().when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                .thenReturn(List.of(new Holiday(MON, "대체공휴일(노동절)"), new Holiday(wed, "어린이날")));
        LeaveRequest request = 승인된_휴가(연차, MON, MON.plusDays(4), "5", "5");
        balance.addUsed(new BigDecimal("5"));

        HolidayImpactService.ImpactSummary summary = service.applyNewHolidays(
                Map.of(MON, "대체공휴일(노동절)", wed, "어린이날"));

        assertThat(request.getDays()).isEqualByComparingTo("3");
        assertThat(balance.getUsed()).isEqualByComparingTo("3");
        assertThat(summary.adjustedRequests()).isEqualTo(1);
        assertThat(summary.restoredDays()).isEqualByComparingTo("2");
        verify(notificationService, times(1)).notify(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 대기_승인_취소요청_상태만_새_공휴일_기간으로_조회한다() {
        LocalDate wed = MON.plusDays(2);

        service.applyNewHolidays(Map.of(wed, "어린이날", MON, "대체공휴일(노동절)"));

        verify(requestRepository).findByStatusInOverlapping(
                EnumSet.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.LEAD_APPROVED,
                        LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCEL_REQUESTED),
                MON, wed);
    }

    @Test
    void 새_공휴일이_없으면_아무것도_조회하지_않는다() {
        HolidayImpactService.ImpactSummary summary = service.applyNewHolidays(Map.of());

        assertThat(summary.adjustedRequests()).isZero();
        verifyNoInteractions(requestRepository, notificationService, auditService);
    }

    @Test
    void 트랜잭션_안에서는_감사_로그를_커밋_뒤에만_남긴다() {
        승인된_휴가(연차, MON, MON.plusDays(2), "3", "3");
        balance.addUsed(new BigDecimal("3"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.applyNewHolidays(새_공휴일);
            verifyNoInteractions(auditService);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(auditService).record(eq(null), eq("SYSTEM"), eq("HOLIDAY_ADJUST"), eq("leave_request"),
                    eq(null), anyString(), eq(true));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // --- 테스트 데이터 ---

    private LeaveRequest 휴가(LeaveType type, LocalDate start, LocalDate end, String days, String deducted) {
        Employee employee = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").build();
        ReflectionTestUtils.setField(employee, "id", 1L);
        LeaveRequest request = new LeaveRequest(employee, type, start, end,
                new BigDecimal(days), new BigDecimal(deducted), 2027, "사유");
        ReflectionTestUtils.setField(request, "id", (long) (requests.size() + 100));
        requests.add(request);
        return request;
    }

    private LeaveRequest 승인된_휴가(LeaveType type, LocalDate start, LocalDate end, String days, String deducted) {
        LeaveRequest request = 휴가(type, start, end, days, deducted);
        request.approve(request.getEmployee(), Instant.now());
        return request;
    }
}
