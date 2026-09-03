-- PDF 최소 지원. 기존 XML/HTML 행 번호와 분리하며 기존 데이터는 재적재하지 않는다.
ALTER TABLE disclosure_documents
    ADD COLUMN parse_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT ck_disclosure_documents_parse_metadata CHECK (jsonb_typeof(parse_metadata) = 'object');

ALTER TABLE disclosure_content_blocks
    ADD COLUMN source_page_number INTEGER,
    ADD COLUMN text_extraction_suspect BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_disclosure_blocks_pdf_page CHECK (
        (source_page_number IS NULL AND NOT text_extraction_suspect)
        OR (source_page_number IS NOT NULL AND source_page_number >= 1 AND block_type = 'PARAGRAPH'
            AND source_line_start = -1 AND source_line_end = -1)
    );

COMMENT ON COLUMN disclosure_documents.parse_metadata IS
    'PDF_TEXT_ONLY: 페이지 수, 텍스트 없는 페이지, 의심 페이지, OCR/표 복원 미지원 한계. PDF는 PARTIAL로 저장';
COMMENT ON COLUMN disclosure_content_blocks.source_page_number IS
    'PDF 물리 페이지 번호(1부터). 인쇄 쪽번호·XML 행 번호가 아님. chunk_sources.content_block_id로 역추적';
COMMENT ON COLUMN disclosure_content_blocks.text_extraction_suspect IS
    '텍스트 추출 품질 휴리스틱 경고. false도 표 구조/수치 검증 완료를 의미하지 않음';
