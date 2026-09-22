package dev.bryrich.credapp.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PrivilegeStatusConverter implements AttributeConverter<PrivilegeStatus, String> {

    @Override
    public String convertToDatabaseColumn(PrivilegeStatus item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public PrivilegeStatus convertToEntityAttribute(String value) {
        return value == null ? null : PrivilegeStatus.fromValue(value);
    }
}
