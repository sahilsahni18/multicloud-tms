package com.trackflow.tms.dto.common;

import com.trackflow.tms.entity.User;

/** A user as shown next to tickets, comments and activity. */
public record UserRef(Long id, String fullName, String email) {

    public static UserRef of(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getFullName(), user.getEmail());
    }
}
