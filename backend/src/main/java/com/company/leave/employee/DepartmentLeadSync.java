package com.company.leave.employee;

import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 부서장(결재 팀장) 자동 지정. 팀장(TEAM_LEAD) 권한이 있는 재직자는 자기 부서의 부서장으로 본다.
 * <ul>
 *   <li>팀장 권한이 생기거나 그 상태로 부서에 들어왔는데 그 부서의 부서장이 비어 있으면 → 부서장으로 지정</li>
 *   <li>팀장 권한이 빠지거나, 부서장이던 부서를 떠나거나, 퇴사하면 → 그 부서의 부서장을 비우고
 *       같은 부서의 다른 재직 팀장(먼저 등록된 사람)으로 채운다</li>
 * </ul>
 * 부서 관리에서 직접 지정한 부서장은 이번 저장으로 위 조건이 생기지 않는 한 건드리지 않는다(직접 지정이 우선).
 * 결재·결재함·일정 범위는 모두 부서장 지정(departments.lead_id)을 기준으로 판단한다.
 */
@Component
public class DepartmentLeadSync {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public DepartmentLeadSync(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * 직원 저장(생성·수정·퇴사·복원) 뒤 호출.
     *
     * @param previousDepartmentId 저장 전 소속 부서(생성이면 null)
     * @param wasTeamLead          저장 전 팀장 권한 여부(생성이면 false)
     */
    public void afterSave(Employee employee, Long previousDepartmentId, boolean wasTeamLead) {
        boolean lead = isActiveTeamLead(employee);
        boolean lostRole = wasTeamLead && !lead;
        boolean movedOut = previousDepartmentId != null
                && !Objects.equals(previousDepartmentId, employee.getDepartmentId());
        if (lostRole || movedOut) {
            for (Department led : departmentRepository.findByLeadId(employee.getId())) {
                // 권한이 빠졌거나 퇴사했으면 맡던 부서 전부, 부서만 옮겼으면 떠난 부서만 비운다
                if (lostRole || led.getId().equals(previousDepartmentId)) {
                    led.assignLead(null);
                    fillFromTeamLeads(led, employee.getId());
                }
            }
        }
        Department department = employee.getDepartment();
        if (lead && department != null && department.getLead() == null) {
            department.assignLead(employee);
        }
    }

    /** 부서장이 빈 부서를 그 부서의 재직 팀장(먼저 등록된 사람)으로 채운다. 없으면 빈 채로 둔다. */
    private void fillFromTeamLeads(Department department, Long excludeId) {
        employeeRepository.findByDepartmentId(department.getId()).stream()
                .filter(e -> !e.getId().equals(excludeId))
                .filter(DepartmentLeadSync::isActiveTeamLead)
                .min(Comparator.comparing(Employee::getId))
                .ifPresent(department::assignLead);
    }

    static boolean isActiveTeamLead(Employee e) {
        return e.isActive() && !e.isSystemAccount() && e.hasRole(Role.TEAM_LEAD);
    }
}
