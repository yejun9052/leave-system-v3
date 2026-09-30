package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveTypeDtos;
import com.company.leave.leave.repository.LeaveTypeRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final PolicyService policyService;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository, PolicyService policyService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.policyService = policyService;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeDtos.Response> list(boolean includeInactive) {
        List<LeaveType> types = includeInactive
                ? leaveTypeRepository.findAllByOrderBySortOrderAscIdAsc()
                : leaveTypeRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        LeavePolicy policy = policyService.getActivePolicy();
        return types.stream().map(t -> LeaveTypeDtos.Response.from(t, policy)).toList();
    }

    @Transactional
    public LeaveTypeDtos.Response create(LeaveTypeDtos.Create req) {
        if (leaveTypeRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 존재하는 휴가 코드입니다: " + req.code());
        }
        LeaveType type = new LeaveType(req.code(), req.name(), req.deductDays(), req.paid(),
                req.portion(), req.deductFromAnnual(), req.requiresAnnualExhausted(), req.colorHex(),
                req.sortOrder() != null ? req.sortOrder() : 0);
        return LeaveTypeDtos.Response.from(leaveTypeRepository.save(type), policyService.getActivePolicy());
    }

    @Transactional
    public LeaveTypeDtos.Response update(Long id, LeaveTypeDtos.Update req) {
        LeaveType type = getEntity(id);
        type.update(req.name(), req.deductDays(), req.paid(), req.portion(),
                req.deductFromAnnual(), req.requiresAnnualExhausted(), req.colorHex(),
                req.sortOrder() != null ? req.sortOrder() : 0, req.active());
        return LeaveTypeDtos.Response.from(type, policyService.getActivePolicy());
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
}
