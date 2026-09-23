package dev.bryrich.credapp.license;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LicenseStatusConverter implements AttributeConverter<LicenseStatus, String> {

    @Override
    public String convertToDatabaseColumn(LicenseStatus status) {
        return status == null ? null : status.getValue();
    }

    @Override
    public LicenseStatus convertToEntityAttribute(String value) {
        return value == null ? null : LicenseStatus.fromValue(value);
    }
}