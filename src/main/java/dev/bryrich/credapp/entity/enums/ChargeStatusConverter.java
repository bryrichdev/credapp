package dev.bryrich.credapp.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ChargeStatusConverter implements AttributeConverter<ChargeStatus, String> {

    @Override
    public String convertToDatabaseColumn(ChargeStatus item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public ChargeStatus convertToEntityAttribute(String value) {
        return value == null ? null : ChargeStatus.fromValue(value);
    }
}
