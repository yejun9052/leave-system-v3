package com.leavesystem.leave.domain.employee;

import com.leavesystem.leave.common.entity.BaseTimeEntity;
import com.leavesystem.leave.domain.department.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * 사원. 설계 문서 7장.
 *
 * <p>사번은 사용하지 않고 이메일을 식별자로 쓴다. 퇴사자는 삭제하지 않고
 * {@code active=false} 로 비활성화하여 기존 신청·원장·이력을 보존한다.
 *
 * <p>로그인 수단(자체 비밀번호 / 사내 계정 연동)은 설계 문서 14.1 의 미결 사항이므로
 * 인증 관련 필드는 아직 두지 않는다.
 */
@Entity
@Getter
@Table(name = "employee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /** 입사일. 연차 자동 부여·소멸의 기준이다(5.2). */
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** 재직 여부. 퇴사 시 false. */
    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    private Employee(String name, String email, LocalDate hireDate, Role role,
                     Department department, boolean active) {
        this.name = name;
        this.email = email;
        this.hireDate = hireDate;
        this.role = role;
        this.department = department;
        this.active = active;
    }
}
