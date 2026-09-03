package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentChunkStatus;
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
import com.foliolens.backend.disclosure.repository.DisclosureChunkRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentChunkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.infrastructure.chunking.batch.DisclosureChunkingBatchService;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.io.TempDir;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.HtmlParserTestSupport;
import com.foliolens.backend.disclosure.service.DisclosureDocumentParsingService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    DisclosureDocumentChunkingService chunkingService;

    @Autowired
    DisclosureDocumentRepository documentRepository;

    @Autowired
    DisclosureSectionRepository sectionRepository;

    @Autowired
    DisclosureContentBlockRepository blockRepository;

    @Autowired
    DisclosureChunkRepository chunkRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DisclosureDocumentParsingService parsingService;

    @Autowired
    DisclosureChunkingBatchService chunkingBatchService;

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
        ).isEqualTo(2);
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

    @Test
    void storedParsingResultCanBeGeneratedAndStoredAsSearchChunks() {
        persistenceService.replaceParsedResult(
                DOCUMENT_ID,
                createFullParsedDocument(),
                "test-parser",
                "1.0.0"
        );

        DisclosureChunkPersistenceResult result =
                chunkingService.generateAndStore(DOCUMENT_ID);

        assertThat(result.savedChunkCount()).isPositive();
        assertThat(result.savedSourceCount()).isPositive();
        assertThat(chunkRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(result.savedChunkCount());

        DisclosureDocument document = documentRepository
                .findById(DOCUMENT_ID)
                .orElseThrow();

        assertThat(document.getChunkStatus())
                .isEqualTo(DisclosureDocumentChunkStatus.COMPLETED);
        assertThat(document.getChunkGeneratorName()).isNotBlank();
        assertThat(document.getChunkGeneratorVersion()).isNotBlank();
        assertThat(document.getChunkErrorMessage()).isNull();
        assertThat(document.getChunkedAt()).isNotNull();
    }

    @Test
    void htmlRoutesValidatesAndPersistsLinksAndReplacesOldChunks(@TempDir Path directory) throws Exception {
        useHtmlFixture();
        assertThat(documentRepository.countHtmlParsingTargets("신규시설투자등")).isEqualTo(1);
        assertThat(documentRepository.findHtmlParsingTargets("신규시설투자등", null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent())
                .extracting(DisclosureDocument::getId).containsExactly(DOCUMENT_ID);
        Path source = directory.resolve(FILE_NAME);
        Files.copy(HtmlParserTestSupport.fixture("facility-original.xml"), source);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        chunkingService.generateAndStore(DOCUMENT_ID);
        assertThat(documentRepository.findHtmlParsingTargets("신규시설투자등",
                DisclosureDocumentParseStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent()).isEmpty();
        long oldChunks = chunkRepository.countByDisclosureDocumentId(DOCUMENT_ID);
        assertThat(oldChunks).isPositive();

        // 새 결과 저장 실패 시 먼저 삭제했던 청크와 기존 블록도 롤백되어야 한다.
        assertThatThrownBy(() -> persistenceService.replaceParsedResult(
                DOCUMENT_ID, createDuplicateSectionOrderDocument(), "bad-parser", "1"))
                .isInstanceOf(RuntimeException.class);
        assertThat(chunkRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(oldChunks);

        Files.copy(HtmlParserTestSupport.fixture("facility-correction.xml"), source, StandardCopyOption.REPLACE_EXISTING);
        jdbcTemplate.update("UPDATE disclosures SET correction=TRUE WHERE id=?", DISCLOSURE_ID);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        var document = documentRepository.findById(DOCUMENT_ID).orElseThrow();
        assertThat(document.getParseStatus()).isEqualTo(DisclosureDocumentParseStatus.COMPLETED);
        assertThat(document.getParserName()).isEqualTo("DartHtmlDisclosureParser");
        assertThat(document.getParserVersion()).isEqualTo("1.1.0");
        assertThat(document.getChunkStatus()).isEqualTo(DisclosureDocumentChunkStatus.PENDING);
        assertThat(sectionRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(2);
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(4);
        assertThat(chunkRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isZero();
        var links = document.getRelatedDisclosureLinks().path("links");
        assertThat(links.size()).isEqualTo(1);
        assertThat(links.get(0).path("krxAcptNo").asText()).isEqualTo("20240430000348");
        assertThat(links.get(0).path("dartReceiptNo").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM disclosure_content_blocks WHERE disclosure_document_id=? AND structured_content->>'schemaVersion'='2'",
                Integer.class, DOCUMENT_ID)).isEqualTo(4);
    }

    @Test
    void plainSpanContractCorrectionPersistsSeparateSectionsAndCurrentParserVersion(@TempDir Path directory) throws Exception {
        useHtmlFixture();
        jdbcTemplate.update("UPDATE disclosures SET correction=TRUE, raw_subtype='단일판매공급계약체결' WHERE id=?", DISCLOSURE_ID);
        Path source = directory.resolve(FILE_NAME);
        Files.copy(HtmlParserTestSupport.fixture("contract-correction.xml"), source);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        var document = documentRepository.findById(DOCUMENT_ID).orElseThrow();
        assertThat(document.getParseStatus()).isEqualTo(DisclosureDocumentParseStatus.COMPLETED);
        assertThat(document.getParserVersion()).isEqualTo("1.1.0");
        assertThat(document.getDocumentName()).isEqualTo("단일판매ㆍ공급계약 체결");
        assertThat(jdbcTemplate.queryForList(
                "SELECT title FROM disclosure_sections WHERE disclosure_document_id=? ORDER BY sequence_no",
                String.class, DOCUMENT_ID)).containsExactly("정정신고(보고)", "단일판매ㆍ공급계약 체결");
        assertThat(jdbcTemplate.queryForList("""
                SELECT count(b.id) FROM disclosure_sections s
                LEFT JOIN disclosure_content_blocks b ON b.section_id=s.id
                WHERE s.disclosure_document_id=? GROUP BY s.id,s.sequence_no ORDER BY s.sequence_no
                """, Long.class, DOCUMENT_ID)).containsExactly(3L, 1L);
    }

    @Test
    void htmlFailureIsRecordedWithoutDeletingLastSuccessfulResult(@TempDir Path directory) throws Exception {
        useHtmlFixture();
        Path source = directory.resolve(FILE_NAME);
        Files.copy(HtmlParserTestSupport.fixture("facility-original.xml"), source);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        Files.writeString(source, "<html><body>not a disclosure</body></html>");
        assertThatThrownBy(() -> parsingService.parseAndStore(DOCUMENT_ID, source))
                .isInstanceOf(IllegalArgumentException.class);
        var document = documentRepository.findById(DOCUMENT_ID).orElseThrow();
        assertThat(document.getParseStatus()).isEqualTo(DisclosureDocumentParseStatus.FAILED);
        assertThat(document.getParserName()).isEqualTo("DartHtmlDisclosureParser");
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "facility-original.xml,신규시설투자등,false",
            "facility-correction.xml,신규시설투자등,true",
            "contract-original.xml,단일판매공급계약체결,false",
            "contract-correction.xml,단일판매공급계약체결,true",
            "contract-cancellation.xml,단일판매공급계약해지,false",
            "major-management.xml,투자판단관련주요경영사항,false"
    })
    void htmlPilotStoresChunksAndSourcesAndSkipsCompletedDocuments(
            String fixture, String subtype, boolean correction, @TempDir Path directory) throws Exception {
        useHtmlFixture();
        jdbcTemplate.update("UPDATE disclosures SET raw_subtype=?, correction=? WHERE id=?",
                subtype, correction, DISCLOSURE_ID);
        Path source = directory.resolve(FILE_NAME);
        Files.copy(HtmlParserTestSupport.fixture(fixture), source);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        var result = chunkingBatchService.processNextBatch(5, DisclosureDocumentContentFormat.HTML, subtype);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.savedChunkCount()).isPositive();
        assertThat(result.savedSourceCount()).isPositive();
        var document = documentRepository.findById(DOCUMENT_ID).orElseThrow();
        assertThat(document.getParseStatus()).isEqualTo(DisclosureDocumentParseStatus.COMPLETED);
        assertThat(document.getChunkStatus()).isEqualTo(DisclosureDocumentChunkStatus.COMPLETED);
        assertThat(document.getChunkGeneratorName()).isEqualTo("DisclosureChunkGenerator");
        assertThat(document.getChunkGeneratorVersion()).isEqualTo("disclosure-chunk-v2");
        assertThat(document.getChunkedAt()).isNotNull();
        assertThat(chunkRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(result.savedChunkCount());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM disclosure_chunk_sources s
                JOIN disclosure_chunks c ON c.id=s.disclosure_chunk_id
                WHERE c.disclosure_document_id=?
                """, Long.class, DOCUMENT_ID)).isEqualTo(result.savedSourceCount());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM disclosure_chunk_sources s
                JOIN disclosure_chunks c ON c.id=s.disclosure_chunk_id
                JOIN disclosure_content_blocks b ON b.id=s.content_block_id
                WHERE c.disclosure_document_id=? AND (
                    b.disclosure_document_id<>c.disclosure_document_id
                    OR b.section_id IS DISTINCT FROM c.section_id
                    OR s.source_line_start<b.source_line_start
                    OR s.source_line_end>b.source_line_end)
                """, Integer.class, DOCUMENT_ID)).isZero();
        assertThat(chunkingBatchService.processNextBatch(5, DisclosureDocumentContentFormat.HTML, subtype)
                .totalCount()).isZero();
        // 재실행이 완료 문서를 건드리지 않아 건수가 그대로 유지된다.
        assertThat(chunkRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(result.savedChunkCount());
    }

    @Test
    void htmlTargetQueryFiltersSubtypeFormatStatusAndViewer() {
        useHtmlFixture();
        jdbcTemplate.update("""
                UPDATE disclosure_documents SET parse_status='COMPLETED', parser_name='test',
                    parser_version='1', parsed_at=CURRENT_TIMESTAMP WHERE id=?
                """, DOCUMENT_ID);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.HTML,
                "신규시설투자등", PageRequest.of(0, 5)).getContent()).hasSize(1);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.HTML,
                null, PageRequest.of(0, 5)).getContent()).hasSize(1);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.HTML,
                "단일판매공급계약해지", PageRequest.of(0, 5)).getContent()).isEmpty();
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.DART_XML,
                null, PageRequest.of(0, 5)).getContent()).isEmpty();

        jdbcTemplate.update("""
                UPDATE disclosure_documents SET parse_status='PENDING', parser_name=NULL,
                    parser_version=NULL, parsed_at=NULL WHERE id=?
                """, DOCUMENT_ID);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.HTML,
                null, PageRequest.of(0, 5)).getContent()).isEmpty();
        jdbcTemplate.update("""
                UPDATE disclosure_documents SET parse_status='FAILED', parser_name='test',
                    parser_version='1', parsed_at=CURRENT_TIMESTAMP, parse_error_message='test failure' WHERE id=?
                """, DOCUMENT_ID);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.HTML,
                null, PageRequest.of(0, 5)).getContent()).isEmpty();
        jdbcTemplate.update("""
                UPDATE disclosure_documents SET parse_status='COMPLETED', parse_error_message=NULL, chunk_status='FAILED',
                    chunk_generator_name='test', chunk_generator_version='test-v1',
                    chunk_error_message='test failure', chunked_at=CURRENT_TIMESTAMP WHERE id=?
                """, DOCUMENT_ID);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.HTML,
                null, PageRequest.of(0, 5)).getContent()).isEmpty();
        jdbcTemplate.update("""
                UPDATE disclosure_documents SET chunk_status='PENDING', chunk_generator_name=NULL,
                    chunk_generator_version=NULL, chunk_error_message=NULL, chunked_at=NULL WHERE id=?
                """, DOCUMENT_ID);
        jdbcTemplate.update("""
                UPDATE disclosures SET source_group='periodic', category='PERIODIC',
                    source_doc_id='periodic_' || receipt_no, base_year=2024, base_month=12,
                    raw_subtype='annual' WHERE id=?
                """, DISCLOSURE_ID);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.HTML,
                null, PageRequest.of(0, 5)).getContent()).isEmpty();
        jdbcTemplate.update("UPDATE disclosure_documents SET content_format='DART_XML' WHERE id=?", DOCUMENT_ID);
        assertThat(documentRepository.findChunkingTargets(DisclosureDocumentContentFormat.DART_XML,
                null, PageRequest.of(0, 5)).getContent()).hasSize(1);
    }

    @Test
    void filteringSymbolTableKeepsRawBlocksAndStoresOnlyUsefulChunkSources(@TempDir Path directory) throws Exception {
        useHtmlFixture();
        Path source = directory.resolve(FILE_NAME);
        Files.writeString(source, """
                <html><body><div class="xforms"><h1>신규 시설투자 등</h1>
                <table><tr><td>-</td></tr></table>
                <table><tr><td>투자금액(원)</td><td>0</td></tr><tr><td>비고</td><td>-</td></tr></table>
                </div></body></html>
                """);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        var result = chunkingService.generateAndStore(DOCUMENT_ID);
        assertThat(result.savedChunkCount()).isEqualTo(1);
        assertThat(result.savedSourceCount()).isEqualTo(1);
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT body_text FROM disclosure_chunks WHERE disclosure_document_id=?",
                String.class, DOCUMENT_ID)).contains("투자금액(원) | 0", "비고 | -");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM disclosure_chunk_sources s
                JOIN disclosure_content_blocks b ON b.id=s.content_block_id
                WHERE b.disclosure_document_id=? AND b.structured_content->'table'->'rows'->0->'cells'->0->>'text'='-'
                """, Integer.class, DOCUMENT_ID)).isZero();
        var repeated = chunkingService.generateAndStore(DOCUMENT_ID);
        assertThat(repeated.deletedChunkCount()).isEqualTo(1);
        assertThat(repeated.savedChunkCount()).isEqualTo(1);
        assertThat(repeated.savedSourceCount()).isEqualTo(1);
    }

    @Test
    void allSymbolDocumentCompletesWithZeroChunksAndPreservesParsedTable(@TempDir Path directory) throws Exception {
        useHtmlFixture();
        Path source = directory.resolve(FILE_NAME);
        Files.writeString(source, """
                <html><body><div class="xforms"><h1>신규 시설투자 등</h1>
                <table><tr><td>-</td></tr></table>
                </div></body></html>
                """);
        parsingService.parseAndStore(DOCUMENT_ID, source);
        var result = chunkingService.generateAndStore(DOCUMENT_ID);
        assertThat(result.savedChunkCount()).isZero();
        assertThat(result.savedSourceCount()).isZero();
        assertThat(blockRepository.countByDisclosureDocumentId(DOCUMENT_ID)).isEqualTo(1);
        var document = documentRepository.findById(DOCUMENT_ID).orElseThrow();
        assertThat(document.getChunkStatus()).isEqualTo(DisclosureDocumentChunkStatus.COMPLETED);
        assertThat(document.getChunkGeneratorVersion()).isEqualTo("disclosure-chunk-v2");
    }

    private void useHtmlFixture() {
        jdbcTemplate.update("""
                UPDATE disclosures SET source_group='exchange', category='EXCHANGE',
                    source_doc_id='exchange_' || receipt_no, base_year=NULL, base_month=NULL,
                    raw_subtype='신규시설투자등'
                WHERE id=?
                """, DISCLOSURE_ID);
        jdbcTemplate.update("UPDATE disclosure_documents SET content_format='HTML' WHERE id=?", DOCUMENT_ID);
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
