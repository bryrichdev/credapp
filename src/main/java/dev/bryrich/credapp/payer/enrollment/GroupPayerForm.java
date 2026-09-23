package dev.bryrich.credapp.payer.enrollment;

/** A payer row on the group form, with the payer's account rep for the group if it has one. */
public class GroupPayerForm extends EnrollmentForm {

    /** One of the payer's contacts; blank for no designated rep. */
    private Long accountRepId;

    public static GroupPayerForm from(GroupPayer enrollment) {
        GroupPayerForm form = new GroupPayerForm();
        form.copyFrom(enrollment);
        form.accountRepId = enrollment.getAccountRep() == null ? null : enrollment.getAccountRep().getId();
        return form;
    }

    public Long getAccountRepId() {
        return accountRepId;
    }

    public void setAccountRepId(Long accountRepId) {
        this.accountRepId = accountRepId;
    }
}
