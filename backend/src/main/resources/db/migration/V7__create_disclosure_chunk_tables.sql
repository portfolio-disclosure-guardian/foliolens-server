ALTER TABLE disclosure_content_blocks
    ADD CONSTRAINT uq_disclosure_content_blocks_id_document
        UNIQUE (id, disclosure_document_id);

CREATE TABLE disclosure_chunks (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disclosure_document_id   UUID NOT NULL,
    section_id               UUID,
    chunk_type               VARCHAR(30) NOT NULL,
    chunk_sequence_no        INTEGER NOT NULL,
    section_path             TEXT NOT NULL,
    body_text                TEXT NOT NULL,
    search_text              TEXT NOT NULL,
    body_character_count     INTEGER GENERATED ALWAYS AS (
        char_length(body_text)
    ) STORED,
    search_character_count   INTEGER GENERATED ALWAYS AS (
        char_length(search_text)
    ) STORED,
    generator_name           VARCHAR(100) NOT NULL,
    generator_version        VARCHAR(100) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_chunks_document_sequence
        UNIQUE (disclosure_document_id, chunk_sequence_no),
    CONSTRAINT uq_disclosure_chunks_id_document
        UNIQUE (id, disclosure_document_id),
    CONSTRAINT fk_disclosure_chunks_document
        FOREIGN KEY (disclosure_document_id)
        REFERENCES disclosure_documents (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_chunks_section_same_document
        FOREIGN KEY (section_id, disclosure_document_id)
        REFERENCES disclosure_sections (id, disclosure_document_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_disclosure_chunks_type
        CHECK (chunk_type IN ('TEXT', 'TABLE', 'IMAGE_CAPTION')),
    CONSTRAINT ck_disclosure_chunks_sequence
        CHECK (chunk_sequence_no >= 1),
    CONSTRAINT ck_disclosure_chunks_body_not_blank
        CHECK (char_length(btrim(body_text)) > 0),
    CONSTRAINT ck_disclosure_chunks_search_not_blank
        CHECK (char_length(btrim(search_text)) > 0),
    CONSTRAINT ck_disclosure_chunks_generator_name_not_blank
        CHECK (char_length(btrim(generator_name)) > 0),
    CONSTRAINT ck_disclosure_chunks_generator_version_not_blank
        CHECK (char_length(btrim(generator_version)) > 0)
);

CREATE INDEX ix_disclosure_chunks_section_sequence
    ON disclosure_chunks (
        section_id,
        disclosure_document_id,
        chunk_sequence_no
    )
    WHERE section_id IS NOT NULL;

CREATE INDEX ix_disclosure_chunks_document_type_sequence
    ON disclosure_chunks (
        disclosure_document_id,
        chunk_type,
        chunk_sequence_no
    );

CREATE TABLE disclosure_chunk_sources (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disclosure_chunk_id      UUID NOT NULL,
    disclosure_document_id   UUID NOT NULL,
    content_block_id         UUID NOT NULL,
    source_order             INTEGER NOT NULL,
    block_sequence_no        INTEGER NOT NULL,
    source_line_start        INTEGER NOT NULL,
    source_line_end          INTEGER NOT NULL,
    table_nesting_path       TEXT,
    table_row_index_start    INTEGER,
    table_row_index_end      INTEGER,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_chunk_sources_chunk_order
        UNIQUE (disclosure_chunk_id, source_order),
    CONSTRAINT fk_disclosure_chunk_sources_chunk_same_document
        FOREIGN KEY (disclosure_chunk_id, disclosure_document_id)
        REFERENCES disclosure_chunks (id, disclosure_document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_chunk_sources_block_same_document
        FOREIGN KEY (content_block_id, disclosure_document_id)
        REFERENCES disclosure_content_blocks (id, disclosure_document_id)
        ON DELETE NO ACTION,
    CONSTRAINT ck_disclosure_chunk_sources_order
        CHECK (source_order >= 1),
    CONSTRAINT ck_disclosure_chunk_sources_block_sequence
        CHECK (block_sequence_no >= 1),
    CONSTRAINT ck_disclosure_chunk_sources_source_lines
        CHECK (
            source_line_start >= -1
            AND source_line_end >= -1
            AND (
                source_line_start = -1
                OR source_line_end = -1
                OR source_line_end >= source_line_start
            )
        ),
    CONSTRAINT ck_disclosure_chunk_sources_table_path_not_blank
        CHECK (
            table_nesting_path IS NULL
            OR char_length(btrim(table_nesting_path)) > 0
        ),
    CONSTRAINT ck_disclosure_chunk_sources_table_row_pair
        CHECK (
            (table_row_index_start IS NULL)
            = (table_row_index_end IS NULL)
        ),
    CONSTRAINT ck_disclosure_chunk_sources_table_row_range
        CHECK (
            table_row_index_start IS NULL
            OR (
                table_row_index_start >= 0
                AND table_row_index_end >= table_row_index_start
            )
        )
);

CREATE INDEX ix_disclosure_chunk_sources_content_block
    ON disclosure_chunk_sources (
        content_block_id,
        disclosure_document_id
    );

COMMENT ON TABLE disclosure_chunks IS
    '파싱된 공시 원문에서 생성한 검색용 TEXT, TABLE, IMAGE_CAPTION 청크';

COMMENT ON COLUMN disclosure_chunks.disclosure_document_id IS
    '청크가 속한 공시 원문 파일';

COMMENT ON COLUMN disclosure_chunks.section_id IS
    '청크가 속한 Section. 문서 서두 청크이면 NULL';

COMMENT ON COLUMN disclosure_chunks.chunk_type IS
    '검색 청크 유형. TEXT, TABLE 또는 IMAGE_CAPTION';

COMMENT ON COLUMN disclosure_chunks.chunk_sequence_no IS
    '문서 안에서 원문 순서대로 1부터 부여한 청크 순서';

COMMENT ON COLUMN disclosure_chunks.section_path IS
    '부모 Section 제목을 포함한 전체 경로. 문서 서두는 문서 서두로 저장';

COMMENT ON COLUMN disclosure_chunks.body_text IS
    '원문 내용을 검색 가능한 형태로 정규화·직렬화한 청크 본문';

COMMENT ON COLUMN disclosure_chunks.search_text IS
    'Section 경로와 HEADING 등 검색 문맥을 포함한 문자열';

COMMENT ON COLUMN disclosure_chunks.body_character_count IS
    'body_text에서 자동 계산되는 문자 수';

COMMENT ON COLUMN disclosure_chunks.search_character_count IS
    'search_text에서 자동 계산되는 문자 수';

COMMENT ON COLUMN disclosure_chunks.generator_name IS
    '청크를 생성한 Generator 식별자';

COMMENT ON COLUMN disclosure_chunks.generator_version IS
    '청크 생성 규칙을 재현하기 위한 버전';

COMMENT ON TABLE disclosure_chunk_sources IS
    '검색 청크와 원본 disclosure_content_blocks 및 표 행 범위를 연결하는 근거';

COMMENT ON COLUMN disclosure_chunk_sources.disclosure_chunk_id IS
    '근거가 연결된 검색 청크';

COMMENT ON COLUMN disclosure_chunk_sources.disclosure_document_id IS
    '청크와 원본 Block이 같은 문서인지 보장하기 위한 문서 ID';

COMMENT ON COLUMN disclosure_chunk_sources.content_block_id IS
    '청크 생성에 사용한 원본 ContentBlock';

COMMENT ON COLUMN disclosure_chunk_sources.source_order IS
    '하나의 청크 안에서 원본 Block 출처가 등장하는 1부터 시작하는 순서';

COMMENT ON COLUMN disclosure_chunk_sources.block_sequence_no IS
    '원본 문서에서 ContentBlock이 등장한 전역 순서';

COMMENT ON COLUMN disclosure_chunk_sources.source_line_start IS
    '원본 XML 시작 행. 알 수 없으면 -1';

COMMENT ON COLUMN disclosure_chunk_sources.source_line_end IS
    '원본 XML 종료 행. 알 수 없으면 -1';

COMMENT ON COLUMN disclosure_chunk_sources.table_nesting_path IS
    'TABLE 청크의 중첩 표 JSON 위치. 최상위 표 또는 TEXT 출처이면 NULL 가능';

COMMENT ON COLUMN disclosure_chunk_sources.table_row_index_start IS
    'TABLE 청크가 사용한 0부터 시작하는 표 행 범위의 시작';

COMMENT ON COLUMN disclosure_chunk_sources.table_row_index_end IS
    'TABLE 청크가 사용한 0부터 시작하는 표 행 범위의 끝';
