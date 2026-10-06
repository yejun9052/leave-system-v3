package com.company.leave.department;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.department.domain.Department;
import com.company.leave.department.dto.DepartmentRequests;
import com.company.leave.department.dto.DepartmentResponse;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        Map<Long, Long> memberCounts = memberCountMap();

        Map<Long, DepartmentResponse> nodes = new HashMap<>();
        for (Department d : all) {
            String leadName = d.getLead() != null ? d.getLead().getName() : null;
            nodes.put(d.getId(), DepartmentResponse.of(d, leadName,
                    memberCounts.getOrDefault(d.getId(), 0L)));
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
        Map<Long, Long> memberCounts = memberCountMap();
        return departmentRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(d -> DepartmentResponse.of(d,
                        d.getLead() != null ? d.getLead().getName() : null,
                        memberCounts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequests.Create req) {
        Department parent = req.parentId() != null ? getEntity(req.parentId()) : null;
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 0;
        Department dept = new Department(req.name(), parent, sortOrder);
        if (req.leadId() != null) {
            dept.assignLead(getEmployee(req.leadId()));
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
        dept.assignLead(req.leadId() != null ? getEmployee(req.leadId()) : null);
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

    private DepartmentResponse toResponse(Department d) {
        return DepartmentResponse.of(d,
                d.getLead() != null ? d.getLead().getName() : null,
                employeeRepository.countMembersByDepartmentId(d.getId()));
    }

    private Map<Long, Long> memberCountMap() {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : employeeRepository.countGroupByDepartment()) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
