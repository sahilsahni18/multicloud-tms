package com.trackflow.tms.util;

import java.util.Locale;

public final class EmailUtils {

    private EmailUtils() {
    }

    /** Emails are stored and compared trimmed and lower-case. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
