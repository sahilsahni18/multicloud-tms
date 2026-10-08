package com.trackflow.tms.entity;

/** The four application roles. Spring Security sees them as ROLE_&lt;name&gt;. */
public enum RoleName {
    ADMIN,
    PROJECT_MANAGER,
    DEVELOPER,
    USER;

    public String authority() {
        return "ROLE_" + name();
    }
}
