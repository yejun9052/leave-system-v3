package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveTypeDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.leave.repository.LeaveTypeRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final PolicyService policyService;
    private final SpecialLeaveRuleRepository specialRuleRepository;
    /** 없으면(단위 테스트) 진행 중인 휴가 검사를 하지 않는다 */
    private final LeaveRequestRepository requestRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository, PolicyService policyService,
                            SpecialLeaveRuleRepository specialRuleRepository) {
        this(leaveTypeRepository, policyService, specialRuleRepository, null);
    }

    @Autowired
    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository, PolicyService policyService,
                            SpecialLeaveRuleRepository specialRuleRepository,
                            LeaveRequestRepository requestRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.policyService = policyService;
        this.specialRuleRepository = specialRuleRepository;
        this.requestRepository = requestRepository;
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
        requireNoOpenRequestsIfRulesChange(type, req);
        type.update(req.name(), req.deductDays(), req.paid(), req.portion(),
                req.annualDeductionMode(), req.colorHex(),
                req.sortOrder() != null ? req.sortOrder() : 0, req.active());
        if (req.allowedDuringBlackout() != null) {
            type.allowDuringBlackout(req.allowedDuringBlackout());
        }
        return toResponse(type);
    }

    /**
     * 차감 방식·단위(종일·반차·시간차)는 이 종류로 결재 대기·승인·취소 요청 중인 휴가 중 아직 끝나지 않은 것이 있으면 바꿀 수 없다.
     * 이미 신청한 휴가의 일수·겹침·공휴일 재계산이 새 규칙으로 다시 해석되면 연차가 어긋난다. 새 규칙은 새 종류로 만든다.
     */
    private void requireNoOpenRequestsIfRulesChange(LeaveType type, LeaveTypeDtos.Update req) {
        boolean modeChanged = req.annualDeductionMode() != null && req.annualDeductionMode() != type.getAnnualDeductionMode();
        boolean portionChanged = req.portion() != null && req.portion() != type.getPortion();
        if (requestRepository == null || (!modeChanged && !portionChanged)) {
            return;
        }
        long open = requestRepository.countByLeaveTypeIdAndStatusInAndEndDateGreaterThanEqual(type.getId(),
                EnumSet.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCEL_REQUESTED),
                LocalDate.now());
        if (open > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "'" + type.getName() + "'으로 진행 중이거나 앞으로 쓸 휴가가 "
                    + open + "건 있어 " + (modeChanged ? "차감 방식" : "단위") + "을 바꿀 수 없습니다. "
                    + "새 휴가 종류를 만들어 쓰고 이 종류는 사용 안 함으로 두세요.");
        }
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
