CREATE TABLE disclosures (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_doc_id          VARCHAR(64) NOT NULL,
    company_id             UUID NOT NULL,
    receipt_no             VARCHAR(14) NOT NULL,
    category               VARCHAR(20) NOT NULL,
    source_group           VARCHAR(20) NOT NULL,
    raw_subtype            VARCHAR(200),
    report_name            VARCHAR(500) NOT NULL,
    correction             BOOLEAN NOT NULL DEFAULT FALSE,
    receipt_date           DATE NOT NULL,
    submitter              VARCHAR(200) NOT NULL,
    base_year              SMALLINT,
    base_month             SMALLINT,
    manifest_path          TEXT NOT NULL,
    file_format            VARCHAR(20) NOT NULL,
    expected_file_count    INTEGER NOT NULL,
    source_provider        VARCHAR(30) NOT NULL,
    source_dataset_version VARCHAR(100) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosures_source_doc_id
        UNIQUE (source_doc_id),
    CONSTRAINT uq_disclosures_receipt_no
        UNIQUE (receipt_no),
    CONSTRAINT fk_disclosures_company
        FOREIGN KEY (company_id)
        REFERENCES companies (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_disclosures_source_doc_id
        CHECK (source_doc_id = source_group || '_' || receipt_no),
    CONSTRAINT ck_disclosures_receipt_no
        CHECK (receipt_no ~ '^[0-9]{14}$'),
    CONSTRAINT ck_disclosures_category
        CHECK (category IN ('PERIODIC', 'MATERIAL', 'EXCHANGE', 'OWNERSHIP')),
    CONSTRAINT ck_disclosures_source_group
        CHECK (source_group IN ('periodic', 'major', 'exchange', 'holding')),
    CONSTRAINT ck_disclosures_category_mapping
        CHECK (
            (source_group = 'periodic' AND category = 'PERIODIC')
            OR (source_group = 'major' AND category = 'MATERIAL')
            OR (source_group = 'exchange' AND category = 'EXCHANGE')
            OR (source_group = 'holding' AND category = 'OWNERSHIP')
        ),
    CONSTRAINT ck_disclosures_raw_subtype_not_blank
        CHECK (raw_subtype IS NULL OR char_length(btrim(raw_subtype)) > 0),
    CONSTRAINT ck_disclosures_report_name_not_blank
        CHECK (char_length(btrim(report_name)) > 0),
    CONSTRAINT ck_disclosures_submitter_not_blank
        CHECK (char_length(btrim(submitter)) > 0),
    CONSTRAINT ck_disclosures_base_year
        CHECK (base_year IS NULL OR base_year BETWEEN 1900 AND 2100),
    CONSTRAINT ck_disclosures_base_period
        CHECK (
            (
                source_group = 'periodic'
                AND base_year IS NOT NULL
                AND base_month IN (3, 6, 9, 12)
            )
            OR (
                source_group <> 'periodic'
                AND base_year IS NULL
                AND base_month IS NULL
            )
        ),
    CONSTRAINT ck_disclosures_manifest_path_not_blank
        CHECK (char_length(btrim(manifest_path)) > 0),
    CONSTRAINT ck_disclosures_manifest_path_relative
        CHECK (
            manifest_path !~ '^/'
            AND manifest_path !~ '^[A-Za-z]:'
            AND manifest_path !~ '(^|/)\.\.(/|$)'
        ),
    CONSTRAINT ck_disclosures_file_format
        CHECK (file_format IN ('xml', 'pdf+html')),
    CONSTRAINT ck_disclosures_expected_file_count
        CHECK (expected_file_count > 0),
    CONSTRAINT ck_disclosures_source_provider
        CHECK (source_provider IN ('CONTEST')),
    CONSTRAINT ck_disclosures_source_dataset_version_not_blank
        CHECK (char_length(btrim(source_dataset_version)) > 0)
);

CREATE INDEX ix_disclosures_company_receipt_date
    ON disclosures (company_id, receipt_date DESC);

CREATE INDEX ix_disclosures_category_receipt_date
    ON disclosures (category, receipt_date DESC);

CREATE INDEX ix_disclosures_raw_subtype_receipt_date
    ON disclosures (raw_subtype, receipt_date DESC)
    WHERE raw_subtype IS NOT NULL;

CREATE INDEX ix_disclosures_correction_receipt_date
    ON disclosures (receipt_date DESC)
    WHERE correction;

CREATE INDEX ix_disclosures_dataset_version
    ON disclosures (source_dataset_version);

COMMENT ON TABLE disclosures IS
    '대회 제공 manifest.jsonl을 기준으로 적재한 공시 메타데이터';

COMMENT ON COLUMN disclosures.source_doc_id IS
    '데이터셋 내부 문서 ID. doc_group과 DART 접수번호의 조합';

COMMENT ON COLUMN disclosures.company_id IS
    'manifest의 corp_code로 조회한 companies 행';

COMMENT ON COLUMN disclosures.receipt_no IS
    'DART 공시 접수번호 14자리 문자열';

COMMENT ON COLUMN disclosures.category IS
    '서비스 API에서 사용하는 정규화 공시 대분류';

COMMENT ON COLUMN disclosures.source_group IS
    'manifest에 기록된 원본 doc_group';

COMMENT ON COLUMN disclosures.raw_subtype IS
    'manifest에 기록된 원본 doc_subtype. major 공시는 null일 수 있음';

COMMENT ON COLUMN disclosures.receipt_date IS
    '공시 접수일. manifest의 rcept_dt를 날짜로 변환한 값';

COMMENT ON COLUMN disclosures.base_year IS
    '정기공시 보고 기준연도. 정기공시가 아니면 null';

COMMENT ON COLUMN disclosures.base_month IS
    '정기공시 보고 기준월. 3, 6, 9, 12 중 하나';

COMMENT ON COLUMN disclosures.manifest_path IS
    '데이터셋 루트를 기준으로 한 원문 폴더 상대경로';

COMMENT ON COLUMN disclosures.file_format IS
    'manifest가 지정한 원문 제공 형식: xml 또는 pdf+html';

COMMENT ON COLUMN disclosures.expected_file_count IS
    'manifest에 기록된 원문 폴더 내부 예상 파일 수';

COMMENT ON COLUMN disclosures.source_provider IS
    '공시 메타데이터 출처. 평가 DB에서는 CONTEST만 허용';

COMMENT ON COLUMN disclosures.source_dataset_version IS
    '공시 메타데이터를 마지막으로 반영한 내부 데이터셋 버전';
