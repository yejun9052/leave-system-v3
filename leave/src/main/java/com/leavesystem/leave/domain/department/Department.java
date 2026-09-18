package com.leavesystem.leave.domain.department;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import com.leavesystem.leave.domain.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서. 설계 문서 7장.
 *
 * <p>자기 참조 계층이며 깊이 제한을 두지 않는다. 하위 부서 조회는 재귀 CTE 로 처리한다.
 * {@code minStaffOnDuty} 가 0 이면 최소 잔류 인원을 제한하지 않는다(6.3).
 */
@Entity
@Getter
@Table(name = "department")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 상위 부서. 최상위 부서는 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    /** 부서 팀장. 미지정이면 상위 부서장이 1차 확인을 대행한다(6.1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    private Employee leader;

    /** 최소 잔류 인원. 직속 부서의 활성 사원만 모수로 센다. */
    @Column(name = "min_staff_on_duty", nullable = false)
    private int minStaffOnDuty;

    @Builder
    private Department(String name, Department parent, Employee leader, int minStaffOnDuty) {
        this.name = name;
        this.parent = parent;
        this.leader = leader;
        this.minStaffOnDuty = minStaffOnDuty;
    }
}
