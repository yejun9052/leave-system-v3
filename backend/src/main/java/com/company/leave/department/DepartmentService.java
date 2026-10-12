package com.company.leave.department;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.department.domain.Department;
import com.company.leave.department.dto.DepartmentRequests;
import com.company.leave.department.dto.DepartmentResponse;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final CalendarEventRepository calendarEventRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository,
                             CalendarEventRepository calendarEventRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.calendarEventRepository = calendarEventRepository;
    }

    /** 전체 부서를 트리 형태로 조회. */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getTree() {
        List<Department> all = departmentRepository.findAllByOrderBySortOrderAscNameAsc();
        Map<Long, Long> direct = memberCountMap();
        Map<Long, Long> total = withSubtree(all, direct);

        Map<Long, DepartmentResponse> nodes = new HashMap<>();
        for (Department d : all) {
            String leadName = d.getLead() != null ? d.getLead().getName() : null;
            nodes.put(d.getId(), DepartmentResponse.of(d, leadName,
                    total.getOrDefault(d.getId(), 0L), direct.getOrDefault(d.getId(), 0L)));
        }

        List<DepartmentResponse> roots = new ArrayList<>();
        for (Department d : all) {
            DepartmentResponse node = nodes.get(d.getId());
            if (d.getParentId() == null) {
                roots.add(node);
            } else {
                DepartmentResponse parent = nodes.get(d.getParentId());
                if (parent != null) {
                    parent.children().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getFlat() {
        List<Department> all = departmentRepository.findAllByOrderBySortOrderAscNameAsc();
        Map<Long, Long> direct = memberCountMap();
        Map<Long, Long> total = withSubtree(all, direct);
        return all.stream()
                .map(d -> DepartmentResponse.of(d,
                        d.getLead() != null ? d.getLead().getName() : null,
                        total.getOrDefault(d.getId(), 0L), direct.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequests.Create req) {
        Department parent = req.parentId() != null ? getEntity(req.parentId()) : null;
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 0;
        Department dept = new Department(req.name(), parent, sortOrder);
        if (req.leadId() != null) {
            dept.assignLead(leadCandidate(req.leadId()));
        }
        Department saved = departmentRepository.save(dept);
        return toResponse(saved);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequests.Update req) {
        Department dept = getEntity(id);
        dept.rename(req.name());
        if (req.sortOrder() != null) {
            dept.changeSortOrder(req.sortOrder());
        }
        // 부서장은 사용자 관리(팀장 권한·소속 부서)에서 자동으로 정해진다(DepartmentLeadSync).
        // 보내지 않으면 그대로 둔다: 이름만 고칠 때 화면에 남아 있던 옛 값으로 덮어써 부서장이 지워지지 않게
        if (req.leadId() != null) {
            dept.assignLead(leadCandidate(req.leadId()));
        }
        return toResponse(dept);
    }

    /** 부서 상·하위 이동. 자기 자신 또는 하위로의 이동은 순환이 되므로 금지. */
    @Transactional
    public DepartmentResponse move(Long id, DepartmentRequests.Move req) {
        Department dept = getEntity(id);
        Long newParentId = req.newParentId();

        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new BusinessException(ErrorCode.DEPARTMENT_CYCLE);
            }
            List<Long> subtree = departmentRepository.findSubtreeIds(id);
            if (subtree.contains(newParentId)) {
                throw new BusinessException(ErrorCode.DEPARTMENT_CYCLE);
            }
            dept.moveTo(getEntity(newParentId));
        } else {
            dept.moveTo(null);
        }
        return toResponse(dept);
    }

    @Transactional
    public void delete(Long id) {
        Department dept = getEntity(id);
        if (departmentRepository.existsByParentId(id)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_CHILDREN);
        }
        if (employeeRepository.countByDepartmentId(id) > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_MEMBERS);
        }
        // 그 부서 전용 일정만 지운다. 휴가 일정은 남기고 부서 칸만 비워진다(FK ON DELETE SET NULL).
        calendarEventRepository.deleteDepartmentEvents(id);
        departmentRepository.delete(dept);
    }

    @Transactional(readOnly = true)
    public Department getEntity(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    private Employee getEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }

    /** 부서장으로 지정할 수 있는 사람: 재직 중이고 팀장 이상 권한, 관리 전용 계정은 아님(결재함을 쓸 수 있어야 한다). */
    private Employee leadCandidate(Long id) {
        Employee e = getEmployee(id);
        if (!e.isActive() || e.isSystemAccount() || !e.hasRole(Role.TEAM_LEAD)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "부서장은 재직 중인 팀장 이상 권한의 직원만 지정할 수 있습니다: " + e.getName());
        }
        return e;
    }

    private DepartmentResponse toResponse(Department d) {
        Map<Long, Long> direct = memberCountMap();
        long own = direct.getOrDefault(d.getId(), 0L);
        long total = withSubtree(departmentRepository.findAllByOrderBySortOrderAscNameAsc(), direct)
                .getOrDefault(d.getId(), own);
        return DepartmentResponse.of(d,
                d.getLead() != null ? d.getLead().getName() : null,
                total, own);
    }

    /**
     * 하위 부서까지 합친 인원: 각 부서의 직속 인원을 자기와 모든 상위 부서에 더한다.
     * 목록에 없는 상위(고아)에서 멈추고, 혹시 모를 순환에서도 멈춘다.
     */
    static Map<Long, Long> withSubtree(List<Department> all, Map<Long, Long> direct) {
        Set<Long> ids = new HashSet<>();
        all.forEach(d -> ids.add(d.getId()));
        Map<Long, Long> total = new HashMap<>();
        for (Department d : all) {
            long count = direct.getOrDefault(d.getId(), 0L);
            Set<Long> seen = new HashSet<>();
            for (Department p = d; p != null && ids.contains(p.getId()) && seen.add(p.getId()); p = p.getParent()) {
                total.merge(p.getId(), count, Long::sum);
            }
        }
        return total;
    }

    private Map<Long, Long> memberCountMap() {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : employeeRepository.countGroupByDepartment()) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
