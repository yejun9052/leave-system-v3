package com.company.leave.policy;

import com.company.leave.leave.accrual.ServiceAwardLookup;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** 정책 › 포상 규칙(service_award_rules)에서 근속 연수가 딱 맞는 규칙의 포상 일수를 찾는다. */
@Component
public class ServiceAwardRuleLookup implements ServiceAwardLookup {

    private final ServiceAwardRuleRepository repository;

    public ServiceAwardRuleLookup(ServiceAwardRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal bonusDays(int completedYears) {
        if (completedYears < 1) {
            return BigDecimal.ZERO;
        }
        return repository.findByYears(completedYears).map(ServiceAwardRule::getBonusDays).orElse(BigDecimal.ZERO);
    }
}
