package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureBlock;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureBlockType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureImage;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureSection;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import com.foliolens.backend.disclosure.repository.DisclosureContentBlockRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DisclosureParsingPersistenceIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID DISCLOSURE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final UUID DOCUMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final String FILE_NAME = "test.xml";

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
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    DisclosureParsingPersistenceService persistenceService;

    @Autowired
    DisclosureParsingFailureRecorder failureRecorder;

    @Autowired
    DisclosureDocumentRepository documentRepository;

    @Autowired
    DisclosureSectionRepository sectionRepository;

    @Autowired
    DisclosureContentBlockRepository blockRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE companies CASCADE");
        insertDocumentFixture();
    }

    @Test
    void parsedResultIsStoredWithHierarchyAndJsonb() {
        DisclosureParsingPersistenceResult result =
                persistenceService.replaceParsedResult(
                        DOCUMENT_ID,
                        createFullParsedDocument(),
                        "test-parser",
                        "1.0.0"
                );

        assertThat(result.deletedSectionCount()).isZero();
        assertThat(result.deletedBlockCount()).isZero();
        assertThat(result.savedSectionCount()).isEqualTo(2);
        assertThat(result.savedBlockCount()).isEqualTo(5);

        assertThat(sectionRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(2);
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(5);

        DisclosureDocument savedDocument = documentRepository
                .findById(DOCUMENT_ID)
                .orElseThrow();

        assertThat(savedDocument.getParseStatus())
                .isEqualTo(DisclosureDocumentParseStatus.COMPLETED);
        assertThat(savedDocument.getParserName())
                .isEqualTo("test-parser");
        assertThat(savedDocument.getParserVersion())
                .isEqualTo("1.0.0");
        assertThat(savedDocument.getParseErrorMessage()).isNull();
        assertThat(savedDocument.getParsedAt()).isNotNull();
        assertThat(savedDocument.getDocumentName())
                .isEqualTo("테스트 문서");

        Integer preambleCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM disclosure_content_blocks
                WHERE disclosure_document_id = ?
                  AND section_id IS NULL
                """,
                Integer.class,
                DOCUMENT_ID
        );

        Integer childSectionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM disclosure_sections
                WHERE disclosure_document_id = ?
                  AND parent_section_id IS NOT NULL
                """,
                Integer.class,
                DOCUMENT_ID
        );

        assertThat(preambleCount).isEqualTo(1);
        assertThat(childSectionCount).isEqualTo(1);

        List<DisclosureContentBlock> tableBlocks = blockRepository
                .findAllByDisclosureDocumentIdAndBlockTypeOrderBySequenceNoAsc(
                        DOCUMENT_ID,
                        DisclosureContentBlockType.TABLE
                );

        List<DisclosureContentBlock> imageBlocks = blockRepository
                .findAllByDisclosureDocumentIdAndBlockTypeOrderBySequenceNoAsc(
                        DOCUMENT_ID,
                        DisclosureContentBlockType.IMAGE
                );

        assertThat(tableBlocks).hasSize(1);
        assertThat(imageBlocks).hasSize(1);
        assertThat(tableBlocks.getFirst().getStructuredContent().isObject())
                .isTrue();
        assertThat(imageBlocks.getFirst().getStructuredContent().isObject())
                .isTrue();
        assertThat(
                tableBlocks.getFirst()
                        .getStructuredContent()
                        .get("schemaVersion")
                        .asInt()
        ).isEqualTo(1);
        assertThat(
                imageBlocks.getFirst()
                        .getStructuredContent()
                        .get("schemaVersion")
                        .asInt()
        ).isEqualTo(1);
    }

    @Test
    void reparsingReplacesExistingRowsWithoutDuplicates() {
        persistenceService.replaceParsedResult(
                DOCUMENT_ID,
                createFullParsedDocument(),
                "test-parser",
                "1.0.0"
        );

        DisclosureParsingPersistenceResult secondResult =
                persistenceService.replaceParsedResult(
                        DOCUMENT_ID,
                        createSmallParsedDocument(),
                        "test-parser",
                        "1.1.0"
                );

        assertThat(secondResult.deletedSectionCount()).isEqualTo(2);
        assertThat(secondResult.deletedBlockCount()).isEqualTo(5);
        assertThat(secondResult.savedSectionCount()).isEqualTo(1);
        assertThat(secondResult.savedBlockCount()).isEqualTo(2);

        assertThat(sectionRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(1);
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(2);

        DisclosureDocument document = documentRepository
                .findById(DOCUMENT_ID)
                .orElseThrow();

        assertThat(document.getParserVersion()).isEqualTo("1.1.0");
        assertThat(document.getDocumentName())
                .isEqualTo("재파싱 문서");
    }

    @Test
    void failedReplacementRollsBackOldRowsAndFailureIsRecordedSeparately() {
        persistenceService.replaceParsedResult(
                DOCUMENT_ID,
                createFullParsedDocument(),
                "test-parser",
                "1.0.0"
        );

        assertThatThrownBy(() ->
                persistenceService.replaceParsedResult(
                        DOCUMENT_ID,
                        createDuplicateSectionOrderDocument(),
                        "test-parser",
                        "2.0.0"
                )
        ).isInstanceOf(RuntimeException.class);

        assertThat(sectionRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(2);
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(5);

        DisclosureDocument rolledBackDocument = documentRepository
                .findById(DOCUMENT_ID)
                .orElseThrow();

        assertThat(rolledBackDocument.getParseStatus())
                .isEqualTo(DisclosureDocumentParseStatus.COMPLETED);
        assertThat(rolledBackDocument.getParserVersion())
                .isEqualTo("1.0.0");

        RuntimeException parsingFailure = new RuntimeException(
                "상위 예외",
                new IllegalArgumentException("실제 파싱 실패 원인")
        );

        failureRecorder.markFailed(
                DOCUMENT_ID,
                "test-parser",
                "2.0.0",
                parsingFailure
        );

        DisclosureDocument failedDocument = documentRepository
                .findById(DOCUMENT_ID)
                .orElseThrow();

        assertThat(failedDocument.getParseStatus())
                .isEqualTo(DisclosureDocumentParseStatus.FAILED);
        assertThat(failedDocument.getParserName())
                .isEqualTo("test-parser");
        assertThat(failedDocument.getParserVersion())
                .isEqualTo("2.0.0");
        assertThat(failedDocument.getParseErrorMessage())
                .isEqualTo(
                        "IllegalArgumentException: 실제 파싱 실패 원인"
                );
        assertThat(failedDocument.getParsedAt()).isNotNull();

        // 실패 상태 기록은 기존의 마지막 정상 파싱 행을 물리적으로 지우지 않는다.
        assertThat(sectionRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(2);
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(5);
    }

    private ParsedDisclosureDocument createFullParsedDocument() {
        ParsedDisclosureTableCell headerCell =
                new ParsedDisclosureTableCell(
                        0,
                        ParsedDisclosureTableCellType.HEADER,
                        1,
                        1,
                        "항목",
                        31,
                        31,
                        List.of(),
                        List.of()
                );

        ParsedDisclosureTable table = new ParsedDisclosureTable(
                1,
                30,
                32,
                List.of(
                        new ParsedDisclosureTableRow(
                                0,
                                31,
                                31,
                                List.of(headerCell)
                        )
                )
        );

        ParsedDisclosureImage image = new ParsedDisclosureImage(
                "1.jpg",
                "테스트 이미지",
                100,
                200,
                "CENTER",
                50,
                52
        );

        ParsedDisclosureSection childSection =
                new ParsedDisclosureSection(
                        2,
                        5,
                        "하위 절",
                        40,
                        60,
                        List.of(
                                ParsedDisclosureBlock.image(6, image),
                                ParsedDisclosureBlock.pageBreak(7, 55)
                        ),
                        List.of()
                );

        ParsedDisclosureSection rootSection =
                new ParsedDisclosureSection(
                        1,
                        2,
                        "상위 장",
                        20,
                        70,
                        List.of(
                                ParsedDisclosureBlock.text(
                                        ParsedDisclosureBlockType.PARAGRAPH,
                                        3,
                                        "본문 문단",
                                        21,
                                        22
                                ),
                                ParsedDisclosureBlock.table(4, table)
                        ),
                        List.of(childSection)
                );

        return new ParsedDisclosureDocument(
                FILE_NAME,
                "테스트 문서",
                List.of(
                        ParsedDisclosureBlock.text(
                                ParsedDisclosureBlockType.PARAGRAPH,
                                1,
                                "문서 앞부분",
                                10,
                                10
                        )
                ),
                List.of(rootSection)
        );
    }

    private ParsedDisclosureDocument createSmallParsedDocument() {
        ParsedDisclosureSection section = new ParsedDisclosureSection(
                1,
                2,
                "새 장",
                20,
                30,
                List.of(
                        ParsedDisclosureBlock.text(
                                ParsedDisclosureBlockType.HEADING,
                                3,
                                "새 제목",
                                21,
                                21
                        )
                ),
                List.of()
        );

        return new ParsedDisclosureDocument(
                FILE_NAME,
                "재파싱 문서",
                List.of(
                        ParsedDisclosureBlock.text(
                                ParsedDisclosureBlockType.PARAGRAPH,
                                1,
                                "새 앞부분",
                                10,
                                10
                        )
                ),
                List.of(section)
        );
    }

    private ParsedDisclosureDocument
    createDuplicateSectionOrderDocument() {
        ParsedDisclosureSection first = new ParsedDisclosureSection(
                1,
                1,
                "첫 번째 장",
                10,
                20,
                List.of(),
                List.of()
        );

        ParsedDisclosureSection second = new ParsedDisclosureSection(
                1,
                1,
                "두 번째 장",
                30,
                40,
                List.of(),
                List.of()
        );

        return new ParsedDisclosureDocument(
                FILE_NAME,
                "잘못된 문서",
                List.of(),
                List.of(first, second)
        );
    }

    private void insertDocumentFixture() {
        jdbcTemplate.update(
                """
                INSERT INTO companies (
                    id, corp_code, stock_code, corp_name, listed_name,
                    corp_eng_name, market, industry, sector_no, sector,
                    listing_date, fiscal_month, market_cap,
                    market_cap_as_of, listed, source_provider,
                    source_dataset_version
                ) VALUES (
                    ?, '00000001', '000001', '테스트기업', '테스트기업',
                    'Test Company', 'KOSPI', '테스트업', 1, '테스트섹터',
                    DATE '2020-01-01', 12, 1000,
                    DATE '2026-07-24', TRUE, 'CONTEST', 'test-v1'
                )
                """,
                COMPANY_ID
        );

        jdbcTemplate.update(
                """
                INSERT INTO disclosures (
                    id, source_doc_id, company_id, receipt_no,
                    category, source_group, report_name, correction,
                    receipt_date, submitter, base_year, base_month,
                    manifest_path, file_format, expected_file_count,
                    source_provider, source_dataset_version
                ) VALUES (
                    ?, 'periodic_20240101000001', ?, '20240101000001',
                    'PERIODIC', 'periodic', '테스트 사업보고서', FALSE,
                    DATE '2024-01-01', '테스트기업', 2023, 12,
                    'periodic/test', 'xml', 1,
                    'CONTEST', 'test-v1'
                )
                """,
                DISCLOSURE_ID,
                COMPANY_ID
        );

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_documents (
                    id, disclosure_id, relative_path,
                    normalized_relative_path, file_name, file_extension,
                    document_role, document_name, content_format,
                    file_size_bytes, sha256, parse_status
                ) VALUES (
                    ?, ?, 'periodic/test/test.xml',
                    'periodic/test/test.xml', 'test.xml', 'xml',
                    'MAIN', NULL, 'DART_XML',
                    100, ?, 'PENDING'
                )
                """,
                DOCUMENT_ID,
                DISCLOSURE_ID,
                "a".repeat(64)
        );
    }
}
