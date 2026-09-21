package dev.bryrich.credapp.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SexConverter implements AttributeConverter<Sex, String> {

    @Override
    public String convertToDatabaseColumn(Sex sex) {
        return sex == null ? null : sex.getValue();
    }

    @Override
    public Sex convertToEntityAttribute(String value) {
        return value == null ? null : Sex.fromValue(value);
    }
}
