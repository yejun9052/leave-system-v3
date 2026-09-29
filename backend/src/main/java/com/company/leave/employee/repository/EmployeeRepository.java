package com.company.leave.employee.repository;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, EmployeeRepositoryCustom {

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmployeeNo(String employeeNo);

    boolean existsByEmail(String email);

    boolean existsByEmployeeNo(String employeeNo);

    // 주의: Employee 에는 편의 getter getDepartmentId() 가 있어 파생 쿼리(findByDepartmentId)가
    // department.id 로 분해되지 않고 충돌한다. 따라서 명시적 JPQL 로 작성한다.
    @Query("select e from Employee e where e.department.id = :departmentId")
    List<Employee> findByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("select count(e) from Employee e where e.department.id = :departmentId")
    long countByDepartmentId(@Param("departmentId") Long departmentId);

    List<Employee> findByStatus(EmployeeStatus status);

    long countByStatus(EmployeeStatus status);

    @Query("select count(e) from Employee e")
    long countAll();

    /** 부서별 소속 인원 수: [departmentId, count] */
    @Query("select e.department.id, count(e) from Employee e "
            + "where e.department.id is not null group by e.department.id")
    List<Object[]> countGroupByDepartment();

    @Query("select e from Employee e where e.department.id in :departmentIds")
    List<Employee> findByDepartmentIdIn(@Param("departmentIds") List<Long> departmentIds);

    @Query("select e.id from Employee e")
    List<Long> findAllIds();

    @Query("select e.id from Employee e where e.department.id in :deptIds")
    List<Long> findIdsByDepartmentIdIn(@Param("deptIds") java.util.Collection<Long> deptIds);

    /**
     * 사용자 생성 직렬화용 트랜잭션 advisory lock (라이선스 인원 TOCTOU 방지).
     * 커밋/롤백 시 자동 해제된다. (PostgreSQL 전용)
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(90001)) t", nativeQuery = true)
    Integer lockForUserCreation();
}
