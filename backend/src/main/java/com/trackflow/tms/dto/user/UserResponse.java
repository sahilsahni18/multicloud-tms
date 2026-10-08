package com.trackflow.tms.dto.user;

import com.trackflow.tms.entity.RoleName;
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
}
