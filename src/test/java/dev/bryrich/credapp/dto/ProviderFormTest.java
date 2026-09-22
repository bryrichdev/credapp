package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.Provider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The four array columns are typed as comma-separated text and split on save. */
class ProviderFormTest {

    private ProviderForm form() {
        ProviderForm form = new ProviderForm();
        form.setFirstName("Ada");
        form.setLastName("Byron");
        return form;
    }

    @Test
    void splitsCommaSeparatedListsAndTrimsEachEntry() {
        ProviderForm form = form();
        form.setLanguages("English, Spanish ,  ASL");

        Provider provider = form.toEntity();

        assertThat(provider.getLanguages()).containsExactly("English", "Spanish", "ASL");
    }

    @Test
    void dropsBlankEntriesRatherThanStoringEmptyStrings() {
        ProviderForm form = form();
        form.setModalities("Telehealth, , In-person,");

        assertThat(form.toEntity().getModalities()).containsExactly("Telehealth", "In-person");
    }

    @Test
    void emptyTextBecomesAnEmptyListNotNull() {
        ProviderForm form = form();
        form.setPrevNames("   ");

        Provider provider = form.toEntity();

        assertThat(provider.getPrevNames()).isNotNull().isEmpty();
        assertThat(provider.getAreasOfExpertise()).isNotNull().isEmpty();
    }

    @Test
    void joinsListsBackIntoTextForTheEditScreen() {
        ProviderForm form = form();
        form.setLanguages("English, Spanish");
        Provider provider = form.toEntity();

        ProviderForm round = ProviderForm.from(provider);

        assertThat(round.getLanguages()).isEqualTo("English, Spanish");
    }

    @Test
    void uppercasesTheStateCode() {
        ProviderForm form = form();
        form.setState("mi");

        assertThat(form.toEntity().getState()).isEqualTo("MI");
    }

    @Test
    void buildsASingleLineAddressFromTheSplitFields() {
        ProviderForm form = form();
        form.setStreet1("55 Elm St");
        form.setCity("Grand Rapids");
        form.setState("MI");
        form.setZipCode("49503");

        assertThat(form.toEntity().getFormattedAddress())
                .isEqualTo("55 Elm St, Grand Rapids, MI 49503");
    }

    @Test
    void leavesTheAddressBlankWhenNothingIsOnFile() {
        assertThat(form().toEntity().getFormattedAddress()).isEmpty();
    }

    @Test
    void neverCarriesTheCaqhSecretReference() {
        ProviderForm form = form();
        form.setCaqhUsername("bryrich");

        Provider provider = form.toEntity();

        assertThat(provider.getCaqhUsername()).isEqualTo("bryrich");
        assertThat(provider.getCaqhSecretRef()).isNull();
    }
}
