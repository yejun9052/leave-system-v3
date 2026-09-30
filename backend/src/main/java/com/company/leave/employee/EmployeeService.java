package com.company.leave.employee;

import com.company.leave.auth.SessionTerminator;
import com.company.leave.auth.password.PasswordResetService;
import com.company.leave.auth.password.TemporaryPasswordGenerator;
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
import com.company.leave.mail.AccountMailEvents;
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

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final LicenseService licenseService;
    private final SessionTerminator sessionTerminator;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordResetService passwordResetService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           PasswordEncoder passwordEncoder,
                           ApplicationEventPublisher eventPublisher,
                           LicenseService licenseService,
                           SessionTerminator sessionTerminator,
                           TemporaryPasswordGenerator temporaryPasswordGenerator,
                           PasswordResetService passwordResetService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.licenseService = licenseService;
        this.sessionTerminator = sessionTerminator;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.passwordResetService = passwordResetService;
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
        Set<Role> roles = resolveRoles(req.roles());
        // 동시 생성 직렬화 → 라이선스 최대 사용자 수 초과(TOCTOU) 방지
        employeeRepository.lockForUserCreation();
        licenseService.checkUserQuota(employeeRepository.countByStatusAndSystemAccountFalse(EmployeeStatus.ACTIVE));
        validateEmailUnique(req.email(), null);

        // 초기 비밀번호는 서버가 생성해 메일로만 전달(관리자는 값을 알 수 없음) → 첫 로그인 시 변경 강제
        String temporaryPassword = temporaryPasswordGenerator.generate();

        Employee employee = Employee.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .name(req.name())
                .department(resolveDepartment(req.departmentId()))
                .position(req.position())
                .phone(req.phone())
                .hireDate(req.hireDate())
                .roles(roles)
                .build();
        employee.requirePasswordChange();
        Employee saved = employeeRepository.save(employee);

        // 연차 엔진에 신규 입사자 알림 → 초기 연차 부여 (Phase 3)
        eventPublisher.publishEvent(new EmployeeCreatedEvent(saved.getId()));
        // 계정 생성 메일(임시 비밀번호) — 커밋 후 발송
        eventPublisher.publishEvent(new AccountMailEvents.AccountCreated(
                saved.getEmail(), saved.getName(), temporaryPassword));
        return EmployeeResponse.from(saved);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequests.Update req) {
        Employee employee = getEntity(id);
        if (employee.isSystemAccount()) {
            throw new BusinessException(ErrorCode.SYSTEM_ACCOUNT_ROLE_IMMUTABLE);
        }
        Set<Role> roles = resolveRoles(req.roles());
        validateEmailUnique(req.email(), id);

        employee.changeEmail(req.email());
        employee.updateProfile(req.name(), req.position(), req.phone());
        employee.changeHireDate(req.hireDate());
        employee.assignDepartment(resolveDepartment(req.departmentId()));
        employee.replaceRoles(roles);
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public void resign(Long id, LocalDate resignedDate) {
        Employee employee = getManageable(id);
        employee.resign(resignedDate != null ? resignedDate : LocalDate.now());
        // 퇴사자의 로그인 세션 즉시 폐기 (이후 요청은 AccountStateFilter 에서도 차단됨)
        sessionTerminator.terminateAll(employee.getId());
    }

    @Transactional
    public void reactivate(Long id) {
        getManageable(id).reactivate();
    }

    /** 관리자 초기화: 비밀번호를 바꾸지 않고 본인에게 재설정 링크 메일만 보낸다. */
    @Transactional
    public void sendPasswordResetMail(Long id) {
        passwordResetService.issue(getManageable(id));
    }

    /** 본인 비밀번호 변경. 성공하면 변경 요구 해제(세션 처리는 호출부에서). */
    @Transactional
    public void changeMyPassword(Long employeeId, String currentPassword, String newPassword) {
        Employee employee = getEntity(employeeId);
        if (!passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        employee.setOwnPassword(passwordEncoder.encode(newPassword));
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

    /** 재직 중인 관리자(HR_ADMIN·SUPER_ADMIN) ID. 팀장 휴가 결재 알림 수신자. */
    @Transactional(readOnly = true)
    public java.util.List<Long> activeAdminIds() {
        return employeeRepository.findIdsByAnyRoleAndStatus(
                EnumSet.of(Role.HR_ADMIN, Role.SUPER_ADMIN), EmployeeStatus.ACTIVE);
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
        if (roles != null && roles.contains(Role.SUPER_ADMIN)) {
            throw new BusinessException(ErrorCode.SUPER_ADMIN_ROLE_RESTRICTED);
        }
        return (roles == null || roles.isEmpty()) ? EnumSet.of(Role.EMPLOYEE) : roles;
    }

    private void validateEmailUnique(String email, Long selfId) {
        employeeRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
            }
        });
    }

    /** 신규 사용자 생성 이벤트 (연차 초기 부여 트리거). */
    public record EmployeeCreatedEvent(Long employeeId) {
    }
}
