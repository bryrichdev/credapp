package dev.bryrich.credapp.payer.enrollment;

/** A payer row on the provider form. */
public class ProviderPayerForm extends EnrollmentForm {

    public static ProviderPayerForm from(ProviderPayer enrollment) {
        ProviderPayerForm form = new ProviderPayerForm();
        form.copyFrom(enrollment);
        return form;
    }
}
