package dev.bryrich.credapp.ssn;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SsnSubjectTypeConverter implements AttributeConverter<SsnSubjectType, String> {

    @Override
    public String convertToDatabaseColumn(SsnSubjectType type) {
        return type == null ? null : type.getValue();
    }

    @Override
    public SsnSubjectType convertToEntityAttribute(String value) {
        return value == null ? null : SsnSubjectType.fromValue(value);
    }
}
