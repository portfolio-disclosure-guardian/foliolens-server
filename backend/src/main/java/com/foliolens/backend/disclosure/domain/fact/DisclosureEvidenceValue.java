package com.foliolens.backend.disclosure.domain.fact;

/**
 * Fact 판단에 사용한 원문과 표 머리글·행 레이블·단위 정보.
 * 정규화된 값은 Evidence가 아니라 DisclosureFact에 저장한다.
 */
public record DisclosureEvidenceValue(
        String sourceText,
        String rowLabel,
        String columnLabel,
        String rawValue,
        String rawUnit,
        String noteText
) {

    public DisclosureEvidenceValue {
        sourceText = requireText(sourceText, "sourceText");
        rowLabel = normalizeOptionalText(rowLabel);
        columnLabel = normalizeOptionalText(columnLabel);
        rawValue = normalizeOptionalText(rawValue);
        rawUnit = normalizeOptionalText(rawUnit);
        noteText = normalizeOptionalText(noteText);

        if (rawUnit != null && rawValue == null) {
            throw new IllegalArgumentException(
                    "rawUnit이 있으면 rawValue도 필요합니다."
            );
        }
    }

    public boolean hasRawValue() {
        return rawValue != null;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "는 비어 있을 수 없습니다."
            );
        }
        return value.strip();
    }
}
