package com.company.leave.leave;

import com.company.leave.audit.AuditService;
import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.notification.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 새로 추가된 공휴일이 걸친 기존 휴가 신청을 다시 계산해 차감 일수·잔액을 바로잡는다.
 * <ul>
 *   <li>현재 holidays 기준으로 처음부터 다시 계산해 덮어쓴다 → 같은 동기화를 반복해도 이중 환원 없음</li>
 *   <li>기간 내 근무일이 0이 되면 자동 취소(캘린더 일정 삭제)</li>
 *   <li>승인·취소요청 건만 잔액 환원(대기 건은 아직 차감 전이라 신청 값만 갱신)</li>
 * </ul>
 * 공휴일 저장과 같은 트랜잭션에서 실행된다(HolidaySyncService).
 */
@Service
public class HolidayImpactService {

    private static final Logger log = LoggerFactory.getLogger(HolidayImpactService.class);
    static final Set<LeaveRequestStatus> TARGET_STATUSES = EnumSet.of(
            LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCEL_REQUESTED);
    static final String AUTO_CANCEL_REASON = "공휴일 지정으로 자동 취소";

    private final LeaveRequestRepository requestRepository;
    private final HolidayRepository holidayRepository;
    private final WorkdayCalculator workdayCalculator;
    private final LeaveBalanceService balanceService;
    private final CalendarEventRepository calendarEventRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public HolidayImpactService(LeaveRequestRepository requestRepository,
                                HolidayRepository holidayRepository,
                                WorkdayCalculator workdayCalculator,
                                LeaveBalanceService balanceService,
                                CalendarEventRepository calendarEventRepository,
                                NotificationService notificationService,
                                AuditService auditService) {
        this.requestRepository = requestRepository;
        this.holidayRepository = holidayRepository;
        this.workdayCalculator = workdayCalculator;
        this.balanceService = balanceService;
        this.calendarEventRepository = calendarEventRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /** 조정 결과 요약. */
    public record ImpactSummary(int adjustedRequests, BigDecimal restoredDays) {

        public static ImpactSummary none() {
            return new ImpactSummary(0, BigDecimal.ZERO);
        }
    }

    /**
     * @param newHolidays 이번에 새로 추가된 공휴일(날짜 → 이름). 이미 holidays 테이블에 저장된 상태여야 한다.
     */
    @Transactional
    public ImpactSummary applyNewHolidays(Map<LocalDate, String> newHolidays) {
        if (newHolidays.isEmpty()) {
            return ImpactSummary.none();
        }
        LocalDate min = newHolidays.keySet().stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = newHolidays.keySet().stream().max(LocalDate::compareTo).orElseThrow();

        int adjusted = 0;
        BigDecimal restored = BigDecimal.ZERO;
        List<String> auditDetails = new ArrayList<>();
        for (LeaveRequest request : requestRepository.findByStatusInOverlapping(TARGET_STATUSES, min, max)) {
            Map<LocalDate, String> hit = holidaysWithin(request, newHolidays);
            if (hit.isEmpty()) {
                continue;
            }
            Adjustment result = recalculate(request, hit);
            if (result == null) {
                continue; // 값 변화 없음(이미 반영됨)
            }
            adjusted++;
            restored = restored.add(result.restored());
            notificationService.notify(request.getEmployee().getId(), "LEAVE_HOLIDAY_ADJUSTED",
                    result.cancelled() ? "공휴일 지정으로 휴가가 자동 취소되었습니다" : "공휴일 지정으로 휴가 일수가 조정되었습니다",
                    result.message(), "/my-leaves");
            auditDetails.add("leaveRequest=" + request.getId() + " | " + result.message());
        }
        recordAuditAfterCommit(auditDetails);
        log.info("공휴일 반영: 신청 {}건 조정, 환원 {}일", adjusted, restored);
        return new ImpactSummary(adjusted, restored);
    }

    /** 한 건을 현재 공휴일 기준으로 다시 계산. 바뀐 것이 없으면 null. */
    private Adjustment recalculate(LeaveRequest request, Map<LocalDate, String> hit) {
        LeaveType type = request.getLeaveType();
        Set<LocalDate> holidays = holidayRepository
                .findByDateBetweenOrderByDateAsc(request.getStartDate(), request.getEndDate()).stream()
                .map(Holiday::getDate).collect(Collectors.toSet());
        BigDecimal oldDays = request.getDays();
        BigDecimal oldDeducted = request.getDeductedDays();
        boolean chargedBalance = request.getStatus() != LeaveRequestStatus.PENDING && type.isDeductFromAnnual();
        String holidayText = hit.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
        String period = type.getName() + " " + request.getStartDate() + " ~ " + request.getEndDate();

        if (workdayCalculator.countWorkdays(request.getStartDate(), request.getEndDate(), holidays) == 0) {
            BigDecimal restored = chargedBalance ? oldDeducted : BigDecimal.ZERO;
            restore(request, restored);
            calendarEventRepository.deleteByLeaveRequestId(request.getId());
            request.autoCancel(AUTO_CANCEL_REASON);
            String message = period + ": " + holidayText + " 지정으로 근무일이 없어 자동 취소되었습니다. "
                    + "(차감 " + plain(oldDeducted) + "일 → 0일"
                    + (restored.signum() > 0 ? ", " + plain(restored) + "일 환원)" : ")");
            return new Adjustment(true, restored, message);
        }

        BigDecimal newDays = workdayCalculator.computeLeaveDays(
                request.getStartDate(), request.getEndDate(), type, holidays);
        BigDecimal newDeducted = workdayCalculator.deductionFor(type, newDays);
        if (newDays.compareTo(oldDays) == 0 && newDeducted.compareTo(oldDeducted) == 0) {
            return null;
        }
        request.adjustDays(newDays, newDeducted);
        BigDecimal restored = chargedBalance ? oldDeducted.subtract(newDeducted).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        restore(request, restored);
        String message = period + ": " + holidayText + " 지정으로 일수 " + plain(oldDays) + "일 → " + plain(newDays)
                + "일, 차감 " + plain(oldDeducted) + "일 → " + plain(newDeducted) + "일"
                + (restored.signum() > 0 ? " (" + plain(restored) + "일 환원)" : "");
        return new Adjustment(false, restored, message);
    }

    private void restore(LeaveRequest request, BigDecimal days) {
        if (days.signum() > 0) {
            balanceService.getOrCreate(request.getEmployee().getId(), request.getAppliedYear()).restoreUsed(days);
        }
    }

    private Map<LocalDate, String> holidaysWithin(LeaveRequest request, Map<LocalDate, String> newHolidays) {
        return newHolidays.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(request.getStartDate()) && !e.getKey().isAfter(request.getEndDate()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.TreeMap::new));
    }

    /** 감사 로그는 별도 트랜잭션(REQUIRES_NEW)이라, 조정이 실제로 커밋된 뒤에만 남긴다. */
    private void recordAuditAfterCommit(List<String> details) {
        if (details.isEmpty()) {
            return;
        }
        Runnable write = () -> details.forEach(d ->
                auditService.record(null, "SYSTEM", "HOLIDAY_ADJUST", "leave_request", null, d, true));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record Adjustment(boolean cancelled, BigDecimal restored, String message) {
    }
}
