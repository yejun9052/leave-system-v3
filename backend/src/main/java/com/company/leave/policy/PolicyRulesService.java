package com.company.leave.policy;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.policy.domain.BlackoutPeriod;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.dto.PolicyRuleDtos;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyRulesService {

    private final ServiceAwardRuleRepository awardRepository;
    private final SpecialLeaveRuleRepository specialRepository;
    private final BlackoutPeriodRepository blackoutRepository;

    public PolicyRulesService(ServiceAwardRuleRepository awardRepository,
                              SpecialLeaveRuleRepository specialRepository,
                              BlackoutPeriodRepository blackoutRepository) {
        this.awardRepository = awardRepository;
        this.specialRepository = specialRepository;
        this.blackoutRepository = blackoutRepository;
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
        SpecialLeaveRule r = new SpecialLeaveRule(req.name(), req.days(), req.leaveTypeCode(),
                req.sortOrder() != null ? req.sortOrder() : 0);
        return PolicyRuleDtos.SpecialRule.from(specialRepository.save(r));
    }

    @Transactional
    public PolicyRuleDtos.SpecialRule updateSpecial(Long id, PolicyRuleDtos.SpecialRuleRequest req) {
        SpecialLeaveRule r = specialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        r.update(req.name(), req.days(), req.leaveTypeCode(),
                req.sortOrder() != null ? req.sortOrder() : 0);
        return PolicyRuleDtos.SpecialRule.from(r);
    }

    @Transactional
    public void deleteSpecial(Long id) {
        specialRepository.deleteById(id);
    }

    // --- 블랙아웃 ---
    @Transactional(readOnly = true)
    public List<PolicyRuleDtos.Blackout> listBlackouts() {
        return blackoutRepository.findAllByOrderByStartDateAsc().stream()
                .map(PolicyRuleDtos.Blackout::from).toList();
    }

    @Transactional
    public PolicyRuleDtos.Blackout createBlackout(PolicyRuleDtos.BlackoutRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종료일이 시작일보다 빠릅니다.");
        }
        return PolicyRuleDtos.Blackout.from(
                blackoutRepository.save(new BlackoutPeriod(req.startDate(), req.endDate(), req.name())));
    }

    @Transactional
    public PolicyRuleDtos.Blackout updateBlackout(Long id, PolicyRuleDtos.BlackoutRequest req) {
        BlackoutPeriod b = blackoutRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        b.update(req.startDate(), req.endDate(), req.name());
        return PolicyRuleDtos.Blackout.from(b);
    }

    @Transactional
    public void deleteBlackout(Long id) {
        blackoutRepository.deleteById(id);
    }
}
