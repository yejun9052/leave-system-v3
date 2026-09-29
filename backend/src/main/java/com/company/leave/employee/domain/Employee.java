package com.company.leave.employee.domain;

import com.company.leave.common.entity.BaseTimeEntity;
import com.company.leave.department.domain.Department;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * 사용자(직원).
 */
@Entity
@Table(name = "employees")
public class Employee extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(length = 50)
    private String position;

    @Column(length = 30)
    private String phone;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "resigned_date")
    private LocalDate resignedDate;

    /** 기본 제공 시스템 관리자 여부. true 면 사용자(계정) 목록에서 숨겨진다. */
    @Column(name = "system_account", nullable = false)
    private boolean systemAccount = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_roles", joinColumns = @JoinColumn(name = "employee_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    private Set<Role> roles = EnumSet.of(Role.EMPLOYEE);

    protected Employee() {
    }

    private Employee(Builder b) {
        this.email = b.email;
        this.passwordHash = b.passwordHash;
        this.name = b.name;
        this.employeeNo = b.employeeNo;
        this.department = b.department;
        this.position = b.position;
        this.phone = b.phone;
        this.hireDate = b.hireDate;
        this.status = b.status != null ? b.status : EmployeeStatus.ACTIVE;
        this.roles = (b.roles == null || b.roles.isEmpty())
                ? EnumSet.of(Role.EMPLOYEE) : EnumSet.copyOf(b.roles);
        this.systemAccount = b.systemAccount;
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- 도메인 동작 ---

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public void updateProfile(String name, String position, String phone) {
        this.name = name;
        this.position = position;
        this.phone = phone;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public void assignDepartment(Department department) {
        this.department = department;
    }

    public void changeHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public void replaceRoles(Set<Role> roles) {
        this.roles = (roles == null || roles.isEmpty())
                ? EnumSet.of(Role.EMPLOYEE) : EnumSet.copyOf(roles);
    }

    public void resign(LocalDate date) {
        this.status = EmployeeStatus.RESIGNED;
        this.resignedDate = date;
    }

    public void reactivate() {
        this.status = EmployeeStatus.ACTIVE;
        this.resignedDate = null;
    }

    public boolean isActive() {
        return status == EmployeeStatus.ACTIVE;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return roles.contains(Role.SUPER_ADMIN) || roles.contains(Role.HR_ADMIN);
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public Department getDepartment() {
        return department;
    }

    public Long getDepartmentId() {
        return department != null ? department.getId() : null;
    }

    public String getPosition() {
        return position;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public LocalDate getResignedDate() {
        return resignedDate;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isSystemAccount() {
        return systemAccount;
    }

    public static final class Builder {
        private String email;
        private String passwordHash;
        private String name;
        private String employeeNo;
        private Department department;
        private String position;
        private String phone;
        private LocalDate hireDate;
        private EmployeeStatus status;
        private Set<Role> roles;
        private boolean systemAccount;

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder employeeNo(String employeeNo) {
            this.employeeNo = employeeNo;
            return this;
        }

        public Builder department(Department department) {
            this.department = department;
            return this;
        }

        public Builder position(String position) {
            this.position = position;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder hireDate(LocalDate hireDate) {
            this.hireDate = hireDate;
            return this;
        }

        public Builder status(EmployeeStatus status) {
            this.status = status;
            return this;
        }

        public Builder roles(Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public Builder systemAccount(boolean systemAccount) {
            this.systemAccount = systemAccount;
            return this;
        }

        public Employee build() {
            return new Employee(this);
        }
    }
}
