package dev.bryrich.credapp.malpractice;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CoverageScopeConverter implements AttributeConverter<CoverageScope, String> {

    @Override
    public String convertToDatabaseColumn(CoverageScope item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public CoverageScope convertToEntityAttribute(String value) {
        return value == null ? null : CoverageScope.fromValue(value);
    }
}
