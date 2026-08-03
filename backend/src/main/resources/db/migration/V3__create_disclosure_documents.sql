CREATE TABLE disclosure_documents (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disclosure_id            UUID NOT NULL,
    relative_path            TEXT NOT NULL,
    normalized_relative_path TEXT NOT NULL,
    file_name                VARCHAR(500) NOT NULL,
    file_extension           VARCHAR(10) NOT NULL,
    document_role            VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    document_name            VARCHAR(500),
    content_format           VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    file_size_bytes          BIGINT NOT NULL,
    sha256                   CHAR(64) NOT NULL,
    parse_status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    parser_name              VARCHAR(100),
    parser_version           VARCHAR(50),
    parse_error_message      TEXT,
    parsed_at                TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_documents_normalized_path
        UNIQUE (normalized_relative_path),
    CONSTRAINT fk_disclosure_documents_disclosure
        FOREIGN KEY (disclosure_id)
        REFERENCES disclosures (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_disclosure_documents_relative_path_not_blank
        CHECK (char_length(btrim(relative_path)) > 0),
    CONSTRAINT ck_disclosure_documents_relative_path_safe
        CHECK (
            relative_path !~ '^/'
            AND relative_path !~ '^[A-Za-z]:'
            AND relative_path !~ '(^|/)\.\.(/|$)'
            AND position(E'\\' IN relative_path) = 0
        ),
    CONSTRAINT ck_disclosure_documents_normalized_path_not_blank
        CHECK (char_length(btrim(normalized_relative_path)) > 0),
    CONSTRAINT ck_disclosure_documents_normalized_path_safe
        CHECK (
            normalized_relative_path !~ '^/'
            AND normalized_relative_path !~ '^[A-Za-z]:'
            AND normalized_relative_path !~ '(^|/)\.\.(/|$)'
            AND position(E'\\' IN normalized_relative_path) = 0
        ),
    CONSTRAINT ck_disclosure_documents_file_name_not_blank
        CHECK (char_length(btrim(file_name)) > 0),
    CONSTRAINT ck_disclosure_documents_file_extension
        CHECK (file_extension IN ('xml', 'html', 'pdf')),
    CONSTRAINT ck_disclosure_documents_document_role
        CHECK (
            document_role IN (
                'MAIN',
                'ATTACHMENT',
                'AUDIT_REPORT',
                'VIEWER',
                'UNKNOWN'
            )
        ),
    CONSTRAINT ck_disclosure_documents_document_name_not_blank
        CHECK (
            document_name IS NULL
            OR char_length(btrim(document_name)) > 0
        ),
    CONSTRAINT ck_disclosure_documents_content_format
        CHECK (content_format IN ('DART_XML', 'HTML', 'PDF', 'UNKNOWN')),
    CONSTRAINT ck_disclosure_documents_file_size
        CHECK (file_size_bytes >= 0),
    CONSTRAINT ck_disclosure_documents_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_disclosure_documents_parse_status
        CHECK (parse_status IN ('PENDING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_disclosure_documents_parser_name_not_blank
        CHECK (parser_name IS NULL OR char_length(btrim(parser_name)) > 0),
    CONSTRAINT ck_disclosure_documents_parser_version_not_blank
        CHECK (parser_version IS NULL OR char_length(btrim(parser_version)) > 0),
    CONSTRAINT ck_disclosure_documents_parse_error_not_blank
        CHECK (
            parse_error_message IS NULL
            OR char_length(btrim(parse_error_message)) > 0
        ),
    CONSTRAINT ck_disclosure_documents_parse_result
        CHECK (
            (
                parse_status = 'PENDING'
                AND parser_name IS NULL
                AND parser_version IS NULL
                AND parse_error_message IS NULL
                AND parsed_at IS NULL
            )
            OR (
                parse_status = 'COMPLETED'
                AND parser_name IS NOT NULL
                AND parser_version IS NOT NULL
                AND parse_error_message IS NULL
                AND parsed_at IS NOT NULL
            )
            OR (
                parse_status = 'PARTIAL'
                AND parser_name IS NOT NULL
                AND parser_version IS NOT NULL
                AND parse_error_message IS NOT NULL
                AND parsed_at IS NOT NULL
            )
            OR (
                parse_status = 'FAILED'
                AND parser_name IS NOT NULL
                AND parser_version IS NOT NULL
                AND parse_error_message IS NOT NULL
                AND parsed_at IS NOT NULL
            )
        )
);

CREATE INDEX ix_disclosure_documents_disclosure
    ON disclosure_documents (disclosure_id);

CREATE INDEX ix_disclosure_documents_parse_status
    ON disclosure_documents (parse_status)
    WHERE parse_status <> 'COMPLETED';

CREATE INDEX ix_disclosure_documents_sha256
    ON disclosure_documents (sha256);

COMMENT ON TABLE disclosure_documents IS
    '공시별 실제 원문 파일과 무결성·파싱 상태를 관리하는 파일 단위 메타데이터';

COMMENT ON COLUMN disclosure_documents.disclosure_id IS
    '원문 파일이 속한 disclosures 행';

COMMENT ON COLUMN disclosure_documents.relative_path IS
    '데이터셋 루트 기준 실제 파일 상대경로. 물리 파일명의 Unicode 표현을 보존';

COMMENT ON COLUMN disclosure_documents.normalized_relative_path IS
    '경로 조회와 중복 검사용 NFC 정규화 상대경로';

COMMENT ON COLUMN disclosure_documents.file_name IS
    '경로에서 분리한 실제 파일명';

COMMENT ON COLUMN disclosure_documents.file_extension IS
    '파일명 기준 소문자 확장자. xml, html 또는 pdf';

COMMENT ON COLUMN disclosure_documents.document_role IS
    '공시 내 파일 역할. 본문, 첨부, 감사보고서, 뷰어 또는 미분류';

COMMENT ON COLUMN disclosure_documents.document_name IS
    '원문 내부 DOCUMENT-NAME 등에서 추출한 문서명';

COMMENT ON COLUMN disclosure_documents.content_format IS
    '확장자가 아니라 실제 내용을 검사해 판별한 파싱 형식';

COMMENT ON COLUMN disclosure_documents.file_size_bytes IS
    '원문 파일 크기, byte 단위';

COMMENT ON COLUMN disclosure_documents.sha256 IS
    '원문 파일 내용의 소문자 SHA-256 해시';

COMMENT ON COLUMN disclosure_documents.parse_status IS
    '파일 파싱 상태. PENDING, COMPLETED, PARTIAL 또는 FAILED';

COMMENT ON COLUMN disclosure_documents.parser_name IS
    '실제로 사용한 파서 식별자';

COMMENT ON COLUMN disclosure_documents.parser_version IS
    '파싱 결과 재현을 위한 파서 규칙 버전';

COMMENT ON COLUMN disclosure_documents.parse_error_message IS
    '부분 성공 또는 실패 시 저장하는 오류 원인';

COMMENT ON COLUMN disclosure_documents.parsed_at IS
    '가장 최근 파싱 시도 완료 시각';
