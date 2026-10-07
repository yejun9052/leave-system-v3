package com.company.leave.policy;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.mail.AnnouncementMailTemplates.Change;
import com.company.leave.mail.AnnouncementMailTemplates.Schedule;
import com.company.leave.notification.AnnouncementMessenger;
import com.company.leave.policy.domain.BlackoutPeriod;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.dto.PolicyRuleDtos;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyRulesService {

    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");

    private final ServiceAwardRuleRepository awardRepository;
    private final SpecialLeaveRuleRepository specialRepository;
    private final BlackoutPeriodRepository blackoutRepository;
    private final AnnouncementMessenger announcementMessenger;

    public PolicyRulesService(ServiceAwardRuleRepository awardRepository,
                              SpecialLeaveRuleRepository specialRepository,
                              BlackoutPeriodRepository blackoutRepository,
                              AnnouncementMessenger announcementMessenger) {
        this.awardRepository = awardRepository;
        this.specialRepository = specialRepository;
        this.blackoutRepository = blackoutRepository;
        this.announcementMessenger = announcementMessenger;
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

    /** 추가·변경·삭제 모두 재직 중인 전 직원에게 알림 + 메일(처리한 본인 제외, {@link AnnouncementMessenger}). */
    @Transactional
    public PolicyRuleDtos.Blackout createBlackout(PolicyRuleDtos.BlackoutRequest req, Long actorId) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종료일이 시작일보다 빠릅니다.");
        }
        BlackoutPeriod saved = blackoutRepository.save(new BlackoutPeriod(req.startDate(), req.endDate(), req.name()));
        announcementMessenger.blackout(Change.CREATED, schedule(saved), null, actorId);
        return PolicyRuleDtos.Blackout.from(saved);
    }

    @Transactional
    public PolicyRuleDtos.Blackout updateBlackout(Long id, PolicyRuleDtos.BlackoutRequest req, Long actorId) {
        BlackoutPeriod b = blackoutRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Schedule before = schedule(b);
        b.update(req.startDate(), req.endDate(), req.name());
        announcementMessenger.blackout(Change.UPDATED, schedule(b), before, actorId);
        return PolicyRuleDtos.Blackout.from(b);
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
