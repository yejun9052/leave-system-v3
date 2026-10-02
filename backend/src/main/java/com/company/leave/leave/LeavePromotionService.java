package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.PromotionNotice;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.leave.repository.PromotionNoticeRepository;
import com.company.leave.leave.repository.PromotionNoticeSummary;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.LeaveMail;
import com.company.leave.mail.MailLayout;
import com.company.leave.mail.PromotionMailTemplates;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 사용 촉진. 직원마다 지금 연차 기간의 사용 기한(입사일 기준이면 다음 입사 기념일 전날)이 다가오는데
 * 아직 사용 계획이 없는 연차(남은 연차 − 결재 대기)가 있으면 안내한다.
 * 관리자가 대상을 골라 보내면 앱 알림과 메일을 보내고 발송 이력(promotion_notices)을 남긴다.
 */
@Service
public class LeavePromotionService {

    /** 조회·발송할 수 있는 가장 긴 기간: 사용 기한 6개월 전부터. */
    public static final int MAX_MONTHS = 6;

    private static final Logger log = LoggerFactory.getLogger(LeavePromotionService.class);

    private final LeaveBalanceService balanceService;
    private final LeaveRequestRepository requestRepository;
    private final PromotionNoticeRepository noticeRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final PolicyService policyService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountMailProperties mailProperties;

    public LeavePromotionService(LeaveBalanceService balanceService,
                                 LeaveRequestRepository requestRepository,
                                 PromotionNoticeRepository noticeRepository,
                                 EmployeeRepository employeeRepository,
                                 NotificationService notificationService,
                                 PolicyService policyService,
                                 ApplicationEventPublisher eventPublisher,
                                 AccountMailProperties mailProperties) {
        this.balanceService = balanceService;
        this.requestRepository = requestRepository;
        this.noticeRepository = noticeRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.policyService = policyService;
        this.eventPublisher = eventPublisher;
        this.mailProperties = mailProperties;
    }

    /**
     * 촉진 대상 한 명.
     *
     * @param granted        부여 연차(이월 포함)
     * @param unplanned      사용 계획이 없는 연차 = 남은 연차 − 결재 대기
     * @param daysLeft       사용 기한까지 남은 날(기한 당일 0)
     * @param timeLeft       사용 기한까지 남은 기간("2개월 29일")
     * @param noticeCount    이 연차 기간에 촉진 안내를 보낸 횟수
     * @param lastNotifiedAt 이 연차 기간에 마지막으로 보낸 시각(없으면 null)
     */
    public record Target(
            Long employeeId,
            String name,
            String department,
            boolean hasEmail,
            int year,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal granted,
            BigDecimal used,
            BigDecimal pending,
            BigDecimal remaining,
            BigDecimal unplanned,
            long daysLeft,
            String timeLeft,
            long noticeCount,
            Instant lastNotifiedAt) {
    }

    /** 발송 결과. skipped: 대상이 아니어서 보내지 않은 직원 수(그 사이 휴가를 신청했거나 기한이 지난 경우 등). */
    public record SendResult(int sent, int mailed, int skipped) {
    }

    /**
     * 사용 기한이 오늘부터 months 개월 안에 끝나고 사용 계획이 없는 연차가 있는 재직자(관리 전용 계정 제외).
     * 사용 기한이 가까운 순.
     *
     * @param months 1~6 (범위를 벗어나면 가까운 값으로 맞춤)
     */
    @Transactional(readOnly = true)
    public List<Target> targets(int months) {
        int m = Math.max(1, Math.min(MAX_MONTHS, months));
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusMonths(m);
        List<LeaveBalanceService.PeriodBalance> candidates = balanceService.balancesAsOf(today, true).stream()
                .filter(pb -> !pb.period().end().isAfter(limit))
                .filter(pb -> pb.balance().remaining().signum() > 0)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, PromotionNoticeSummary> notices = noticeRepository
                .summarize(candidates.stream().map(pb -> pb.employee().getId()).toList()).stream()
                .collect(Collectors.toMap(s -> s.employeeId() + ":" + s.balanceYear(), Function.identity()));

        List<Target> targets = new ArrayList<>();
        for (LeaveBalanceService.PeriodBalance pb : candidates) {
            Employee e = pb.employee();
            LeaveBalance b = pb.balance();
            BigDecimal pending = requestRepository.sumPendingDeductedDays(e.getId(), b.getYear());
            BigDecimal unplanned = b.remaining().subtract(pending);
            if (unplanned.signum() <= 0) {
                continue; // 남은 연차를 모두 신청해 둠
            }
            LocalDate end = pb.period().end();
            PromotionNoticeSummary sent = notices.get(e.getId() + ":" + b.getYear());
            targets.add(new Target(e.getId(), e.getName(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null,
                    hasMailbox(e), b.getYear(), pb.period().start(), end,
                    b.getGranted().add(b.getCarriedOver()), b.getUsed(), pending, b.remaining(), unplanned,
                    ChronoUnit.DAYS.between(today, end), timeLeft(today, end),
                    sent != null ? sent.count() : 0, sent != null ? sent.lastSentAt() : null));
        }
        targets.sort(Comparator.comparing(Target::periodEnd).thenComparing(Target::name));
        return targets;
    }

    /**
     * 고른 직원에게 촉진 안내(앱 알림 + 메일)를 보내고 발송 이력을 남긴다. 메일은 커밋 뒤 한 사람씩 보낸다.
     * 화면에서 고른 뒤 상황이 바뀌었을 수 있어 대상 여부(사용 기한 6개월 이내, 사용 계획 없는 연차 있음)를 다시 확인한다.
     *
     * @param actorId 보낸 관리자
     */
    @Transactional
    public SendResult send(Collection<Long> employeeIds, Long actorId) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "안내를 보낼 직원을 선택해 주세요.");
        }
        Map<Long, Target> eligible = targets(MAX_MONTHS).stream()
                .collect(Collectors.toMap(Target::employeeId, Function.identity()));
        Employee actor = employeeRepository.findById(actorId).orElse(null);
        boolean carryOver = policyService.getActivePolicy().isCarryOverEnabled();
        int sent = 0;
        int mailed = 0;
        int skipped = 0;
        for (Long id : new LinkedHashSet<>(employeeIds)) {
            Target t = eligible.get(id);
            Employee e = t != null ? employeeRepository.findById(id).orElse(null) : null;
            if (e == null) {
                skipped++;
                continue;
            }
            if (deliver(t, e, actor, carryOver)) {
                mailed++;
            }
            sent++;
        }
        log.info("연차 촉진 안내 발송: 선택 {}명 중 {}명(메일 {}명), 제외 {}명", employeeIds.size(), sent, mailed, skipped);
        return new SendResult(sent, mailed, skipped);
    }

    /** @return 메일까지 보냈으면 true(메일 주소가 없으면 앱 알림만) */
    private boolean deliver(Target t, Employee e, Employee actor, boolean carryOver) {
        notificationService.notify(e.getId(), "LEAVE_PROMOTION", "연차 사용 촉진 안내",
                "사용 계획이 없는 연차 " + plain(t.unplanned()) + "일, 사용 기한 " + t.periodEnd() + "("
                        + t.timeLeft() + " 남음). 사용 계획을 세워 휴가를 신청해 주세요.",
                "/my-leaves");
        boolean mail = hasMailbox(e);
        if (mail) {
            PromotionMailTemplates.Mail m = PromotionMailTemplates.promotion(
                    new PromotionMailTemplates.Notice(t.name(), t.department(), t.periodStart(), t.periodEnd(),
                            t.granted(), t.used(), t.remaining(), t.pending(), t.unplanned(), t.timeLeft(),
                            t.daysLeft()),
                    actor != null ? LeaveMessenger.handler(actor) : null, carryOver, mailProperties.linkBaseUrl());
            MailLayout.Content content = m.content().withRecipient(e.getName());
            eventPublisher.publishEvent(new LeaveMail(List.of(e.getEmail()), m.subject(), content.text(),
                    content.html(), null, null));
        }
        noticeRepository.save(new PromotionNotice(e.getId(), t.year(), t.periodEnd(), t.remaining(),
                (int) t.daysLeft(), mail ? e.getEmail() : null, actor != null ? actor.getId() : null));
        return mail;
    }

    /**
     * 정기 알림(스케줄러 7/1·11/1, 정책의 촉진제도 사용이 켜져 있을 때): 지금 연차 기간에 사용 계획이 없는 연차가 있는
     * 재직자 모두에게 앱 알림만 보낸다. 메일·발송 이력은 관리자가 고른 발송({@link #send})에서만 남긴다.
     */
    @Transactional
    public int notifyRemaining() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (LeaveBalanceService.PeriodBalance pb : balanceService.balancesAsOf(today, true)) {
            LeaveBalance b = pb.balance();
            BigDecimal unplanned = b.remaining()
                    .subtract(requestRepository.sumPendingDeductedDays(pb.employee().getId(), b.getYear()));
            if (unplanned.signum() <= 0) {
                continue;
            }
            notificationService.notify(pb.employee().getId(), "LEAVE_PROMOTION", "연차 사용 촉진 안내",
                    "사용 계획이 없는 연차 " + plain(unplanned) + "일, 사용 기한 " + pb.period().end() + "("
                            + timeLeft(today, pb.period().end()) + " 남음). 사용 계획을 세워 휴가를 신청해 주세요.",
                    "/my-leaves");
            count++;
        }
        log.info("연차 촉진 정기 알림: {}명", count);
        return count;
    }

    /** 오늘부터 사용 기한까지 남은 기간: "2개월 29일", "3개월", "12일", 기한 당일은 "오늘 하루". */
    static String timeLeft(LocalDate today, LocalDate end) {
        Period p = Period.between(today, end);
        int months = p.getYears() * 12 + p.getMonths();
        int days = p.getDays();
        if (months == 0 && days == 0) {
            return "오늘 하루";
        }
        if (months == 0) {
            return days + "일";
        }
        return days == 0 ? months + "개월" : months + "개월 " + days + "일";
    }

    private static boolean hasMailbox(Employee e) {
        return e.getEmail() != null && e.getEmail().contains("@");
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
