package dev.bryrich.credapp.onboarding;

import java.io.Serializable;

/**
 * An upload that previewed clean, held in the session until it's imported or dropped. The
 * token ties the Import button to this exact upload, and userGroupId to the group it was
 * previewed for, so switching groups in between can't send it somewhere else.
 */
record PendingImport(String token, String fileName, byte[] bytes, Long userGroupId) implements Serializable {

    static final String SESSION_KEY = "credapp.pendingImport";
}
