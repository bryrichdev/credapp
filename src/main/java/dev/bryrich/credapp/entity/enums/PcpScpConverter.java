package dev.bryrich.credapp.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PcpScpConverter implements AttributeConverter<PcpScp, String> {

    @Override
    public String convertToDatabaseColumn(PcpScp item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public PcpScp convertToEntityAttribute(String value) {
        return value == null ? null : PcpScp.fromValue(value);
    }
}
