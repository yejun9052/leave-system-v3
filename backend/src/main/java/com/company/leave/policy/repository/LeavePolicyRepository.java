package com.company.leave.policy.repository;

import com.company.leave.policy.domain.LeavePolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {

    Optional<LeavePolicy> findFirstByActiveTrueOrderByIdAsc();
}
