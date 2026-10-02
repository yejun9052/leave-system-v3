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
 * 연차 사용 촉진: 사용 기한이 N개월 안에 끝나고 남은 연차가 있는 직원을 찾아, 고른 직원에게 알림·메일을 보내고
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
    void 사용_기한이_고른_개월_안이고_남은_연차가_있는_직원만_대상이다() {
        기간(직원(1L, "두달", "a@company.com"), TODAY.plusMonths(2), "15", "5");
        기간(직원(2L, "다씀", "b@company.com"), TODAY.plusMonths(1), "15", "15");
        기간(직원(3L, "일곱달", "c@company.com"), TODAY.plusMonths(7), "15", "0");
        기간(직원(4L, "다신청", "d@company.com"), TODAY.plusDays(20), "15", "13");
        when(requestRepository.sumPendingDeductedDays(eq(4L), anyInt())).thenReturn(new BigDecimal("2"));

        // 남은 연차를 모두 결재 대기로 신청해 둔 직원도 남은 연차가 있으면 대상(결재 대기 일수로 따로 보여 줌)
        assertThat(service.targets(6)).extracting(LeavePromotionService.Target::name).containsExactly("다신청", "두달");
        assertThat(service.targets(3)).extracting(LeavePromotionService.Target::name).containsExactly("다신청", "두달");
        assertThat(service.targets(1)).extracting(LeavePromotionService.Target::name).containsExactly("다신청");
    }

    @Test
    void 사용_기한이_가까운_순이고_결재_대기_일수를_따로_보여준다() {
        기간(직원(1L, "나중", "a@company.com"), TODAY.plusMonths(5), "15", "5");
        기간(직원(2L, "먼저", "b@company.com"), TODAY.plusDays(10), "16", "4");
        when(requestRepository.sumPendingDeductedDays(eq(2L), anyInt())).thenReturn(new BigDecimal("2.125"));

        List<LeavePromotionService.Target> targets = service.targets(6);

        assertThat(targets).extracting(LeavePromotionService.Target::name).containsExactly("먼저", "나중");
        LeavePromotionService.Target 먼저 = targets.get(0);
        assertThat(먼저.remaining()).isEqualByComparingTo("12");
        assertThat(먼저.pending()).isEqualByComparingTo("2.125");
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
        when(requestRepository.sumPendingDeductedDays(eq(1L), anyInt())).thenReturn(new BigDecimal("2.125"));

        LeavePromotionService.SendResult result = service.send(List.of(1L), 99L);

        assertThat(result).isEqualTo(new LeavePromotionService.SendResult(1, 1, 0));
        verify(notificationService).notify(eq(1L), eq("LEAVE_PROMOTION"), eq("연차 사용 촉진 안내"),
                eq("남은 연차 10일(결재 대기 2.125일), 사용 기한 " + pb.period().end() + "("
                        + LeavePromotionService.timeLeft(TODAY, pb.period().end()) + " 남음). 기한 전에 휴가를 신청해 주세요."),
                eq("/my-leaves"));
        ArgumentCaptor<LeaveMail> mail = ArgumentCaptor.forClass(LeaveMail.class);
        verify(eventPublisher).publishEvent(mail.capture());
        assertThat(mail.getValue().to()).containsExactly("hong@company.com");
        assertThat(mail.getValue().subject()).isEqualTo("[연차관리] 연차 사용 촉진 안내 - 남은 연차 10일 (사용 기한 "
                + pb.period().end() + ")");
        assertThat(mail.getValue().text()).contains("수신자: 홍길동", "발송자: 김인사 (인사관리자)",
                "사용하지 않은 연차가 10일 남아 있습니다. (결재 대기 중인 휴가 2.125일)", "결재 대기: 2.125일", "남은 연차: 10일",
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
    void 자동_발송은_발송_시기에_들어온_직원에게_가장_가까운_시기로_한_번씩_보낸다() {
        자동_발송_켬(6, 2);
        기간(직원(1L, "다섯달", "a@company.com"), TODAY.plusMonths(5), "15", "5");
        기간(직원(2L, "한달", "b@company.com"), TODAY.plusMonths(1), "15", "5");
        기간(직원(3L, "다씀", "c@company.com"), TODAY.plusMonths(1), "15", "15");

        LeavePromotionService.AutoResult result = service.autoSend();

        assertThat(result).isEqualTo(new LeavePromotionService.AutoResult(2, 2, 0));
        ArgumentCaptor<PromotionNotice> notices = ArgumentCaptor.forClass(PromotionNotice.class);
        verify(noticeRepository, times(2)).save(notices.capture());
        assertThat(notices.getAllValues()).extracting(PromotionNotice::getEmployeeId, PromotionNotice::getAutoMonths,
                        PromotionNotice::getSentBy)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, 2, null),
                        org.assertj.core.groups.Tuple.tuple(1L, 6, null));
        ArgumentCaptor<LeaveMail> mails = ArgumentCaptor.forClass(LeaveMail.class);
        verify(eventPublisher, times(2)).publishEvent(mails.capture());
        assertThat(mails.getAllValues().get(0).text()).contains("발송자: 자동 발송 (사용 기한 2개월 전 안내)");
    }

    @Test
    void 같은_시기에_이미_보낸_직원은_관리자_수동_발송이어도_건너뛴다() {
        자동_발송_켬(6, 2);
        LeaveBalanceService.PeriodBalance pb = 기간(직원(1L, "한달", "a@company.com"), TODAY.plusMonths(1), "15", "5");
        when(noticeRepository.findByEmployeeIdIn(any())).thenReturn(List.of(
                발송_이력(1L, pb.balance().getYear(), TODAY.minusDays(10), null))); // 2개월 전 시기 안의 수동 발송

        LeavePromotionService.AutoResult result = service.autoSend();

        assertThat(result).isEqualTo(new LeavePromotionService.AutoResult(0, 0, 1));
        verify(noticeRepository, never()).save(any());
        assertThat(service.autoPreview()).isEmpty();
    }

    @Test
    void 앞_시기에_보낸_것은_다음_시기_발송을_막지_않는다() {
        자동_발송_켬(6, 2);
        LeaveBalanceService.PeriodBalance pb = 기간(직원(1L, "한달", "a@company.com"), TODAY.plusMonths(1), "15", "5");
        when(noticeRepository.findByEmployeeIdIn(any())).thenReturn(List.of(
                발송_이력(1L, pb.balance().getYear(), TODAY.minusMonths(4), 6), // 6개월 전 시기에 보냄
                발송_이력(1L, pb.balance().getYear() - 1, TODAY.minusDays(3), null))); // 지난 연차 기간 발송은 무관

        assertThat(service.autoPreview()).extracting(LeavePromotionService.AutoTarget::stageMonths).containsExactly(2);
        assertThat(service.autoSend().sent()).isEqualTo(1);
    }

    @Test
    void 발송_시기_밖의_직원은_자동으로_보내지_않는다() {
        자동_발송_켬(2);
        기간(직원(1L, "다섯달", "a@company.com"), TODAY.plusMonths(5), "15", "5");

        assertThat(service.autoSend()).isEqualTo(new LeavePromotionService.AutoResult(0, 0, 0));
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void 미리보기는_보내지_않고_대상과_발송_시기만_알려준다() {
        자동_발송_켬(6, 2);
        기간(직원(1L, "다섯달", "a@company.com"), TODAY.plusMonths(5), "15", "5");

        assertThat(service.autoPreview()).extracting(LeavePromotionService.AutoTarget::name,
                LeavePromotionService.AutoTarget::stageMonths).containsExactly(org.assertj.core.groups.Tuple.tuple("다섯달", 6));
        verify(noticeRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    // --- helpers ---

    private void 자동_발송_켬(Integer... months) {
        LeavePolicy policy = LeavePolicy.createDefault();
        policy.applyAutomation(true, List.of(months));
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
    }

    private static PromotionNotice 발송_이력(Long employeeId, int year, LocalDate sentOn, Integer autoMonths) {
        PromotionNotice n = new PromotionNotice(employeeId, year, sentOn.plusMonths(1), BigDecimal.TEN, 30,
                "a@company.com", autoMonths == null ? 99L : null, autoMonths);
        ReflectionTestUtils.setField(n, "sentAt", sentOn.atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant());
        return n;
    }

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
