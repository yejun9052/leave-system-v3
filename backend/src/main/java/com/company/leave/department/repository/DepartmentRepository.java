package com.company.leave.department.repository;

import com.company.leave.department.domain.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * 주어진 부서와 그 모든 하위 부서의 id를 재귀 CTE로 조회한다.
     * 부서 이동 시 순환참조(자기 자신 또는 하위로의 이동) 검증에 사용.
     */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM departments WHERE id = :rootId
                UNION ALL
                SELECT d.id FROM departments d
                JOIN subtree s ON d.parent_id = s.id
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<Long> findSubtreeIds(@Param("rootId") Long rootId);

    List<Department> findByParentIsNullOrderBySortOrderAscNameAsc();

    List<Department> findAllByOrderBySortOrderAscNameAsc();

    // 주의: Department 에 편의 getter getParentId()/getLeadId() 가 있어 파생 쿼리가
    // parent.id / lead.id 로 분해되지 않고 충돌한다. 따라서 명시적 JPQL 로 작성한다.
    @Query("select (count(d) > 0) from Department d where d.parent.id = :parentId")
    boolean existsByParentId(@Param("parentId") Long parentId);

    List<Department> findByName(String name);

    @Query("select d from Department d where d.lead.id = :leadId")
    List<Department> findByLeadId(@Param("leadId") Long leadId);
}
