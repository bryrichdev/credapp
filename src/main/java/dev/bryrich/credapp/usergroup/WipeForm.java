package dev.bryrich.credapp.usergroup;

/** The two things a superuser types to confirm a wipe. */
public class WipeForm {

    /** The group's name, retyped, so the wipe can't land on the wrong group by a slip. */
    private String confirmName;
    private String currentPassword;

    public String getConfirmName() {
        return confirmName;
    }

    public void setConfirmName(String confirmName) {
        this.confirmName = confirmName;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}
