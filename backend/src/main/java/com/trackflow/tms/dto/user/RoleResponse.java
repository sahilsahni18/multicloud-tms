package com.trackflow.tms.dto.user;

import com.trackflow.tms.entity.RoleName;

public record RoleResponse(RoleName name, String description, long userCount) {
}
