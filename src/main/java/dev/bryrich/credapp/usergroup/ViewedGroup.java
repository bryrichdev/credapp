package dev.bryrich.credapp.usergroup;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * A superuser looking at another user group the way its admin sees it, read-only. Held in
 * the signed-in session so it lasts until they go back to their own group or sign out.
 *
 * Only CurrentUserGroupResolver, GroupViewFilter and the page model read this, and all
 * three ignore it unless the signed-in account is a superuser.
 */
public final class ViewedGroup {

    static final String ID = "credapp.viewedUserGroupId";
    static final String NAME = "credapp.viewedUserGroupName";

    private ViewedGroup() {
    }

    public static void start(HttpSession session, UserGroup group) {
        session.setAttribute(ID, group.getId());
        session.setAttribute(NAME, group.getName());
    }

    public static void stop(HttpSession session) {
        session.removeAttribute(ID);
        session.removeAttribute(NAME);
    }

    public static Long id(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Long) session.getAttribute(ID);
    }

    public static String name(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(NAME);
    }

    /** The viewed group for the request on this thread, or null outside a web request. */
    public static Long currentId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return id(attributes.getRequest());
        }
        return null;
    }
}
