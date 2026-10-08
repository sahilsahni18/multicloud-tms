package com.trackflow.tms.dto.common;

/** A user as shown next to tickets, comments and activity. */
public record UserRef(Long id, String fullName, String email) {
}
