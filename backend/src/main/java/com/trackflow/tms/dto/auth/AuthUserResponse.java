package com.trackflow.tms.dto.auth;

import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.security.AuthUser;
import java.util.List;
import java.util.Set;

/** The signed-in user as the frontend needs it (menus are built from roles). */
public record AuthUserResponse(Long id, String email, String fullName, List<RoleName> roles) {

    public static AuthUserResponse from(AuthUser user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getFullName(), sorted(user.getRoles()));
    }

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getFullName(), sorted(user.roleNames()));
    }

    private static List<RoleName> sorted(Set<RoleName> roles) {
        return roles.stream().sorted().toList();
    }
}
