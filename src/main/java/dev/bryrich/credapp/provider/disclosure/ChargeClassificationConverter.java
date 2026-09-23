package dev.bryrich.credapp.provider.disclosure;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ChargeClassificationConverter implements AttributeConverter<ChargeClassification, String> {

    @Override
    public String convertToDatabaseColumn(ChargeClassification item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public ChargeClassification convertToEntityAttribute(String value) {
        return value == null ? null : ChargeClassification.fromValue(value);
    }
}
