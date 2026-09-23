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
 * 사원과 단일 시스템 관리자 계정. 설계 문서 7장.
 *
 * <p>실제 사원은 사번 대신 이메일을 식별자로 쓴다. 퇴사자는 삭제하지 않고
 * {@code active=false} 로 비활성화하여 기존 신청·원장·이력을 보존한다.
 *
 * <p>SYS_ADMIN 행은 운영용 로그인 계정이며 실제 사원 수와 연차 대상에서 제외한다.
 * 이 행에는 이메일과 입사일 대신 별도 로그인 ID와 비밀번호 해시가 들어간다.
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

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", length = 150, unique = true)
    private String email;

    @Column(name = "login_id", length = 80, unique = true)
    private String loginId;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** 입사일. 연차 자동 부여·소멸의 기준이다(5.2). */
    @Column(name = "hire_date")
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

    public static Employee systemAdmin(String ownerName, String loginId, String passwordHash) {
        Employee account = new Employee(ownerName, null, null, Role.SYS_ADMIN, null, true);
        account.loginId = loginId;
        account.passwordHash = passwordHash;
        return account;
    }
}
