ALTER TABLE disclosure_documents
    ADD COLUMN related_disclosure_links JSONB NOT NULL
        DEFAULT '{"schemaVersion":1,"links":[]}'::jsonb;

ALTER TABLE disclosure_documents
    ADD CONSTRAINT ck_disclosure_documents_related_links CHECK (
        jsonb_typeof(related_disclosure_links) = 'object'
        AND related_disclosure_links ? 'schemaVersion'
        AND related_disclosure_links ->> 'schemaVersion' = '1'
        AND related_disclosure_links ? 'links'
        AND jsonb_typeof(related_disclosure_links -> 'links') = 'array'
    );

COMMENT ON COLUMN disclosure_documents.related_disclosure_links IS
    '원문에 기재된 관련공시 링크와 원문 행 위치. KRX 식별자와 DART 접수번호를 구분하며 관계 확정을 뜻하지 않음';
