package com.company.leave.security;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 인증된 사용자 principal. Spring Security 컨텍스트에 저장된다.
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String name;
    private final Long departmentId;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    private UserPrincipal(Long id, String email, String password, String name,
                          Long departmentId, boolean active, List<GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.departmentId = departmentId;
        this.active = active;
        this.authorities = authorities;
    }

    public static UserPrincipal from(Employee e) {
        List<GrantedAuthority> auths = e.getRoles().stream()
                .map(Role::authority)
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
        return new UserPrincipal(e.getId(), e.getEmail(), e.getPasswordHash(), e.getName(),
                e.getDepartmentId(), e.isActive(), auths);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
