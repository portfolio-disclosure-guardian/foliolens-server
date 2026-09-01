package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchResult;
import com.foliolens.backend.disclosure.service.DisclosureChunkSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DisclosureChunkSearchRepositoryIntegrationTest {

    private static final UUID COMPANY_ID = new UUID(501, 1);
    private static final UUID DISCLOSURE_ID = new UUID(502, 1);
    private static final UUID OTHER_DISCLOSURE_ID = new UUID(502, 2);
    private static final UUID COMPLETED_DOCUMENT_ID = new UUID(503, 1);
    private static final UUID PENDING_DOCUMENT_ID = new UUID(503, 2);
    private static final UUID OTHER_DOCUMENT_ID = new UUID(503, 3);
    private static final UUID SECTION_ID = new UUID(504, 1);
    private static final UUID AMOUNT_BLOCK_ID = new UUID(505, 1);
    private static final UUID DATE_BLOCK_ID = new UUID(505, 2);
    private static final UUID AMOUNT_CHUNK_ID = new UUID(506, 1);
    private static final UUID DATE_CHUNK_ID = new UUID(506, 2);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            )
                    .withDatabaseName("foliolens_test")
                    .withUsername("foliolens")
                    .withPassword("foliolens");

    @DynamicPropertySource
    static void configurePostgreSql(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DisclosureChunkSearchService searchService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE companies CASCADE");
        insertCompany();
        insertDisclosures();
        insertDocuments();
        insertSectionAndBlocks();
        insertChunksAndSources();
    }

    @Test
    void searchesRankedChunkAndReturnsOriginalSourceReference() {
        DisclosureChunkSearchCondition condition = condition(
                Set.of(DISCLOSURE_ID),
                Set.of(),
                Set.of("facility.amount", "facility.purpose"),
                1
        );

        DisclosureChunkSearchResult result = searchService.search(condition);

        assertThat(result.searchedDocumentCount()).isEqualTo(1);
        assertThat(result.candidateChunkCount()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
        assertThat(result.warnings()).contains(
                "파싱이 완료되지 않은 원문 문서가 1개 있습니다.",
                "청킹이 완료되지 않은 원문 문서가 1개 있습니다.",
                "topK 상한으로 인해 일부 청크 후보가 결과에서 제외됐습니다."
        );

        assertThat(result.items()).singleElement().satisfies(hit -> {
            assertThat(hit.chunkId()).isEqualTo(AMOUNT_CHUNK_ID);
            assertThat(hit.disclosureId()).isEqualTo(DISCLOSURE_ID);
            assertThat(hit.disclosureDocumentId())
                    .isEqualTo(COMPLETED_DOCUMENT_ID);
            assertThat(hit.companyName()).isEqualTo("테스트반도체");
            assertThat(hit.chunkType())
                    .isEqualTo(DisclosureChunkType.TABLE);
            assertThat(hit.documentName()).isEqualTo("facility-main.xml");
            assertThat(hit.searchScore()).isPositive();
            assertThat(hit.scoreBreakdown().finalScore())
                    .isEqualTo(hit.searchScore());
            assertThat(hit.matchedTerms()).contains(
                    "투자금액",
                    "투자목적",
                    "신규시설투자"
            );
            assertThat(hit.sources()).singleElement().satisfies(source -> {
                assertThat(source.contentBlockId())
                        .isEqualTo(AMOUNT_BLOCK_ID);
                assertThat(source.sourceLineStart()).isEqualTo(100);
                assertThat(source.sourceLineEnd()).isEqualTo(120);
                assertThat(source.tableRowIndexStart()).isEqualTo(0);
                assertThat(source.tableRowIndexEnd()).isEqualTo(2);
            });
        });
    }

    @Test
    void documentFilterCanSelectPendingDocumentWithoutSearchingItsChunks() {
        DisclosureChunkSearchCondition condition = condition(
                Set.of(DISCLOSURE_ID),
                Set.of(PENDING_DOCUMENT_ID),
                Set.of("facility.amount"),
                10
        );

        DisclosureChunkSearchResult result = searchService.search(condition);

        assertThat(result.items()).isEmpty();
        assertThat(result.searchedDocumentCount()).isZero();
        assertThat(result.candidateChunkCount()).isZero();
        assertThat(result.warnings()).containsExactly(
                "파싱이 완료되지 않은 원문 문서가 1개 있습니다.",
                "청킹이 완료되지 않은 원문 문서가 1개 있습니다."
        );
    }

    @Test
    void rejectsDocumentThatDoesNotBelongToRequestedDisclosure() {
        DisclosureChunkSearchCondition condition = condition(
                Set.of(DISCLOSURE_ID),
                Set.of(OTHER_DOCUMENT_ID),
                Set.of("facility.amount"),
                10
        );

        assertThatThrownBy(() -> searchService.search(condition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("요청한 공시에 속하지 않거나");
    }

    @Test
    void unsupportedFactDoesNotFallBackToBroadChunkSearch() {
        DisclosureChunkSearchCondition condition = condition(
                Set.of(DISCLOSURE_ID),
                Set.of(),
                Set.of("facility.unknown"),
                10
        );

        DisclosureChunkSearchResult result = searchService.search(condition);

        assertThat(result.items()).isEmpty();
        assertThat(result.candidateChunkCount()).isZero();
        assertThat(result.warnings()).contains(
                "지원하지 않는 factKey입니다: facility.unknown",
                "해석 가능한 검색어 또는 Section 힌트가 없어 "
                        + "청크 검색을 실행하지 않았습니다."
        );
    }

    private DisclosureChunkSearchCondition condition(
            Set<UUID> disclosureIds,
            Set<UUID> documentIds,
            Set<String> factKeys,
            int topK
    ) {
        return new DisclosureChunkSearchCondition(
                disclosureIds,
                documentIds,
                Set.of(),
                factKeys,
                List.of(),
                List.of(),
                Set.of(
                        DisclosureChunkType.TEXT,
                        DisclosureChunkType.TABLE
                ),
                topK,
                0
        );
    }

    private void insertCompany() {
        jdbcTemplate.update(
                """
                INSERT INTO companies (
                    id, corp_code, stock_code, corp_name, listed_name,
                    corp_eng_name, market, industry, sector_no, sector,
                    listing_date, fiscal_month, market_cap,
                    market_cap_as_of, listed, source_provider,
                    source_dataset_version
                ) VALUES (
                    ?, '00000501', '000501', '테스트반도체 주식회사',
                    '테스트반도체', 'Test Semiconductor', 'KOSPI',
                    '반도체', 1, '반도체', DATE '2020-01-01', 12, 1000,
                    DATE '2026-07-24', TRUE, 'CONTEST', 'test-v1'
                )
                """,
                COMPANY_ID
        );
    }

    private void insertDisclosures() {
        insertDisclosure(
                DISCLOSURE_ID,
                "20240424800501",
                "신규시설투자등"
        );
        insertDisclosure(
                OTHER_DISCLOSURE_ID,
                "20240501800502",
                "신규시설투자등 정정"
        );
    }

    private void insertDisclosure(
            UUID disclosureId,
            String receiptNo,
            String reportName
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO disclosures (
                    id, source_doc_id, company_id, receipt_no,
                    category, source_group, raw_subtype, report_name,
                    correction, receipt_date, submitter,
                    manifest_path, file_format, expected_file_count,
                    source_provider, source_dataset_version
                ) VALUES (
                    ?, 'exchange_' || ?, ?, ?,
                    'EXCHANGE', 'exchange', '신규시설투자등', ?,
                    FALSE, DATE '2024-04-24', '테스트반도체',
                    'exchange/test', 'xml', 2,
                    'CONTEST', 'test-v1'
                )
                """,
                disclosureId,
                receiptNo,
                COMPANY_ID,
                receiptNo,
                reportName
        );
    }

    private void insertDocuments() {
        insertDocument(
                COMPLETED_DOCUMENT_ID,
                DISCLOSURE_ID,
                "facility-main.xml",
                true
        );
        insertDocument(
                PENDING_DOCUMENT_ID,
                DISCLOSURE_ID,
                "facility-attachment.xml",
                false
        );
        insertDocument(
                OTHER_DOCUMENT_ID,
                OTHER_DISCLOSURE_ID,
                "other-main.xml",
                true
        );
    }

    private void insertDocument(
            UUID documentId,
            UUID disclosureId,
            String fileName,
            boolean completed
    ) {
        String path = "exchange/test/" + fileName;

        if (completed) {
            jdbcTemplate.update(
                    """
                    INSERT INTO disclosure_documents (
                        id, disclosure_id, relative_path,
                        normalized_relative_path, file_name, file_extension,
                        document_role, document_name, content_format,
                        file_size_bytes, sha256, parse_status,
                        parser_name, parser_version, parsed_at,
                        chunk_status, chunk_generator_name,
                        chunk_generator_version, chunked_at
                    ) VALUES (
                        ?, ?, ?, ?, ?, 'xml',
                        'MAIN', NULL, 'DART_XML',
                        100, ?, 'COMPLETED',
                        'test-parser', 'test-parser-v1', CURRENT_TIMESTAMP,
                        'COMPLETED', 'DartXmlDisclosureChunkGenerator',
                        'dart-xml-chunk-v3', CURRENT_TIMESTAMP
                    )
                    """,
                    documentId,
                    disclosureId,
                    path,
                    path,
                    fileName,
                    sha(documentId)
            );
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_documents (
                    id, disclosure_id, relative_path,
                    normalized_relative_path, file_name, file_extension,
                    document_role, document_name, content_format,
                    file_size_bytes, sha256
                ) VALUES (
                    ?, ?, ?, ?, ?, 'xml',
                    'ATTACHMENT', NULL, 'DART_XML',
                    100, ?
                )
                """,
                documentId,
                disclosureId,
                path,
                path,
                fileName,
                sha(documentId)
        );
    }

    private void insertSectionAndBlocks() {
        jdbcTemplate.update(
                """
                INSERT INTO disclosure_sections (
                    id, disclosure_document_id, parent_section_id,
                    section_level, sequence_no, title,
                    source_line_start, source_line_end
                ) VALUES (
                    ?, ?, NULL, 1, 1, '신규시설투자', 90, 160
                )
                """,
                SECTION_ID,
                COMPLETED_DOCUMENT_ID
        );

        insertBlock(
                AMOUNT_BLOCK_ID,
                1,
                "투자금액과 투자목적 표",
                100,
                120,
                "TABLE"
        );
        insertBlock(
                DATE_BLOCK_ID,
                2,
                "투자기간과 이사회결의일",
                130,
                135,
                "PARAGRAPH"
        );
    }

    private void insertBlock(
            UUID blockId,
            int sequenceNo,
            String text,
            int lineStart,
            int lineEnd,
            String blockType
    ) {
        if ("TABLE".equals(blockType)) {
            jdbcTemplate.update(
                    """
                    INSERT INTO disclosure_content_blocks (
                        id, disclosure_document_id, section_id,
                        block_type, sequence_no, text_content,
                        structured_content, source_line_start, source_line_end
                    ) VALUES (
                        ?, ?, ?, ?, ?, NULL,
                        '{"schemaVersion":2,"table":{"rows":[]}}'::jsonb,
                        ?, ?
                    )
                    """,
                    blockId,
                    COMPLETED_DOCUMENT_ID,
                    SECTION_ID,
                    blockType,
                    sequenceNo,
                    lineStart,
                    lineEnd
            );
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_content_blocks (
                    id, disclosure_document_id, section_id,
                    block_type, sequence_no, text_content,
                    structured_content, source_line_start, source_line_end
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, NULL, ?, ?
                )
                """,
                blockId,
                COMPLETED_DOCUMENT_ID,
                SECTION_ID,
                blockType,
                sequenceNo,
                text,
                lineStart,
                lineEnd
        );
    }

    private void insertChunksAndSources() {
        insertChunk(
                AMOUNT_CHUNK_ID,
                1,
                "TABLE",
                "투자금액 | 5,296,200백만원\n투자목적 | 차세대 DRAM 생산능력 확장",
                "신규시설투자 투자내역 투자금액 5,296,200백만원 "
                        + "투자목적 차세대 DRAM 생산능력 확장"
        );
        insertChunk(
                DATE_CHUNK_ID,
                2,
                "TEXT",
                "투자기간은 2024년부터 2027년까지이며 "
                        + "이사회결의일은 2024년 4월 24일입니다.",
                "신규시설투자 투자기간 이사회결의일 2024년 4월 24일"
        );

        insertSource(
                new UUID(507, 1),
                AMOUNT_CHUNK_ID,
                AMOUNT_BLOCK_ID,
                1,
                100,
                120,
                0,
                2
        );
        insertSource(
                new UUID(507, 2),
                DATE_CHUNK_ID,
                DATE_BLOCK_ID,
                2,
                130,
                135,
                null,
                null
        );
    }

    private void insertChunk(
            UUID chunkId,
            int sequenceNo,
            String chunkType,
            String bodyText,
            String searchText
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO disclosure_chunks (
                    id, disclosure_document_id, section_id,
                    chunk_type, chunk_sequence_no, section_path,
                    body_text, search_text,
                    generator_name, generator_version
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    'II. 사업의 내용 > 신규시설투자', ?, ?,
                    'DartXmlDisclosureChunkGenerator', 'dart-xml-chunk-v3'
                )
                """,
                chunkId,
                COMPLETED_DOCUMENT_ID,
                SECTION_ID,
                chunkType,
                sequenceNo,
                bodyText,
                searchText
        );
    }

    private void insertSource(
            UUID sourceId,
            UUID chunkId,
            UUID blockId,
            int blockSequenceNo,
            int lineStart,
            int lineEnd,
            Integer rowStart,
            Integer rowEnd
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO disclosure_chunk_sources (
                    id, disclosure_chunk_id, disclosure_document_id,
                    content_block_id, source_order, block_sequence_no,
                    source_line_start, source_line_end,
                    table_row_index_start, table_row_index_end
                ) VALUES (
                    ?, ?, ?, ?, 1, ?, ?, ?, ?, ?
                )
                """,
                sourceId,
                chunkId,
                COMPLETED_DOCUMENT_ID,
                blockId,
                blockSequenceNo,
                lineStart,
                lineEnd,
                rowStart,
                rowEnd
        );
    }

    private String sha(UUID id) {
        return "%064x".formatted(
                id.getMostSignificantBits() ^ id.getLeastSignificantBits()
        ).replace('-', '0');
    }
}
