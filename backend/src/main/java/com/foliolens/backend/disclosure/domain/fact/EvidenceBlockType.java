package com.foliolens.backend.disclosure.domain.fact;

/**
 * Evidence가 원문에서 가리키는 위치의 논리적 단위.
 */
public enum EvidenceBlockType {
    DOCUMENT_METADATA,
    SECTION,
    TITLE,
    HEADING,
    PARAGRAPH,
    TABLE,
    TABLE_ROW,
    TABLE_CELL,
    NOTE
}
