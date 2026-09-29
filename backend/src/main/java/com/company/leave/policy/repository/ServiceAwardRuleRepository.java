package com.company.leave.policy.repository;

import com.company.leave.policy.domain.ServiceAwardRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceAwardRuleRepository extends JpaRepository<ServiceAwardRule, Long> {

    List<ServiceAwardRule> findAllByOrderByYearsAsc();

    Optional<ServiceAwardRule> findByYears(int years);
}
