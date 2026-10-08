package com.trackflow.tms.security;

import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal. Built from the database at login and from the
 * JWT claims on every other request (no DB lookup per request).
 */
@Getter
public final class AuthUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String fullName;
    private final Set<RoleName> roles;
    /** Only present during login; null when built from a JWT. */
    private final String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    private AuthUser(Long id, String email, String fullName, Set<RoleName> roles, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(RoleName.class) : EnumSet.copyOf(roles);
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = this.roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();
    }

    public static AuthUser fromEntity(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getFullName(), user.roleNames(),
                user.getPasswordHash(), user.isActive());
    }

    public static AuthUser fromToken(Long id, String email, String fullName, Set<RoleName> roles) {
        return new AuthUser(id, email, fullName, roles, null, true);
    }

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(RoleName... candidates) {
        for (RoleName candidate : candidates) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
