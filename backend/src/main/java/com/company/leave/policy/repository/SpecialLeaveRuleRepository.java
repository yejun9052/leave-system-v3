package com.company.leave.policy.repository;

import com.company.leave.policy.domain.SpecialLeaveRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialLeaveRuleRepository extends JpaRepository<SpecialLeaveRule, Long> {

    List<SpecialLeaveRule> findAllByOrderBySortOrderAscIdAsc();
}
