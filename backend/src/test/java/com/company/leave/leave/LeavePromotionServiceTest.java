package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.PromotionNotice;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.leave.repository.PromotionNoticeRepository;
import com.company.leave.leave.repository.PromotionNoticeSummary;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.LeaveMail;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 연차 사용 촉진: 사용 기한이 N개월 안에 끝나고 사용 계획 없는 연차가 있는 직원을 찾아, 고른 직원에게 알림·메일을 보내고
 * 발송 이력을 남긴다. 사용 기한은 오늘 기준 상대 날짜로 둔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("연차 사용 촉진")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeavePromotionServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Mock private LeaveBalanceService balanceService;
    @Mock private LeaveRequestRepository requestRepository;
    @Mock private PromotionNoticeRepository noticeRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private NotificationService notificationService;
    @Mock private PolicyService policyService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private LeavePromotionService service;
    private final List<LeaveBalanceService.PeriodBalance> balances = new ArrayList<>();
    private final Map<Long, Employee> employees = new HashMap<>();
    private Employee 인사관리자;

    @BeforeEach
    void setUp() {
        service = new LeavePromotionService(balanceService, requestRepository, noticeRepository, employeeRepository,
                notificationService, policyService, eventPublisher,
                new AccountMailProperties("noreply@company.com", "http://localhost:5173"));
        인사관리자 = 직원(99L, "김인사", "hr@company.com");
        인사관리자.replaceRoles(Set.of(Role.EMPLOYEE, Role.HR_ADMIN));
        lenient().when(balanceService.balancesAsOf(any(), eq(true))).thenReturn(balances);
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
        lenient().when(noticeRepository.summarize(any())).thenReturn(List.of());
        lenient().when(employeeRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(employees.get(inv.<Long>getArgument(0))));
        lenient().when(policyService.getActivePolicy()).thenReturn(LeavePolicy.createDefault());
    }

    @Test
    void 사용_기한이_고른_개월_안이고_사용_계획_없는_연차가_있는_직원만_대상이다() {
        기간(직원(1L, "두달", "a@company.com"), TODAY.plusMonths(2), "15", "5");
        기간(직원(2L, "다씀", "b@company.com"), TODAY.plusMonths(1), "15", "15");
        기간(직원(3L, "일곱달", "c@company.com"), TODAY.plusMonths(7), "15", "0");
        기간(직원(4L, "대기", "d@company.com"), TODAY.plusDays(20), "15", "13");
        when(requestRepository.sumPendingDeductedDays(eq(4L), anyInt())).thenReturn(new BigDecimal("2"));

        assertThat(service.targets(6)).extracting(LeavePromotionService.Target::name).containsExactly("두달");
        assertThat(service.targets(3)).extracting(LeavePromotionService.Target::name).containsExactly("두달");
        assertThat(service.targets(1)).isEmpty();
    }

    @Test
    void 사용_기한이_가까운_순이고_사용_계획_없는_연차는_결재_대기를_뺀_값이다() {
        기간(직원(1L, "나중", "a@company.com"), TODAY.plusMonths(5), "15", "5");
        기간(직원(2L, "먼저", "b@company.com"), TODAY.plusDays(10), "16", "4");
        when(requestRepository.sumPendingDeductedDays(eq(2L), anyInt())).thenReturn(new BigDecimal("3"));

        List<LeavePromotionService.Target> targets = service.targets(6);

        assertThat(targets).extracting(LeavePromotionService.Target::name).containsExactly("먼저", "나중");
        LeavePromotionService.Target 먼저 = targets.get(0);
        assertThat(먼저.remaining()).isEqualByComparingTo("12");
        assertThat(먼저.unplanned()).isEqualByComparingTo("9");
        assertThat(먼저.daysLeft()).isEqualTo(10);
        assertThat(먼저.timeLeft()).isEqualTo("10일");
    }

    @Test
    void 이번_연차_기간에_보낸_횟수와_최근_발송_시각을_보여준다() {
        LeaveBalanceService.PeriodBalance pb = 기간(직원(1L, "홍길동", "a@company.com"), TODAY.plusMonths(2), "15", "5");
        Instant last = Instant.parse("2026-09-01T00:00:00Z");
        when(noticeRepository.summarize(any())).thenReturn(List.of(
                new PromotionNoticeSummary(1L, pb.balance().getYear(), 2, last),
                new PromotionNoticeSummary(1L, pb.balance().getYear() - 1, 5, Instant.now())));

        LeavePromotionService.Target t = service.targets(6).get(0);

        assertThat(t.noticeCount()).isEqualTo(2);
        assertThat(t.lastNotifiedAt()).isEqualTo(last);
    }

    @Test
    void 남은_기간은_서버가_개월과_일로_계산한다() {
        LocalDate d = LocalDate.of(2026, 10, 2);

        assertThat(LeavePromotionService.timeLeft(d, LocalDate.of(2026, 12, 31))).isEqualTo("2개월 29일");
        assertThat(LeavePromotionService.timeLeft(d, LocalDate.of(2027, 1, 2))).isEqualTo("3개월");
        assertThat(LeavePromotionService.timeLeft(d, LocalDate.of(2026, 10, 14))).isEqualTo("12일");
        assertThat(LeavePromotionService.timeLeft(d, d)).isEqualTo("오늘 하루");
    }

    @Test
    void 고른_직원에게_알림과_메일을_보내고_발송_이력을_남긴다() {
        LeaveBalanceService.PeriodBalance pb = 기간(직원(1L, "홍길동", "hong@company.com"), TODAY.plusMonths(2), "15", "5");

        LeavePromotionService.SendResult result = service.send(List.of(1L), 99L);

        assertThat(result).isEqualTo(new LeavePromotionService.SendResult(1, 1, 0));
        verify(notificationService).notify(eq(1L), eq("LEAVE_PROMOTION"), eq("연차 사용 촉진 안내"), any(), eq("/my-leaves"));
        ArgumentCaptor<LeaveMail> mail = ArgumentCaptor.forClass(LeaveMail.class);
        verify(eventPublisher).publishEvent(mail.capture());
        assertThat(mail.getValue().to()).containsExactly("hong@company.com");
        assertThat(mail.getValue().subject()).isEqualTo("[연차관리] 연차 사용 촉진 안내 - 사용 계획 없는 연차 10일 (사용 기한 "
                + pb.period().end() + ")");
        assertThat(mail.getValue().text()).contains("수신자: 홍길동", "발송자: 김인사 (인사관리자)",
                "사용 기한: " + pb.period().end() + " (" + LeavePromotionService.timeLeft(TODAY, pb.period().end())
                        + " 남음, D-" + (pb.period().end().toEpochDay() - TODAY.toEpochDay()) + ")");
        ArgumentCaptor<PromotionNotice> notice = ArgumentCaptor.forClass(PromotionNotice.class);
        verify(noticeRepository).save(notice.capture());
        assertThat(notice.getValue().getEmployeeId()).isEqualTo(1L);
        assertThat(notice.getValue().getPeriodEnd()).isEqualTo(pb.period().end());
        assertThat(notice.getValue().getRemainingDays()).isEqualByComparingTo("10");
        assertThat(notice.getValue().getEmail()).isEqualTo("hong@company.com");
        assertThat(notice.getValue().getSentBy()).isEqualTo(99L);
    }

    @Test
    void 그_사이_대상이_아니게_된_직원은_건너뛴다() {
        기간(직원(1L, "대상", "a@company.com"), TODAY.plusMonths(2), "15", "5");
        기간(직원(2L, "다씀", "b@company.com"), TODAY.plusMonths(2), "15", "15");

        LeavePromotionService.SendResult result = service.send(List.of(1L, 2L, 1L), 99L);

        assertThat(result).isEqualTo(new LeavePromotionService.SendResult(1, 1, 1));
        verify(noticeRepository, times(1)).save(any());
    }

    @Test
    void 메일_주소가_없으면_알림만_보내고_이력에_주소를_비운다() {
        기간(직원(1L, "주소없음", null), TODAY.plusMonths(2), "15", "5");

        LeavePromotionService.SendResult result = service.send(List.of(1L), 99L);

        assertThat(result).isEqualTo(new LeavePromotionService.SendResult(1, 0, 0));
        verify(eventPublisher, never()).publishEvent(any());
        ArgumentCaptor<PromotionNotice> notice = ArgumentCaptor.forClass(PromotionNotice.class);
        verify(noticeRepository).save(notice.capture());
        assertThat(notice.getValue().getEmail()).isNull();
    }

    @Test
    void 아무도_고르지_않으면_보낼_수_없다() {
        assertThatThrownBy(() -> service.send(List.of(), 99L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 정기_알림은_사용_계획_없는_연차가_있는_모든_재직자에게_앱_알림만_보낸다() {
        기간(직원(1L, "멀리", "a@company.com"), TODAY.plusMonths(9), "15", "5");
        기간(직원(2L, "다씀", "b@company.com"), TODAY.plusMonths(2), "15", "15");

        assertThat(service.notifyRemaining()).isEqualTo(1);

        verify(notificationService).notify(eq(1L), eq("LEAVE_PROMOTION"), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(noticeRepository, never()).save(any());
    }

    // --- helpers ---

    private Employee 직원(Long id, String name, String email) {
        Employee e = Employee.builder().email(email).passwordHash("h").name(name)
                .department(new Department("개발팀", null, 0)).build();
        ReflectionTestUtils.setField(e, "id", id);
        employees.put(id, e);
        return e;
    }

    /** 사용 기한이 end 인 지금 기간(1년). 이월 없음. */
    private LeaveBalanceService.PeriodBalance 기간(Employee e, LocalDate end, String granted, String used) {
        LocalDate start = end.minusYears(1).plusDays(1);
        LeaveBalance b = new LeaveBalance(e.getId(), start.getYear());
        b.setGranted(new BigDecimal(granted));
        b.addUsed(new BigDecimal(used));
        LeaveBalanceService.PeriodBalance pb = new LeaveBalanceService.PeriodBalance(
                e, new LeavePeriodCalculator.Period(start.getYear(), start, end), b);
        balances.add(pb);
        return pb;
    }
}
