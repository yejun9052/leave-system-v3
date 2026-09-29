package com.company.leave.employee;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.dto.EmployeeRequests;
import com.company.leave.employee.dto.EmployeeResponse;
import com.company.leave.employee.dto.EmployeeSearchCondition;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.license.LicenseService;
import com.company.leave.security.SecurityUtils;
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EmployeeService {

    /** 관리자가 초기 비밀번호를 지정하지 않은 경우 사용하는 기본값. */
    public static final String DEFAULT_PASSWORD = "welcome1234!";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final LicenseService licenseService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           PasswordEncoder passwordEncoder,
                           ApplicationEventPublisher eventPublisher,
                           LicenseService licenseService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.licenseService = licenseService;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> search(EmployeeSearchCondition condition, Pageable pageable) {
        Set<Long> scope = currentScopeDeptIds();
        if (scope != null) {                       // 비관리자(팀장): 담당 부서로 제한
            condition = condition.withAllowedDepartmentIds(scope);
        }
        return employeeRepository.search(condition, pageable).map(EmployeeResponse::from);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        Employee employee = getManageable(id);
        Set<Long> scope = currentScopeDeptIds();
        if (scope != null
                && (employee.getDepartmentId() == null || !scope.contains(employee.getDepartmentId()))) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND); // 팀 범위 밖 → 없는 것으로 처리
        }
        return EmployeeResponse.from(employee);
    }

    /**
     * 현재 사용자의 조회 허용 부서 집합.
     * 관리자(SUPER/HR) → null(제한 없음). 그 외(팀장 등) → 리드하는 부서(하위 포함) 집합(없으면 빈 집합).
     */
    private Set<Long> currentScopeDeptIds() {
        UserPrincipal p = SecurityUtils.currentPrincipal();
        boolean admin = p.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SUPER_ADMIN") || a.getAuthority().equals("ROLE_HR_ADMIN"));
        if (admin) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        for (Department led : departmentRepository.findByLeadId(p.getId())) {
            ids.addAll(departmentRepository.findSubtreeIds(led.getId()));
        }
        return ids;
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequests.Create req) {
        // 동시 생성 직렬화 → 라이선스 최대 사용자 수 초과(TOCTOU) 방지
        employeeRepository.lockForUserCreation();
        licenseService.checkUserQuota(employeeRepository.countByStatus(EmployeeStatus.ACTIVE));
        validateEmailUnique(req.email(), null);
        validateEmployeeNoUnique(req.employeeNo(), null);

        String rawPassword = StringUtils.hasText(req.initialPassword())
                ? req.initialPassword() : DEFAULT_PASSWORD;

        Employee employee = Employee.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .name(req.name())
                .employeeNo(emptyToNull(req.employeeNo()))
                .department(resolveDepartment(req.departmentId()))
                .position(req.position())
                .phone(req.phone())
                .hireDate(req.hireDate())
                .roles(resolveRoles(req.roles()))
                .build();
        Employee saved = employeeRepository.save(employee);

        // 연차 엔진에 신규 입사자 알림 → 초기 연차 부여 (Phase 3)
        eventPublisher.publishEvent(new EmployeeCreatedEvent(saved.getId()));
        return EmployeeResponse.from(saved);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequests.Update req) {
        Employee employee = getManageable(id);
        validateEmailUnique(req.email(), id);
        validateEmployeeNoUnique(req.employeeNo(), id);

        employee.changeEmail(req.email());
        employee.updateProfile(req.name(), req.position(), req.phone());
        employee.changeEmployeeNo(emptyToNull(req.employeeNo()));
        employee.changeHireDate(req.hireDate());
        employee.assignDepartment(resolveDepartment(req.departmentId()));
        employee.replaceRoles(resolveRoles(req.roles()));
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public void resign(Long id, LocalDate resignedDate) {
        Employee employee = getManageable(id);
        employee.resign(resignedDate != null ? resignedDate : LocalDate.now());
    }

    @Transactional
    public void reactivate(Long id) {
        getManageable(id).reactivate();
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        getManageable(id).changePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void changeMyPassword(Long employeeId, String currentPassword, String newPassword) {
        Employee employee = getEntity(employeeId);
        if (!passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "현재 비밀번호가 올바르지 않습니다.");
        }
        employee.changePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public EmployeeResponse updateMyProfile(Long employeeId, EmployeeRequests.UpdateMyProfile req) {
        Employee employee = getEntity(employeeId);
        employee.updateProfile(req.name(), req.position(), req.phone());
        return EmployeeResponse.from(employee);
    }

    @Transactional(readOnly = true)
    public Employee getEntity(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }

    /**
     * 관리자가 id 로 다루는 대상 조회. 기본 시스템 관리자 계정은 목록에서 숨겨지므로
     * 단건 조회/수정/삭제 대상에서도 제외한다(존재하지 않는 것으로 처리).
     * 본인 셀프 조작(내 프로필/비밀번호)은 getEntity 를 그대로 사용한다.
     */
    private Employee getManageable(Long id) {
        Employee employee = getEntity(id);
        if (employee.isSystemAccount()) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }
        return employee;
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> allEmployeeIds() {
        return employeeRepository.findAllIds();
    }

    @Transactional(readOnly = true)
    public java.util.Set<Long> employeeIdsInDepartments(java.util.Collection<Long> departmentIds) {
        return new java.util.HashSet<>(employeeRepository.findIdsByDepartmentIdIn(departmentIds));
    }

    // --- helpers ---

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    private Set<Role> resolveRoles(Set<Role> roles) {
        return (roles == null || roles.isEmpty()) ? EnumSet.of(Role.EMPLOYEE) : roles;
    }

    private void validateEmailUnique(String email, Long selfId) {
        employeeRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
            }
        });
    }

    private void validateEmployeeNoUnique(String employeeNo, Long selfId) {
        if (!StringUtils.hasText(employeeNo)) {
            return;
        }
        employeeRepository.findByEmployeeNo(employeeNo.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new BusinessException(ErrorCode.EMPLOYEE_NO_DUPLICATED);
            }
        });
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** 신규 사용자 생성 이벤트 (연차 초기 부여 트리거). */
    public record EmployeeCreatedEvent(Long employeeId) {
    }
}
