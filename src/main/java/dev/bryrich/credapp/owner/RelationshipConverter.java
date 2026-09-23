package dev.bryrich.credapp.owner;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RelationshipConverter
        implements AttributeConverter<Relationship, String> {

    @Override
    public String convertToDatabaseColumn(Relationship r) {
        return r == null ? null : r.dbValue();
    }

    @Override
    public Relationship convertToEntityAttribute(String value) {
        return value == null ? null : Relationship.fromDb(value);
    }
}
