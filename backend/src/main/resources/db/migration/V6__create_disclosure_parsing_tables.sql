CREATE TABLE disclosure_sections (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disclosure_document_id UUID NOT NULL,
    parent_section_id      UUID,
    section_level          INTEGER NOT NULL,
    sequence_no            INTEGER NOT NULL,
    title                  TEXT,
    source_line_start      INTEGER NOT NULL,
    source_line_end        INTEGER NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_sections_document_sequence
        UNIQUE (disclosure_document_id, sequence_no),
    CONSTRAINT uq_disclosure_sections_id_document
        UNIQUE (id, disclosure_document_id),
    CONSTRAINT fk_disclosure_sections_document
        FOREIGN KEY (disclosure_document_id)
        REFERENCES disclosure_documents (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_sections_parent_same_document
        FOREIGN KEY (parent_section_id, disclosure_document_id)
        REFERENCES disclosure_sections (id, disclosure_document_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_disclosure_sections_level
        CHECK (section_level >= 1),
    CONSTRAINT ck_disclosure_sections_sequence
        CHECK (sequence_no >= 1),
    CONSTRAINT ck_disclosure_sections_title_not_blank
        CHECK (
            title IS NULL
            OR char_length(btrim(title)) > 0
        ),
    CONSTRAINT ck_disclosure_sections_source_lines
        CHECK (
            source_line_start >= -1
            AND source_line_end >= -1
            AND (
                source_line_start = -1
                OR source_line_end = -1
                OR source_line_end >= source_line_start
            )
        )
);

CREATE INDEX ix_disclosure_sections_parent
    ON disclosure_sections (parent_section_id)
    WHERE parent_section_id IS NOT NULL;

CREATE TABLE disclosure_content_blocks (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disclosure_document_id UUID NOT NULL,
    section_id             UUID,
    block_type             VARCHAR(20) NOT NULL,
    sequence_no            INTEGER NOT NULL,
    text_content           TEXT,
    structured_content     JSONB,
    source_line_start      INTEGER NOT NULL,
    source_line_end        INTEGER NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_content_blocks_document_sequence
        UNIQUE (disclosure_document_id, sequence_no),
    CONSTRAINT fk_disclosure_content_blocks_document
        FOREIGN KEY (disclosure_document_id)
        REFERENCES disclosure_documents (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_content_blocks_section_same_document
        FOREIGN KEY (section_id, disclosure_document_id)
        REFERENCES disclosure_sections (id, disclosure_document_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_disclosure_content_blocks_type
        CHECK (
            block_type IN (
                'HEADING',
                'PARAGRAPH',
                'TABLE',
                'IMAGE',
                'PAGE_BREAK'
            )
        ),
    CONSTRAINT ck_disclosure_content_blocks_sequence
        CHECK (sequence_no >= 1),
    CONSTRAINT ck_disclosure_content_blocks_text_not_blank
        CHECK (
            text_content IS NULL
            OR char_length(btrim(text_content)) > 0
        ),
    CONSTRAINT ck_disclosure_content_blocks_json_object
        CHECK (
            structured_content IS NULL
            OR jsonb_typeof(structured_content) = 'object'
        ),
    CONSTRAINT ck_disclosure_content_blocks_payload
        CHECK (
            (
                block_type IN ('HEADING', 'PARAGRAPH')
                AND text_content IS NOT NULL
                AND structured_content IS NULL
            )
            OR (
                block_type IN ('TABLE', 'IMAGE')
                AND text_content IS NULL
                AND structured_content IS NOT NULL
            )
            OR (
                block_type = 'PAGE_BREAK'
                AND text_content IS NULL
                AND structured_content IS NULL
            )
        ),
    CONSTRAINT ck_disclosure_content_blocks_source_lines
        CHECK (
            source_line_start >= -1
            AND source_line_end >= -1
            AND (
                source_line_start = -1
                OR source_line_end = -1
                OR source_line_end >= source_line_start
            )
        )
);

CREATE INDEX ix_disclosure_content_blocks_section_sequence
    ON disclosure_content_blocks (section_id, sequence_no)
    WHERE section_id IS NOT NULL;

COMMENT ON TABLE disclosure_sections IS
    '파싱된 공시 원문의 SECTION-N 계층과 원문 위치';

COMMENT ON COLUMN disclosure_sections.disclosure_document_id IS
    '섹션이 속한 원문 파일';

COMMENT ON COLUMN disclosure_sections.parent_section_id IS
    '상위 SECTION. 최상위 섹션이면 NULL';

COMMENT ON COLUMN disclosure_sections.section_level IS
    'SECTION-N의 N 값';

COMMENT ON COLUMN disclosure_sections.sequence_no IS
    '원문 파일 안에서 섹션이 시작된 전역 순서';

COMMENT ON COLUMN disclosure_sections.source_line_start IS
    'SECTION 시작 태그의 원문 행. 알 수 없으면 -1';

COMMENT ON COLUMN disclosure_sections.source_line_end IS
    'SECTION 종료 태그의 원문 행. 알 수 없으면 -1';

COMMENT ON TABLE disclosure_content_blocks IS
    '파싱된 제목·문단·표·이미지·페이지 구분 블록';

COMMENT ON COLUMN disclosure_content_blocks.disclosure_document_id IS
    '블록이 속한 원문 파일';

COMMENT ON COLUMN disclosure_content_blocks.section_id IS
    '블록이 속한 섹션. 섹션 이전의 preamble 블록이면 NULL';

COMMENT ON COLUMN disclosure_content_blocks.block_type IS
    'HEADING, PARAGRAPH, TABLE, IMAGE 또는 PAGE_BREAK';

COMMENT ON COLUMN disclosure_content_blocks.sequence_no IS
    '원문 파일 안에서 블록이 등장한 전역 순서';

COMMENT ON COLUMN disclosure_content_blocks.text_content IS
    'HEADING 또는 PARAGRAPH의 정규화된 텍스트';

COMMENT ON COLUMN disclosure_content_blocks.structured_content IS
    'TABLE의 행·셀·중첩 표 또는 IMAGE 메타데이터를 보존하는 JSONB 객체';

COMMENT ON COLUMN disclosure_content_blocks.source_line_start IS
    '블록 시작 위치의 원문 행. 알 수 없으면 -1';

COMMENT ON COLUMN disclosure_content_blocks.source_line_end IS
    '블록 종료 위치의 원문 행. 알 수 없으면 -1';

