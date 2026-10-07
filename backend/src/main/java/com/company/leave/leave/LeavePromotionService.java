package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.common.search.SearchKeywords;
import com.company.leave.department.repository.DepartmentRepository;
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
import com.company.leave.policy.domain.LeavePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 사용 촉진. 직원마다 지금 연차 기간의 사용 기한(입사일 기준이면 다음 입사 기념일 전날)이 다가오는데
 * 남은 연차가 있으면 안내한다. 결재 대기 중인 일수는 따로 보여 준다.
 * 관리자가 대상을 골라 보내면 앱 알림과 메일을 보내고 발송 이력(promotion_notices)을 남긴다.
 */
@Service
public class LeavePromotionService {

    /** 조회·발송할 수 있는 가장 긴 기간: 사용 기한 6개월 전부터. */
    public static final int MAX_MONTHS = 6;

    private static final Logger log = LoggerFactory.getLogger(LeavePromotionService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final LeaveBalanceService balanceService;
    private final LeaveRequestRepository requestRepository;
    private final PromotionNoticeRepository noticeRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final PolicyService policyService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountMailProperties mailProperties;
    private final DepartmentRepository departmentRepository;

    public LeavePromotionService(LeaveBalanceService balanceService,
                                 LeaveRequestRepository requestRepository,
                                 PromotionNoticeRepository noticeRepository,
                                 EmployeeRepository employeeRepository,
                                 NotificationService notificationService,
                                 PolicyService policyService,
                                 ApplicationEventPublisher eventPublisher,
                                 AccountMailProperties mailProperties,
                                 DepartmentRepository departmentRepository) {
        this.balanceService = balanceService;
        this.requestRepository = requestRepository;
        this.noticeRepository = noticeRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.policyService = policyService;
        this.eventPublisher = eventPublisher;
        this.mailProperties = mailProperties;
        this.departmentRepository = departmentRepository;
    }

    /**
     * 촉진 대상 한 명.
     *
     * @param granted        부여 연차(이월 포함)
     * @param remaining      남은 연차(승인된 휴가만 뺀 값)
     * @param pending        결재 대기 중인 차감 예정(남은 연차에서 아직 빼지 않음)
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
            long daysLeft,
            String timeLeft,
            long noticeCount,
            Instant lastNotifiedAt) {
    }

    /** 발송 결과. skipped: 대상이 아니어서 보내지 않은 직원 수(그 사이 남은 연차를 다 썼거나 기한이 지난 경우 등). */
    public record SendResult(int sent, int mailed, int skipped) {
    }

    /**
     * 사용 기한이 오늘부터 months 개월 안에 끝나고 남은 연차가 있는 재직자(관리 전용 계정 포함).
     * 사용 기한이 가까운 순.
     *
     * @param months 1~6 (범위를 벗어나면 가까운 값으로 맞춤)
     */
    @Transactional(readOnly = true)
    public List<Target> targets(int months) {
        return targets(months, null);
    }

    /**
     * {@link #targets(int)} 중 검색어에 맞는 직원. 검색어는 공백으로 나눈 단어가 모두 이름이나 부서에 맞아야 하고,
     * 상위 부서 이름으로 찾으면 하위 부서 직원도 나온다(다른 목록 검색과 같은 규칙).
     * <p>
     * 예: 오늘 2026-10-07, 6개월 → 기준일 2027-04-07. 사용 기한 2027-02-28·남은 5일 → 대상,
     * 사용 기한 2027-06-30 → 제외(기준일 뒤), 사용 기한 2026-12-31·남은 0일 → 제외.
     */
    @Transactional(readOnly = true)
    public List<Target> targets(int months, String keyword) {
        // 화면에서 고른 개월 수를 1~6 으로 맞춘다(0 → 1, 7 → 6)
        int m = Math.max(1, Math.min(MAX_MONTHS, months));
        LocalDate today = LocalDate.now();
        // 기준일 = 오늘 + m개월. 말일은 그 달 말일로 맞춰진다(8/31 + 6개월 = 2/28)
        LocalDate limit = today.plusMonths(m);
        Predicate<Employee> matches = keywordFilter(keyword);
        // 재직자 전원(관리 전용 계정 포함)의 오늘이 속한 연차 기간과 그 잔액. 잔액 행이 없는 직원은 빠진다.
        // period().end() 가 사용 기한(입사일 기준 또는 회계연도 기준, LeavePeriodCalculator 가 계산)
        List<LeaveBalanceService.PeriodBalance> candidates = balanceService.balancesAsOf(today, true, true).stream()
                // 사용 기한 <= 기준일(같은 날 포함). isBefore 로 쓰면 딱 m개월 남은 직원이 빠진다
                .filter(pb -> !pb.period().end().isAfter(limit))
                // 남은 연차 = 부여 + 이월 - 사용 - 소멸. 결재 대기분은 빼지 않는다(화면에 pending 으로 따로 표시)
                .filter(pb -> pb.balance().remaining().signum() > 0)
                .filter(pb -> matches.test(pb.employee()))
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
            LocalDate end = pb.period().end();
            PromotionNoticeSummary sent = notices.get(e.getId() + ":" + b.getYear());
            targets.add(new Target(e.getId(), e.getName(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null,
                    hasMailbox(e), b.getYear(), pb.period().start(), end,
                    b.getGranted().add(b.getCarriedOver()), b.getUsed(), pending, b.remaining(),
                    ChronoUnit.DAYS.between(today, end), timeLeft(today, end),
                    sent != null ? sent.count() : 0, sent != null ? sent.lastSentAt() : null));
        }
        targets.sort(Comparator.comparing(Target::periodEnd).thenComparing(Target::name));
        return targets;
    }

    /**
     * 고른 직원에게 촉진 안내(앱 알림 + 메일)를 보내고 발송 이력을 남긴다. 메일은 커밋 뒤 한 사람씩 보낸다.
     * 화면에서 고른 뒤 상황이 바뀌었을 수 있어 대상 여부(사용 기한 6개월 이내, 남은 연차 있음)를 다시 확인한다.
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
            if (deliver(t, e, actor, null, carryOver)) {
                mailed++;
            }
            sent++;
        }
        log.info("연차 촉진 안내 발송: 선택 {}명 중 {}명(메일 {}명), 제외 {}명", employeeIds.size(), sent, mailed, skipped);
        return new SendResult(sent, mailed, skipped);
    }

    /** 자동 발송 대상 한 명. stageMonths: 이번에 보낼 발송 시기(사용 기한 N개월 전). */
    public record AutoTarget(Long employeeId, String name, String department, LocalDate periodEnd, String timeLeft,
                             int stageMonths) {
    }

    /** 자동 발송 결과. alreadySent: 같은 시기에 이미 보내(수동 포함) 건너뛴 인원. */
    public record AutoResult(int sent, int mailed, int alreadySent) {
    }

    private record AutoPlan(List<AutoTarget> targets, Map<Long, Target> byEmployee, int alreadySent) {
    }

    /**
     * 자동 발송(스케줄러, 매일 09:00, 정책에서 켰을 때). 정책의 발송 시기(예: 사용 기한 6개월 전·2개월 전)마다
     * 그 시기에 들어온 직원에게 남은 연차가 있으면 수동 발송과 같은 알림·메일을 보내고 이력을 남긴다.
     * <ul>
     *   <li>같은 시기에 이미 보낸 직원(관리자 수동 발송 포함)은 건너뛴다. 다음 시기에는 다시 보낸다</li>
     *   <li>여러 시기에 한꺼번에 들어와 있으면(시기를 늦게 켠 경우 등) 가장 가까운 시기로 한 번만 보낸다</li>
     * </ul>
     */
    @Transactional
    public AutoResult autoSend() {
        LeavePolicy policy = policyService.getActivePolicy();
        AutoPlan plan = autoPlan(policy, LocalDate.now());
        int mailed = 0;
        int sent = 0;
        for (AutoTarget a : plan.targets()) {
            Employee e = employeeRepository.findById(a.employeeId()).orElse(null);
            if (e == null) {
                continue;
            }
            if (deliver(plan.byEmployee().get(a.employeeId()), e, null, a.stageMonths(), policy.isCarryOverEnabled())) {
                mailed++;
            }
            sent++;
        }
        log.info("연차 촉진 자동 발송: {}명(메일 {}명), 같은 시기 발송 이력으로 건너뜀 {}명", sent, mailed, plan.alreadySent());
        return new AutoResult(sent, mailed, plan.alreadySent());
    }

    /** 지금 자동 발송을 돌리면 보낼 직원(보내지 않음). 자동화 탭 미리보기용. */
    @Transactional(readOnly = true)
    public List<AutoTarget> autoPreview() {
        return autoPlan(policyService.getActivePolicy(), LocalDate.now()).targets();
    }

    private AutoPlan autoPlan(LeavePolicy policy, LocalDate today) {
        List<Integer> months = policy.getPromotionMonths(); // 큰 값부터
        if (months.isEmpty()) {
            return new AutoPlan(List.of(), Map.of(), 0);
        }
        List<Target> candidates = targets(months.get(0));
        if (candidates.isEmpty()) {
            return new AutoPlan(List.of(), Map.of(), 0);
        }
        Map<String, List<LocalDate>> sentDates = noticeRepository
                .findByEmployeeIdIn(candidates.stream().map(Target::employeeId).toList()).stream()
                .collect(Collectors.groupingBy(n -> n.getEmployeeId() + ":" + n.getBalanceYear(),
                        Collectors.mapping(n -> LocalDate.ofInstant(n.getSentAt(), SEOUL), Collectors.toList())));
        List<AutoTarget> planned = new ArrayList<>();
        int already = 0;
        for (Target t : candidates) {
            // 들어와 있는 시기 중 가장 가까운 것(가장 작은 개월 수)
            int stage = months.stream().filter(m -> !t.periodEnd().isAfter(today.plusMonths(m)))
                    .min(Integer::compare).orElse(months.get(0));
            LocalDate windowStart = t.periodEnd().minusMonths(stage);
            boolean sentInWindow = sentDates.getOrDefault(t.employeeId() + ":" + t.year(), List.of()).stream()
                    .anyMatch(d -> !d.isBefore(windowStart));
            if (sentInWindow) {
                already++;
                continue;
            }
            planned.add(new AutoTarget(t.employeeId(), t.name(), t.department(), t.periodEnd(), t.timeLeft(), stage));
        }
        return new AutoPlan(planned, candidates.stream().collect(Collectors.toMap(Target::employeeId, Function.identity())),
                already);
    }

    /**
     * 알림·메일을 보내고 이력을 남긴다.
     *
     * @param actor      보낸 관리자(자동 발송이면 null)
     * @param autoMonths 자동 발송이면 발송 시기(사용 기한 N개월 전), 수동이면 null
     * @return 메일까지 보냈으면 true(메일 주소가 없으면 앱 알림만)
     */
    private boolean deliver(Target t, Employee e, Employee actor, Integer autoMonths, boolean carryOver) {
        notificationService.notify(e.getId(), "LEAVE_PROMOTION", "연차 사용 촉진 안내",
                notificationMessage(t.remaining(), t.pending(), t.periodEnd(), t.timeLeft()),
                "/my-leaves");
        boolean mail = hasMailbox(e);
        if (mail) {
            String sender = actor != null ? LeaveMessenger.handler(actor).display()
                    : "자동 발송 (사용 기한 " + autoMonths + "개월 전 안내)";
            PromotionMailTemplates.Mail m = PromotionMailTemplates.promotion(
                    new PromotionMailTemplates.Notice(t.name(), t.department(), t.periodStart(), t.periodEnd(),
                            t.granted(), t.used(), t.remaining(), t.pending(), t.timeLeft(), t.daysLeft()),
                    sender, carryOver, mailProperties.linkBaseUrl());
            MailLayout.Content content = m.content().withRecipient(e.getName());
            eventPublisher.publishEvent(new LeaveMail(List.of(e.getEmail()), m.subject(), content.text(),
                    content.html(), null, null));
        }
        noticeRepository.save(new PromotionNotice(e.getId(), t.year(), t.periodEnd(), t.remaining(),
                (int) t.daysLeft(), mail ? e.getEmail() : null, actor != null ? actor.getId() : null, autoMonths));
        return mail;
    }

    /** 검색어 조건: 단어마다 이름에 포함되거나 그 단어로 찾은 부서(하위 포함) 소속. 검색어가 없으면 모두. */
    private Predicate<Employee> keywordFilter(String keyword) {
        List<String> tokens = SearchKeywords.tokens(keyword);
        if (tokens.isEmpty()) {
            return e -> true;
        }
        List<SearchKeywords.DepartmentNode> departments = departmentRepository.findAll().stream()
                .map(d -> new SearchKeywords.DepartmentNode(d.getId(),
                        d.getParent() != null ? d.getParent().getId() : null, d.getName()))
                .toList();
        List<Set<Long>> deptIdsByToken = tokens.stream()
                .map(t -> SearchKeywords.departmentSubtrees(departments, t)).toList();
        return e -> {
            String name = e.getName() != null ? e.getName().toLowerCase(Locale.ROOT) : "";
            for (int i = 0; i < tokens.size(); i++) {
                boolean byName = name.contains(tokens.get(i));
                boolean byDept = e.getDepartment() != null && deptIdsByToken.get(i).contains(e.getDepartment().getId());
                if (!byName && !byDept) {
                    return false;
                }
            }
            return true;
        };
    }

    /** 앱 알림 문구: "남은 연차 5일(결재 대기 2.125일), 사용 기한 2026-11-30(1개월 28일 남음). …" */
    static String notificationMessage(BigDecimal remaining, BigDecimal pending, LocalDate end, String timeLeft) {
        return "남은 연차 " + plain(remaining) + "일"
                + (pending.signum() > 0 ? "(결재 대기 " + plain(pending) + "일)" : "")
                + ", 사용 기한 " + end + "(" + timeLeft + " 남음). 기한 전에 휴가를 신청해 주세요.";
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
