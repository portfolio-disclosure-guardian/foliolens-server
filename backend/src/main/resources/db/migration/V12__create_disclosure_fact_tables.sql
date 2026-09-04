ALTER TABLE disclosures
    ADD CONSTRAINT uq_disclosures_id_receipt_no
        UNIQUE (id, receipt_no);

ALTER TABLE disclosure_documents
    ADD CONSTRAINT uq_disclosure_documents_id_disclosure
        UNIQUE (id, disclosure_id);

CREATE TABLE disclosure_evidences (
    id                       UUID PRIMARY KEY,
    disclosure_id            UUID NOT NULL,
    disclosure_document_id   UUID NOT NULL,
    receipt_no               VARCHAR(14) NOT NULL,
    document_name            VARCHAR(500) NOT NULL,
    document_file_role       VARCHAR(30) NOT NULL,
    event_document_role      VARCHAR(30) NOT NULL,
    section_id               UUID,
    section_path             TEXT NOT NULL,
    content_block_id         UUID,
    block_type               VARCHAR(30) NOT NULL,
    table_index_or_name      TEXT,
    source_line_start        INTEGER NOT NULL,
    source_line_end          INTEGER NOT NULL,
    table_nesting_path       TEXT,
    table_row_index          INTEGER,
    table_cell_index         INTEGER,
    source_text              TEXT NOT NULL,
    row_label                TEXT,
    column_label             TEXT,
    raw_value                TEXT,
    raw_unit                 TEXT,
    note_text                TEXT,
    status                   VARCHAR(20) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_evidences_id_document
        UNIQUE (id, disclosure_document_id),
    CONSTRAINT fk_disclosure_evidences_disclosure_receipt
        FOREIGN KEY (disclosure_id, receipt_no)
        REFERENCES disclosures (id, receipt_no)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_evidences_document_same_disclosure
        FOREIGN KEY (disclosure_document_id, disclosure_id)
        REFERENCES disclosure_documents (id, disclosure_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_evidences_section_same_document
        FOREIGN KEY (section_id, disclosure_document_id)
        REFERENCES disclosure_sections (id, disclosure_document_id)
        ON DELETE NO ACTION,
    CONSTRAINT fk_disclosure_evidences_block_same_document
        FOREIGN KEY (content_block_id, disclosure_document_id)
        REFERENCES disclosure_content_blocks (id, disclosure_document_id)
        ON DELETE NO ACTION,
    CONSTRAINT ck_disclosure_evidences_receipt_no
        CHECK (receipt_no ~ '^[0-9]{14}$'),
    CONSTRAINT ck_disclosure_evidences_document_name
        CHECK (char_length(btrim(document_name)) > 0),
    CONSTRAINT ck_disclosure_evidences_document_file_role
        CHECK (
            document_file_role IN (
                'MAIN', 'ATTACHMENT', 'AUDIT_REPORT', 'VIEWER', 'UNKNOWN'
            )
        ),
    CONSTRAINT ck_disclosure_evidences_event_document_role
        CHECK (
            event_document_role IN (
                'ORIGINAL', 'CORRECTION', 'PROGRESS', 'COMPLETION',
                'TERMINATION', 'RESULT', 'UNKNOWN'
            )
        ),
    CONSTRAINT ck_disclosure_evidences_block_type
        CHECK (
            block_type IN (
                'DOCUMENT_METADATA', 'SECTION', 'TITLE', 'HEADING',
                'PARAGRAPH', 'TABLE', 'TABLE_ROW', 'TABLE_CELL', 'NOTE'
            )
        ),
    CONSTRAINT ck_disclosure_evidences_verified_only
        CHECK (status = 'VERIFIED'),
    CONSTRAINT ck_disclosure_evidences_source_text
        CHECK (char_length(btrim(source_text)) > 0),
    CONSTRAINT ck_disclosure_evidences_source_lines
        CHECK (
            (source_line_start = -1 AND source_line_end = -1)
            OR (
                source_line_start >= 0
                AND source_line_end >= source_line_start
            )
        ),
    CONSTRAINT ck_disclosure_evidences_table_indexes
        CHECK (
            table_row_index IS NULL
            OR table_row_index >= 0
        ),
    CONSTRAINT ck_disclosure_evidences_table_cell_index
        CHECK (
            table_cell_index IS NULL
            OR (table_row_index IS NOT NULL AND table_cell_index >= 0)
        ),
    CONSTRAINT ck_disclosure_evidences_content_block
        CHECK (
            block_type = 'DOCUMENT_METADATA'
            OR content_block_id IS NOT NULL
        ),
    CONSTRAINT ck_disclosure_evidences_table_location
        CHECK (
            (block_type <> 'TABLE_ROW' OR table_row_index IS NOT NULL)
            AND (
                block_type <> 'TABLE_CELL'
                OR (
                    table_row_index IS NOT NULL
                    AND table_cell_index IS NOT NULL
                )
            )
        ),
    CONSTRAINT ck_disclosure_evidences_row_label
        CHECK (
            block_type NOT IN ('TABLE_ROW', 'TABLE_CELL')
            OR (row_label IS NOT NULL AND char_length(btrim(row_label)) > 0)
        ),
    CONSTRAINT ck_disclosure_evidences_raw_unit
        CHECK (raw_unit IS NULL OR raw_value IS NOT NULL)
);

CREATE INDEX ix_disclosure_evidences_document
    ON disclosure_evidences (disclosure_document_id, block_type);

CREATE INDEX ix_disclosure_evidences_disclosure
    ON disclosure_evidences (disclosure_id, status);

CREATE INDEX ix_disclosure_evidences_content_block
    ON disclosure_evidences (content_block_id)
    WHERE content_block_id IS NOT NULL;

CREATE TABLE disclosure_facts (
    id                         UUID PRIMARY KEY,
    disclosure_id              UUID NOT NULL,
    disclosure_document_id     UUID NOT NULL,
    fact_key                   VARCHAR(200) NOT NULL,
    value_type                 VARCHAR(20) NOT NULL,
    raw_value                  TEXT,
    raw_unit                   TEXT,
    normalized_decimal_value   NUMERIC,
    normalized_date_value      DATE,
    normalized_text_value      TEXT,
    normalized_unit            VARCHAR(50),
    currency                   VARCHAR(10),
    period_start               DATE,
    period_end                 DATE,
    as_of_date                 DATE,
    accounting_basis           VARCHAR(30) NOT NULL,
    generation_method          VARCHAR(30) NOT NULL,
    availability_status        VARCHAR(30) NOT NULL,
    normalization_status       VARCHAR(30) NOT NULL,
    validation_status          VARCHAR(20) NOT NULL,
    source_receipt_no          VARCHAR(14) NOT NULL,
    policy_version             VARCHAR(100),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_facts_id_document
        UNIQUE (id, disclosure_document_id),
    CONSTRAINT fk_disclosure_facts_disclosure_receipt
        FOREIGN KEY (disclosure_id, source_receipt_no)
        REFERENCES disclosures (id, receipt_no)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_facts_document_same_disclosure
        FOREIGN KEY (disclosure_document_id, disclosure_id)
        REFERENCES disclosure_documents (id, disclosure_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_disclosure_facts_fact_key
        CHECK (
            fact_key ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'
        ),
    CONSTRAINT ck_disclosure_facts_value_type
        CHECK (value_type IN ('TEXT', 'DECIMAL', 'DATE', 'CODE')),
    CONSTRAINT ck_disclosure_facts_accounting_basis
        CHECK (accounting_basis IN ('CONSOLIDATED', 'SEPARATE', 'UNKNOWN')),
    CONSTRAINT ck_disclosure_facts_generation_method
        CHECK (
            generation_method IN (
                'SOURCE_METADATA', 'DIRECT_RAW', 'TEXT_EXTRACTED',
                'DIRECT_NORMALIZED', 'DERIVED_CLASSIFICATION',
                'DERIVED_CALCULATION', 'LINKED_RESOLVED', 'SYSTEM_ASSIGNED'
            )
        ),
    CONSTRAINT ck_disclosure_facts_availability_status
        CHECK (
            availability_status IN (
                'AVAILABLE', 'NOT_STATED', 'WITHHELD', 'NOT_APPLICABLE',
                'AMBIGUOUS', 'PARSE_FAILED'
            )
        ),
    CONSTRAINT ck_disclosure_facts_normalization_status
        CHECK (normalization_status IN ('MAPPED', 'NOT_APPLICABLE')),
    CONSTRAINT ck_disclosure_facts_verified_only
        CHECK (validation_status = 'VERIFIED'),
    CONSTRAINT ck_disclosure_facts_receipt_no
        CHECK (source_receipt_no ~ '^[0-9]{14}$'),
    CONSTRAINT ck_disclosure_facts_period
        CHECK (
            (period_start IS NULL AND period_end IS NULL)
            OR (
                period_start IS NOT NULL
                AND period_end IS NOT NULL
                AND period_end >= period_start
            )
        ),
    CONSTRAINT ck_disclosure_facts_normalized_value
        CHECK (
            (
                availability_status <> 'AVAILABLE'
                AND normalized_decimal_value IS NULL
                AND normalized_date_value IS NULL
                AND normalized_text_value IS NULL
            )
            OR (
                availability_status = 'AVAILABLE'
                AND (
                    (
                        value_type = 'DECIMAL'
                        AND normalized_decimal_value IS NOT NULL
                        AND normalized_date_value IS NULL
                        AND normalized_text_value IS NULL
                    )
                    OR (
                        value_type = 'DATE'
                        AND normalized_decimal_value IS NULL
                        AND normalized_date_value IS NOT NULL
                        AND normalized_text_value IS NULL
                    )
                    OR (
                        value_type IN ('TEXT', 'CODE')
                        AND normalized_decimal_value IS NULL
                        AND normalized_date_value IS NULL
                        AND normalized_text_value IS NOT NULL
                        AND char_length(btrim(normalized_text_value)) > 0
                    )
                )
            )
        ),
    CONSTRAINT ck_disclosure_facts_decimal_unit
        CHECK (
            value_type <> 'DECIMAL'
            OR availability_status <> 'AVAILABLE'
            OR normalized_unit IS NOT NULL
        ),
    CONSTRAINT ck_disclosure_facts_krw_currency
        CHECK (normalized_unit <> 'KRW' OR currency = 'KRW'),
    CONSTRAINT ck_disclosure_facts_policy_version
        CHECK (
            generation_method NOT IN (
                'DIRECT_NORMALIZED', 'DERIVED_CLASSIFICATION',
                'DERIVED_CALCULATION', 'LINKED_RESOLVED'
            )
            OR (policy_version IS NOT NULL AND char_length(btrim(policy_version)) > 0)
        )
);

CREATE INDEX ix_disclosure_facts_document_key
    ON disclosure_facts (disclosure_document_id, fact_key);

CREATE INDEX ix_disclosure_facts_disclosure_key
    ON disclosure_facts (disclosure_id, fact_key, validation_status);

CREATE INDEX ix_disclosure_facts_receipt_key
    ON disclosure_facts (source_receipt_no, fact_key);

CREATE TABLE disclosure_fact_evidences (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disclosure_fact_id       UUID NOT NULL,
    disclosure_evidence_id   UUID NOT NULL,
    disclosure_document_id   UUID NOT NULL,
    evidence_order           INTEGER NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_disclosure_fact_evidences_fact_evidence
        UNIQUE (disclosure_fact_id, disclosure_evidence_id),
    CONSTRAINT uq_disclosure_fact_evidences_fact_order
        UNIQUE (disclosure_fact_id, evidence_order),
    CONSTRAINT fk_disclosure_fact_evidences_fact_same_document
        FOREIGN KEY (disclosure_fact_id, disclosure_document_id)
        REFERENCES disclosure_facts (id, disclosure_document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_fact_evidences_evidence_same_document
        FOREIGN KEY (disclosure_evidence_id, disclosure_document_id)
        REFERENCES disclosure_evidences (id, disclosure_document_id)
        ON DELETE NO ACTION
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_disclosure_fact_evidences_order
        CHECK (evidence_order >= 1)
);

CREATE INDEX ix_disclosure_fact_evidences_evidence
    ON disclosure_fact_evidences (disclosure_evidence_id);

COMMENT ON TABLE disclosure_evidences IS
    '검증된 Fact를 뒷받침하는 원문 문장·표 행·표 셀과 재현 가능한 위치';

COMMENT ON TABLE disclosure_facts IS
    '원문값과 타입별 정규화값, 단위, 기간, 검증 상태를 보존하는 검증된 Fact';

COMMENT ON TABLE disclosure_fact_evidences IS
    'Fact와 Evidence의 다대다 관계 및 Fact 안에서의 근거 순서';

COMMENT ON COLUMN disclosure_facts.normalized_decimal_value IS
    'DECIMAL Fact의 손실 없는 정규화값. 금액은 원 단위, 비율은 퍼센트 수치';

COMMENT ON COLUMN disclosure_facts.normalized_date_value IS
    'DATE Fact의 ISO 날짜 정규화값';

COMMENT ON COLUMN disclosure_facts.normalized_text_value IS
    'TEXT 또는 CODE Fact의 정규화값';

COMMENT ON COLUMN disclosure_fact_evidences.evidence_order IS
    'DisclosureFact.evidenceIds에 기록된 1부터 시작하는 근거 순서';
