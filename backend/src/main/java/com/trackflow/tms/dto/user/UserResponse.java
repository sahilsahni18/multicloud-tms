package com.trackflow.tms.dto.user;

import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        boolean enabled,
        List<RoleName> roles,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.isEnabled(),
                user.roleNames().stream().sorted().toList(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
