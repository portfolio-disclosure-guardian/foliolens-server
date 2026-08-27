ALTER TABLE disclosure_documents
    ADD COLUMN chunk_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN chunk_generator_name VARCHAR(100),
    ADD COLUMN chunk_generator_version VARCHAR(100),
    ADD COLUMN chunk_error_message TEXT,
    ADD COLUMN chunked_at TIMESTAMPTZ;

ALTER TABLE disclosure_documents
    ADD CONSTRAINT ck_disclosure_documents_chunk_status
        CHECK (chunk_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    ADD CONSTRAINT ck_disclosure_documents_chunk_generator_name_not_blank
        CHECK (
            chunk_generator_name IS NULL
            OR char_length(btrim(chunk_generator_name)) > 0
        ),
    ADD CONSTRAINT ck_disclosure_documents_chunk_generator_version_not_blank
        CHECK (
            chunk_generator_version IS NULL
            OR char_length(btrim(chunk_generator_version)) > 0
        ),
    ADD CONSTRAINT ck_disclosure_documents_chunk_error_not_blank
        CHECK (
            chunk_error_message IS NULL
            OR char_length(btrim(chunk_error_message)) > 0
        ),
    ADD CONSTRAINT ck_disclosure_documents_chunk_result
        CHECK (
            (
                chunk_status = 'PENDING'
                AND chunk_generator_name IS NULL
                AND chunk_generator_version IS NULL
                AND chunk_error_message IS NULL
                AND chunked_at IS NULL
            )
            OR (
                chunk_status = 'COMPLETED'
                AND chunk_generator_name IS NOT NULL
                AND chunk_generator_version IS NOT NULL
                AND chunk_error_message IS NULL
                AND chunked_at IS NOT NULL
            )
            OR (
                chunk_status = 'FAILED'
                AND chunk_generator_name IS NOT NULL
                AND chunk_generator_version IS NOT NULL
                AND chunk_error_message IS NOT NULL
                AND chunked_at IS NOT NULL
            )
        );

CREATE INDEX ix_disclosure_documents_chunk_status
    ON disclosure_documents (chunk_status)
    WHERE chunk_status <> 'COMPLETED';

COMMENT ON COLUMN disclosure_documents.chunk_status IS
    '파일의 검색 청크 생성 상태. PENDING, COMPLETED 또는 FAILED';

COMMENT ON COLUMN disclosure_documents.chunk_generator_name IS
    '가장 최근 청킹에 사용한 생성기 식별자';

COMMENT ON COLUMN disclosure_documents.chunk_generator_version IS
    '가장 최근 청킹에 사용한 생성 규칙 버전';

COMMENT ON COLUMN disclosure_documents.chunk_error_message IS
    '청킹 실패 시 저장하는 오류 원인';

COMMENT ON COLUMN disclosure_documents.chunked_at IS
    '가장 최근 청킹 시도 완료 시각';
