package com.trackflow.tms.service;

/** Values of activity_logs.action and entity_type. */
public final class ActivityActions {

    public static final String PROJECT_CREATED = "PROJECT_CREATED";
    public static final String PROJECT_UPDATED = "PROJECT_UPDATED";
    public static final String PROJECT_DELETED = "PROJECT_DELETED";
    public static final String MEMBER_ADDED = "MEMBER_ADDED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String TICKET_CREATED = "TICKET_CREATED";
    public static final String TICKET_UPDATED = "TICKET_UPDATED";
    public static final String TICKET_ASSIGNED = "TICKET_ASSIGNED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String TICKET_DELETED = "TICKET_DELETED";
    public static final String COMMENT_ADDED = "COMMENT_ADDED";
    public static final String COMMENT_EDITED = "COMMENT_EDITED";
    public static final String COMMENT_DELETED = "COMMENT_DELETED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_ROLES_CHANGED = "USER_ROLES_CHANGED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String DEPLOYMENT_REQUESTED = "DEPLOYMENT_REQUESTED";
    public static final String DEPLOYMENT_DESTROY_REQUESTED = "DEPLOYMENT_DESTROY_REQUESTED";

    public static final String ENTITY_PROJECT = "PROJECT";
    public static final String ENTITY_TICKET = "TICKET";
    public static final String ENTITY_COMMENT = "COMMENT";
    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_DEPLOYMENT = "DEPLOYMENT";

    private ActivityActions() {
    }
}
