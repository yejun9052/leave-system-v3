package com.company.leave.policy;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.leave.LeaveRequestService.DateRange;
import com.company.leave.mail.AnnouncementMailTemplates.Change;
import com.company.leave.mail.AnnouncementMailTemplates.Schedule;
import com.company.leave.notification.AnnouncementMessenger;
import com.company.leave.policy.domain.BlackoutConflictMode;
import com.company.leave.policy.domain.BlackoutPeriod;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.dto.PolicyRuleDtos;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyRulesService {

    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");

    private final ServiceAwardRuleRepository awardRepository;
    private final LeaveRequestService leaveRequestService;
    private final PolicyService policyService;
    private final SpecialLeaveRuleRepository specialRepository;
    private final BlackoutPeriodRepository blackoutRepository;
    private final AnnouncementMessenger announcementMessenger;

    public PolicyRulesService(ServiceAwardRuleRepository awardRepository,
                              SpecialLeaveRuleRepository specialRepository,
                              BlackoutPeriodRepository blackoutRepository,
                              AnnouncementMessenger announcementMessenger,
                              LeaveRequestService leaveRequestService,
                              PolicyService policyService) {
        this.awardRepository = awardRepository;
        this.specialRepository = specialRepository;
        this.blackoutRepository = blackoutRepository;
        this.announcementMessenger = announcementMessenger;
        this.leaveRequestService = leaveRequestService;
        this.policyService = policyService;
    }

    // --- 장기근속 포상 ---
    @Transactional(readOnly = true)
    public List<PolicyRuleDtos.AwardRule> listAwards() {
        return awardRepository.findAllByOrderByYearsAsc().stream()
                .map(PolicyRuleDtos.AwardRule::from).toList();
    }

    @Transactional
    public PolicyRuleDtos.AwardRule createAward(PolicyRuleDtos.AwardRuleRequest req) {
        if (awardRepository.findByYears(req.years()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 근속연수입니다: " + req.years());
        }
        return PolicyRuleDtos.AwardRule.from(
                awardRepository.save(new ServiceAwardRule(req.years(), req.bonusDays(), req.name())));
    }

    @Transactional
    public PolicyRuleDtos.AwardRule updateAward(Long id, PolicyRuleDtos.AwardRuleRequest req) {
        ServiceAwardRule r = awardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        r.update(req.years(), req.bonusDays(), req.name());
        return PolicyRuleDtos.AwardRule.from(r);
    }

    @Transactional
    public void deleteAward(Long id) {
        awardRepository.deleteById(id);
    }

    // --- 경조사 ---
    @Transactional(readOnly = true)
    public List<PolicyRuleDtos.SpecialRule> listSpecials() {
        return specialRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(PolicyRuleDtos.SpecialRule::from).toList();
    }

    @Transactional
    public PolicyRuleDtos.SpecialRule createSpecial(PolicyRuleDtos.SpecialRuleRequest req) {
        validateSpecialDays(req.days());
        SpecialLeaveRule r = new SpecialLeaveRule(req.name(), req.days(), req.leaveTypeCode(),
                req.sortOrder() != null ? req.sortOrder() : 0, req.annualLimit());
        return PolicyRuleDtos.SpecialRule.from(specialRepository.save(r));
    }

    @Transactional
    public PolicyRuleDtos.SpecialRule updateSpecial(Long id, PolicyRuleDtos.SpecialRuleRequest req) {
        SpecialLeaveRule r = specialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateSpecialDays(req.days());
        // 순서를 보내지 않으면 지금 순서를 그대로 둔다(예전에는 0으로 초기화됐다)
        r.update(req.name(), req.days(), req.leaveTypeCode(),
                req.sortOrder() != null ? req.sortOrder() : r.getSortOrder(), req.annualLimit());
        return PolicyRuleDtos.SpecialRule.from(r);
    }

    @Transactional
    public void deleteSpecial(Long id) {
        specialRepository.deleteById(id);
    }

    /**
     * 경조사 규정 일수는 0.5(오전·오후 반차로 신청, 예: 생일) 또는 1 이상의 정수.
     * 경조사 휴가는 종일 단위라 그 밖의 값(0.25, 1.5 등)은 그대로 신청할 수 없다.
     */
    private static void validateSpecialDays(BigDecimal days) {
        boolean half = days.compareTo(HALF_DAY) == 0;
        boolean whole = days.signum() > 0 && days.stripTrailingZeros().scale() <= 0;
        if (!half && !whole) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "경조사 일수는 0.5일(반차) 또는 1일 단위로 입력해 주세요.");
        }
    }

    // --- 블랙아웃 ---
    @Transactional(readOnly = true)
    public List<PolicyRuleDtos.Blackout> listBlackouts() {
        return blackoutRepository.findAllByOrderByStartDateAsc().stream()
                .map(PolicyRuleDtos.Blackout::from).toList();
    }

    /**
     * 금지 기간 등록·수정 전 미리보기: 저장하면 자동 반려·취소될 휴가와 그대로 남는 휴가(저장하지 않음).
     *
     * @param editingId 수정하는 금지 기간(새로 등록이면 null). 수정이면 새로 늘어난 날짜만 처리 대상이다
     */
    @Transactional(readOnly = true)
    public PolicyRuleDtos.BlackoutImpact blackoutImpact(LocalDate start, LocalDate end, Long editingId) {
        validateBlackoutRange(start, end);
        List<DateRange> segments = List.of(new DateRange(start, end));
        if (editingId != null) {
            BlackoutPeriod b = blackoutRepository.findById(editingId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            segments = addedDates(b.getStartDate(), b.getEndDate(), start, end);
        }
        BlackoutConflictMode mode = conflictMode();
        return PolicyRuleDtos.BlackoutImpact.of(mode, editingId != null && !segments.isEmpty(),
                leaveRequestService.planBlackout(start, end, segments, mode));
    }

    /**
     * 추가·변경·삭제 모두 재직 중인 전 직원에게 알림 + 메일(처리한 본인 제외, {@link AnnouncementMessenger}).
     * 추가하면 금지 기간과 겹치는 휴가를 정책대로 처리한다({@link LeaveRequestService#applyBlackout}, 같은 트랜잭션).
     */
    @Transactional
    public PolicyRuleDtos.Blackout createBlackout(PolicyRuleDtos.BlackoutRequest req, Long actorId) {
        validateBlackoutRange(req.startDate(), req.endDate());
        BlackoutPeriod saved = blackoutRepository.save(new BlackoutPeriod(req.startDate(), req.endDate(), req.name()));
        leaveRequestService.applyBlackout(saved.getName(), saved.getStartDate(), saved.getEndDate(),
                List.of(new DateRange(saved.getStartDate(), saved.getEndDate())), conflictMode(), actorId);
        announcementMessenger.blackout(Change.CREATED, schedule(saved), null, actorId);
        return PolicyRuleDtos.Blackout.from(saved);
    }

    /**
     * 수정. 기간을 늘렸으면 새로 늘어난 날짜와 겹치는 휴가만 지금 정책대로 처리한다. 원래 기간에 있던 휴가(예전 정책으로
     * 유지된 것)는 건드리지 않는다(화면이 경고와 함께 명단을 보여 준다). 이름만 바꾸거나 기간을 줄이면 처리하지 않는다.
     */
    @Transactional
    public PolicyRuleDtos.Blackout updateBlackout(Long id, PolicyRuleDtos.BlackoutRequest req, Long actorId) {
        BlackoutPeriod b = blackoutRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateBlackoutRange(req.startDate(), req.endDate());
        Schedule before = schedule(b);
        List<DateRange> added = addedDates(b.getStartDate(), b.getEndDate(), req.startDate(), req.endDate());
        b.update(req.startDate(), req.endDate(), req.name());
        if (!added.isEmpty()) {
            leaveRequestService.applyBlackout(b.getName(), b.getStartDate(), b.getEndDate(), added, conflictMode(),
                    actorId);
        }
        announcementMessenger.blackout(Change.UPDATED, schedule(b), before, actorId);
        return PolicyRuleDtos.Blackout.from(b);
    }

    private static void validateBlackoutRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종료일이 시작일보다 빠릅니다.");
        }
    }

    private BlackoutConflictMode conflictMode() {
        return policyService.getActivePolicy().getBlackoutConflictMode();
    }

    /** 새 기간 [newStart, newEnd] 중 원래 기간 [oldStart, oldEnd] 에 없던 날짜 구간(앞·뒤 최대 두 구간). */
    static List<DateRange> addedDates(LocalDate oldStart, LocalDate oldEnd, LocalDate newStart, LocalDate newEnd) {
        if (newEnd.isBefore(oldStart) || newStart.isAfter(oldEnd)) {
            return List.of(new DateRange(newStart, newEnd));
        }
        List<DateRange> added = new ArrayList<>();
        if (newStart.isBefore(oldStart)) {
            added.add(new DateRange(newStart, oldStart.minusDays(1)));
        }
        if (newEnd.isAfter(oldEnd)) {
            added.add(new DateRange(oldEnd.plusDays(1), newEnd));
        }
        return added;
    }

    @Transactional
    public void deleteBlackout(Long id, Long actorId) {
        blackoutRepository.findById(id).ifPresent(b -> {
            blackoutRepository.delete(b);
            announcementMessenger.blackout(Change.DELETED, schedule(b), null, actorId);
        });
    }

    private static Schedule schedule(BlackoutPeriod b) {
        return new Schedule(b.getName(), b.getStartDate(), b.getEndDate(), null);
    }
}
