package com.company.leave.policy.repository;

import com.company.leave.policy.domain.SpecialLeaveRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialLeaveRuleRepository extends JpaRepository<SpecialLeaveRule, Long> {

    List<SpecialLeaveRule> findAllByOrderBySortOrderAscIdAsc();

    /** 해당 휴가 종류에 연결된 규정(신청 때 선택). */
    List<SpecialLeaveRule> findByLeaveTypeCodeOrderBySortOrderAscIdAsc(String leaveTypeCode);
}
