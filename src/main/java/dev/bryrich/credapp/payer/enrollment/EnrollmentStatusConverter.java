package dev.bryrich.credapp.payer.enrollment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EnrollmentStatusConverter implements AttributeConverter<EnrollmentStatus, String> {

    @Override
    public String convertToDatabaseColumn(EnrollmentStatus item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public EnrollmentStatus convertToEntityAttribute(String value) {
        return value == null ? null : EnrollmentStatus.fromValue(value);
    }
}
