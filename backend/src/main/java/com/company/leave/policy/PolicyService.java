package com.company.leave.policy;

import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.dto.PolicyDtos;
import com.company.leave.policy.repository.LeavePolicyRepository;
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
        LeavePolicy policy = getActivePolicy();
        policy.apply(req.toSettings());
        return PolicyDtos.Response.from(policy);
    }
}
