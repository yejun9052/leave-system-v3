package com.company.leave.employee.repository;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, EmployeeRepositoryCustom {

    Optional<Employee> findByEmail(String email);

    /** 대소문자를 가리지 않고 같은 이메일(중복 검사용). */
    List<Employee> findAllByEmailIgnoreCase(String email);

    // 주의: Employee 에는 편의 getter getDepartmentId() 가 있어 파생 쿼리(findByDepartmentId)가
    // department.id 로 분해되지 않고 충돌한다. 따라서 명시적 JPQL 로 작성한다.
    @Query("select e from Employee e where e.department.id = :departmentId")
    List<Employee> findByDepartmentId(@Param("departmentId") Long departmentId);

    /** 부서 소속 계정 수(관리 전용 계정 포함). 부서 삭제 가능 여부 판단용. */
    @Query("select count(e) from Employee e where e.department.id = :departmentId")
    long countByDepartmentId(@Param("departmentId") Long departmentId);

    /** 상태별 전체(관리 전용 계정 포함). 연차 부여용: 관리 전용 계정도 휴가를 쓸 수 있다. */
    List<Employee> findByStatus(EmployeeStatus status);

    /** 해당 상태의 직원 수(관리 전용 계정 제외). 라이선스 인원. */
    long countByStatusAndSystemAccountFalse(EmployeeStatus status);

    /** 해당 상태의 직원 수(관리 전용 계정 포함). 대시보드 인원. */
    long countByStatus(EmployeeStatus status);

    @Query("select count(e) from Employee e")
    long countAll();

    /** 부서별 재직 인원 수(퇴사자·관리 전용 계정 제외): [departmentId, count] */
    @Query("select e.department.id, count(e) from Employee e "
            + "where e.department.id is not null and e.status = com.company.leave.employee.domain.EmployeeStatus.ACTIVE "
            + "and e.systemAccount = false group by e.department.id")
    List<Object[]> countGroupByDepartment();

    /** 한 부서(하위 부서 제외)의 재직 인원 수(퇴사자·관리 전용 계정 제외) */
    @Query("select count(e) from Employee e where e.department.id = :departmentId "
            + "and e.status = com.company.leave.employee.domain.EmployeeStatus.ACTIVE and e.systemAccount = false")
    long countActiveStaff(@Param("departmentId") Long departmentId);

    @Query("select e.id from Employee e")
    List<Long> findAllIds();

    @Query("select e.id from Employee e where e.department.id in :deptIds")
    List<Long> findIdsByDepartmentIdIn(@Param("deptIds") java.util.Collection<Long> deptIds);

    /** 주어진 역할 중 하나라도 가진, 해당 상태의 직원 ID (예: 결재 알림을 받을 재직 관리자). */
    @Query("select e.id from Employee e where e.role in :roles and e.status = :status")
    List<Long> findIdsByAnyRoleAndStatus(@Param("roles") java.util.Collection<Role> roles,
                                         @Param("status") EmployeeStatus status);

    /**
     * 사용자 생성 직렬화용 트랜잭션 advisory lock (라이선스 인원 TOCTOU 방지).
     * 커밋/롤백 시 자동 해제된다. (PostgreSQL 전용)
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(90001)) t", nativeQuery = true)
    Integer lockForUserCreation();
}
