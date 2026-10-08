package com.trackflow.tms.dto.project;

import com.trackflow.tms.entity.RoleName;
import java.util.List;

public record MemberResponse(Long id, String fullName, String email, List<RoleName> roles, boolean owner) {
}
