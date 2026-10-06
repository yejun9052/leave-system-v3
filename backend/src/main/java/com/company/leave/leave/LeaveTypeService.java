package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveTypeDtos;
import com.company.leave.leave.repository.LeaveTypeRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final PolicyService policyService;
    private final SpecialLeaveRuleRepository specialRuleRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository, PolicyService policyService,
                            SpecialLeaveRuleRepository specialRuleRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.policyService = policyService;
        this.specialRuleRepository = specialRuleRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeDtos.Response> list(boolean includeInactive) {
        List<LeaveType> types = includeInactive
                ? leaveTypeRepository.findAllByOrderBySortOrderAscIdAsc()
                : leaveTypeRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        LeavePolicy policy = policyService.getActivePolicy();
        Map<String, List<SpecialLeaveRule>> rulesByCode = specialRuleRepository.findAllByOrderBySortOrderAscIdAsc()
                .stream()
                .filter(r -> r.getLeaveTypeCode() != null)
                .collect(Collectors.groupingBy(SpecialLeaveRule::getLeaveTypeCode));
        return types.stream()
                .map(t -> LeaveTypeDtos.Response.from(t, policy, rulesByCode.getOrDefault(t.getCode(), List.of())))
                .toList();
    }

    @Transactional
    public LeaveTypeDtos.Response create(LeaveTypeDtos.Create req) {
        if (leaveTypeRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 존재하는 휴가 코드입니다: " + req.code());
        }
        LeaveType type = new LeaveType(req.code(), req.name(), req.deductDays(), req.paid(),
                req.portion(), req.annualDeductionMode(), req.colorHex(),
                req.sortOrder() != null ? req.sortOrder() : 0);
        type.allowDuringBlackout(Boolean.TRUE.equals(req.allowedDuringBlackout()));
        return toResponse(leaveTypeRepository.save(type));
    }

    @Transactional
    public LeaveTypeDtos.Response update(Long id, LeaveTypeDtos.Update req) {
        LeaveType type = getEntity(id);
        type.update(req.name(), req.deductDays(), req.paid(), req.portion(),
                req.annualDeductionMode(), req.colorHex(),
                req.sortOrder() != null ? req.sortOrder() : 0, req.active());
        if (req.allowedDuringBlackout() != null) {
            type.allowDuringBlackout(req.allowedDuringBlackout());
        }
        return toResponse(type);
    }

    @Transactional
    public void delete(Long id) {
        // 사용 이력이 있을 수 있으므로 비활성화 처리 권장. 물리 삭제는 참조 없을 때만.
        LeaveType type = getEntity(id);
        leaveTypeRepository.delete(type);
    }

    @Transactional(readOnly = true)
    public LeaveType getEntity(Long id) {
        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_TYPE_NOT_FOUND));
    }

    /** 이 종류에 연결된 경조사 규정(신청 때 선택). */
    @Transactional(readOnly = true)
    public List<SpecialLeaveRule> specialRulesOf(LeaveType type) {
        return specialRuleRepository.findByLeaveTypeCodeOrderBySortOrderAscIdAsc(type.getCode());
    }

    private LeaveTypeDtos.Response toResponse(LeaveType type) {
        return LeaveTypeDtos.Response.from(type, policyService.getActivePolicy(), specialRulesOf(type));
    }
}
