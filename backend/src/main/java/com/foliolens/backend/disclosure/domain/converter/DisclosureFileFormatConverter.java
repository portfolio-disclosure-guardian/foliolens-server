package com.foliolens.backend.disclosure.domain.converter;

import com.foliolens.backend.disclosure.domain.DisclosureFileFormat;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Java의 파일 형식 Enum과 DB 문자열을 서로 변환하는 JPA 변환기
 */
@Converter
public class DisclosureFileFormatConverter implements AttributeConverter<DisclosureFileFormat, String> {

    @Override
    public String convertToDatabaseColumn(DisclosureFileFormat attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.getValue();
    }

    @Override
    public DisclosureFileFormat convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        return DisclosureFileFormat.fromValue(dbData);
    }
}
