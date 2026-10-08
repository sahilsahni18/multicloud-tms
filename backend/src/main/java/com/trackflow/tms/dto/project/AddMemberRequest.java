package com.trackflow.tms.dto.project;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotNull Long userId) {
}
