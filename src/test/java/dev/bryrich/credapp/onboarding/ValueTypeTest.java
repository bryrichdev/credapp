package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.onboarding.xlsx.XlsxCell;
import dev.bryrich.credapp.provider.Sex;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTypeTest {

    @Test
    void numbersExcelStrippedOfLeadingZerosGetThemBack() throws Exception {
        assertThat(ValueType.ZIP.convert(number("2134"))).isEqualTo("02134");
        assertThat(ValueType.ZIP.convert(text("43604-1234"))).isEqualTo("43604-1234");
        assertThat(ValueType.digits(9, "TIN").convert(number("12345678"))).isEqualTo("012345678");
        assertThat(ValueType.digits(9, "SSN").convert(text("123-45-6789"))).isEqualTo("123456789");
        assertThat(ValueType.digits(10, "NPI").convert(number("1234567890"))).isEqualTo("1234567890");
    }

    @Test
    void datesComeInSeveralShapes() throws Exception {
        LocalDate date = LocalDate.of(2025, 1, 31);
        assertThat(ValueType.DATE.convert(text("2025-01-31"))).isEqualTo(date);
        assertThat(ValueType.DATE.convert(text("1/31/2025"))).isEqualTo(date);
        assertThat(ValueType.DATE.convert(number("45688"))).isEqualTo(date);
        assertThatThrownBy(() -> ValueType.DATE.convert(text("2/30/2025")))
                .hasMessage("\"2/30/2025\" isn't a date. Use 2025-01-31 or 1/31/2025");
        assertThatThrownBy(() -> ValueType.DATE.convert(text("31/1/2025"))).isInstanceOf(ValueType.BadValue.class);
    }

    @Test
    void percentagesReadTheSameWhicheverWayTheyWereTyped() throws Exception {
        assertThat((BigDecimal) ValueType.PERCENT.convert(number("25"))).isEqualByComparingTo("25");
        assertThat((BigDecimal) ValueType.PERCENT.convert(text("25%"))).isEqualByComparingTo("25");
        assertThat((BigDecimal) ValueType.PERCENT.convert(new XlsxCell(XlsxCell.Kind.PERCENT, "0.255",
                new BigDecimal("0.255"), null))).isEqualByComparingTo("25.5");
        assertThat((BigDecimal) ValueType.MONEY.convert(text("$1,000,000"))).isEqualByComparingTo("1000000");
    }

    @Test
    void yesNoAndChoicesAreForgivingAboutSpelling() throws Exception {
        assertThat(ValueType.YES_NO.convert(text("y"))).isEqualTo(true);
        assertThat(ValueType.YES_NO.convert(XlsxCell.bool(false))).isEqualTo(false);
        ValueType sex = ValueType.choice(Sex.class, Sex::getLabel, Map.of("F", Sex.FEMALE));
        assertThat(sex.convert(text("female"))).isEqualTo(Sex.FEMALE);
        assertThat(sex.convert(text("F"))).isEqualTo(Sex.FEMALE);
        assertThat(sex.choices()).containsExactly("Male", "Female", "Other", "Unknown");
        assertThatThrownBy(() -> sex.convert(text("?")))
                .hasMessage("\"?\" isn't one of: Male, Female, Other, Unknown");
    }

    @Test
    void anExcelErrorIsNeverTakenAsAValue() {
        assertThatThrownBy(() -> ValueType.TEXT.convert(XlsxCell.error("#REF!")))
                .hasMessage("The cell shows an Excel error (#REF!)");
    }

    private static XlsxCell text(String value) {
        return XlsxCell.text(value);
    }

    private static XlsxCell number(String value) {
        return new XlsxCell(XlsxCell.Kind.NUMBER, value, new BigDecimal(value), null);
    }
}
