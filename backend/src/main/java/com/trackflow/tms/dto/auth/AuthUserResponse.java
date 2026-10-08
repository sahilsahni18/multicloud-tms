package com.trackflow.tms.dto.auth;

import com.trackflow.tms.entity.RoleName;
import java.util.List;

/** The signed-in user as the frontend needs it (menus are built from roles). */
public record AuthUserResponse(Long id, String email, String fullName, List<RoleName> roles) {
}
