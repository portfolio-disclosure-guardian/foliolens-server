package com.foliolens.backend.disclosure.domain.converter;

import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Java의 Enum 값과 DB 문자열 값을 서로 변환하는 JPA 변환기
 * Java - DisclosureSourceGroup.EXCHANGE
 * DB   - exchange (소문자)
 * 따라서 저장할 때나, 조회할 때 변환
 * '@Enumerated(EnumType.STRING)만 사용하면 Enum 이름이 그대로 저장 -> DB에 대문자로 저장됨
 */
@Converter
public class DisclosureSourceGroupConverter implements AttributeConverter<DisclosureSourceGroup, String> {
    @Override
    public String convertToDatabaseColumn(DisclosureSourceGroup attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.getValue();
    }

    @Override
    public DisclosureSourceGroup convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        return DisclosureSourceGroup.fromValue(dbData);
    }
}
