package dev.bryrich.credapp.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerFormatTest {

    @Test
    void writesSavedValuesTheWayFormsAskForThem() {
        assertThat(AnswerFormat.DATE_MDY.apply("1984-03-09")).isEqualTo("03/09/1984");
        assertThat(AnswerFormat.DATE_MDY_DASH.apply("1984-03-09")).isEqualTo("03-09-1984");
        assertThat(AnswerFormat.DATE_MY.apply("2015-06-30")).isEqualTo("06/2015");
        assertThat(AnswerFormat.DATE_LONG.apply("2027-01-31")).isEqualTo("January 31, 2027");
        assertThat(AnswerFormat.SSN.apply("123456789")).isEqualTo("123-45-6789");
        assertThat(AnswerFormat.TAX_ID.apply("871234567")).isEqualTo("87-1234567");
        assertThat(AnswerFormat.PHONE_DASHES.apply("(801) 555 0142")).isEqualTo("801-555-0142");
        assertThat(AnswerFormat.PHONE_PARENS.apply("801.555.0142")).isEqualTo("(801) 555-0142");
        assertThat(AnswerFormat.DIGITS.apply("12-3456789")).isEqualTo("123456789");
        assertThat(AnswerFormat.UPPER.apply("Priya Shah")).isEqualTo("PRIYA SHAH");
    }

    @Test
    void leavesValuesThatDontFitForReview() {
        assertThat(AnswerFormat.DATE_MDY.apply("sometime in 2020")).isEqualTo("sometime in 2020");
        assertThat(AnswerFormat.PHONE_DASHES.apply("555-0142")).isEqualTo("555-0142");
        assertThat(AnswerFormat.SSN.apply("")).isEmpty();
        assertThat(AnswerFormat.of(null)).isEqualTo(AnswerFormat.AS_SAVED);
        assertThat(AnswerFormat.of("")).isEqualTo(AnswerFormat.AS_SAVED);
    }
}
