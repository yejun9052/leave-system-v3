package com.company.leave.policy;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.dto.PolicyDtos;
import com.company.leave.policy.repository.LeavePolicyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {

    private final LeavePolicyRepository policyRepository;

    public PolicyService(LeavePolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /** 활성 정책을 반환하며, 없으면 기본 정책을 생성한다. */
    @Transactional
    public LeavePolicy getActivePolicy() {
        return policyRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(() -> policyRepository.save(LeavePolicy.createDefault()));
    }

    @Transactional
    public PolicyDtos.Response get() {
        return PolicyDtos.Response.from(getActivePolicy());
    }

    @Transactional
    public PolicyDtos.Response update(PolicyDtos.UpdateRequest req) {
        // 회계연도 시작일은 그 달에 있는 날이어야 한다(2월 30일·4월 31일 불가, 2월은 평년 기준 28일까지)
        int lastDay = java.time.YearMonth.of(2025, req.fiscalStartMonth()).lengthOfMonth();
        if (req.fiscalStartDay() > lastDay) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회계연도 시작일: " + req.fiscalStartMonth() + "월은 "
                    + lastDay + "일까지 있습니다.");
        }
        LeavePolicy policy = getActivePolicy();
        policy.apply(req.toSettings());
        policy.changeBlackoutConflictMode(req.blackoutConflictMode());
        return PolicyDtos.Response.from(policy);
    }

    /** 자동화 설정(연차 촉진 자동 발송 ON/OFF·발송 시기) 저장. 연차 정책 저장과 따로 한다. */
    @Transactional
    public LeavePolicy updateAutomation(boolean promotionEnabled, List<Integer> promotionMonths) {
        LeavePolicy policy = getActivePolicy();
        try {
            policy.applyAutomation(promotionEnabled, promotionMonths);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
        return policy;
    }
}
