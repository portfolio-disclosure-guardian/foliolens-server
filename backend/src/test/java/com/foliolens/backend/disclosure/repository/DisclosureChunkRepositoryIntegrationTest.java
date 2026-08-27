package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureChunk;
import com.foliolens.backend.disclosure.domain.DisclosureChunkSource;
import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import jakarta.persistence.EntityManager;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DisclosureChunkRepositoryIntegrationTest {

    private static final UUID COMPANY_ID = new UUID(101, 1);
    private static final UUID DISCLOSURE_ID = new UUID(102, 1);
    private static final UUID DOCUMENT_ID = new UUID(103, 1);
    private static final UUID SECTION_ID = new UUID(104, 1);
    private static final UUID BLOCK_ID = new UUID(105, 1);

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
    DisclosureChunkRepository chunkRepository;

    @Autowired
    DisclosureChunkSourceRepository sourceRepository;

    @Autowired
    DisclosureDocumentRepository documentRepository;

    @Autowired
    DisclosureSectionRepository sectionRepository;

    @Autowired
    DisclosureContentBlockRepository blockRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE companies CASCADE");
        insertFixture();
        entityManager.clear();
    }

    @Test
    void savesQueriesAndDeletesChunkAggregate() {
        DisclosureDocument document = documentRepository
                .findById(DOCUMENT_ID)
                .orElseThrow();
        DisclosureSection section = sectionRepository
                .findById(SECTION_ID)
                .orElseThrow();
        DisclosureContentBlock block = blockRepository
                .findById(BLOCK_ID)
                .orElseThrow();

        String bodyText = "투자금액은 5,000억원입니다.";
        String searchText = "[II. 사업의 내용] 투자금액은 5,000억원입니다.";

        DisclosureChunk chunk = DisclosureChunk.create(
                document,
                section,
                DisclosureChunkType.TEXT,
                1,
                "II. 사업의 내용",
                bodyText,
                searchText,
                "DartXmlDisclosureChunkGenerator",
                "dart-xml-chunk-v1"
        );
        chunk.addSource(
                block,
                1,
                block.getSequenceNo(),
                block.getSourceLineStart(),
                block.getSourceLineEnd(),
                null,
                null,
                null
        );

        UUID chunkId = chunkRepository.saveAndFlush(chunk).getId();
        entityManager.clear();

        List<DisclosureChunk> savedChunks = chunkRepository
                .findAllByDisclosureDocumentIdOrderByChunkSequenceNoAsc(
                        DOCUMENT_ID
                );
        List<DisclosureChunkSource> savedSources = sourceRepository
                .findAllByDisclosureChunkIdOrderBySourceOrderAsc(chunkId);

        assertThat(savedChunks).hasSize(1);
        assertThat(savedChunks.getFirst().getChunkType())
                .isEqualTo(DisclosureChunkType.TEXT);
        assertThat(savedChunks.getFirst().getBodyCharacterCount())
                .isEqualTo(bodyText.length());
        assertThat(savedChunks.getFirst().getSearchCharacterCount())
                .isEqualTo(searchText.length());
        assertThat(savedSources).hasSize(1);
        assertThat(savedSources.getFirst().getContentBlock().getId())
                .isEqualTo(BLOCK_ID);
        assertThat(chunkRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(1);
        assertThat(sourceRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(1);
        assertThat(sourceRepository.existsByContentBlockId(BLOCK_ID))
                .isTrue();

        int deletedCount = chunkRepository
                .deleteAllByDisclosureDocumentId(DOCUMENT_ID);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(chunkRepository.existsByDisclosureDocumentId(DOCUMENT_ID))
                .isFalse();
        assertThat(sourceRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isZero();
    }

    private void insertFixture() {
        jdbcTemplate.update(
                """
                INSERT INTO companies (
                    id, corp_code, stock_code, corp_name, listed_name,
                    corp_eng_name, market, industry, sector_no, sector,
                    listing_date, fiscal_month, market_cap,
                    market_cap_as_of, listed, source_provider,
                    source_dataset_version
                ) VALUES (
                    ?, '00000101', '000101', '청크테스트기업', '청크테스트기업',
                    'Chunk Test Company', 'KOSPI', '테스트업', 1, '테스트섹터',
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
                    ?, 'periodic_20240101000101', ?, '20240101000101',
                    'PERIODIC', 'periodic', '청크 테스트 사업보고서', FALSE,
                    DATE '2024-01-01', '청크테스트기업', 2023, 12,
                    'periodic/chunk-test', 'xml', 1,
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
                    file_size_bytes, sha256, parse_status,
                    parser_name, parser_version, parsed_at
                ) VALUES (
                    ?, ?, 'periodic/chunk-test/test.xml',
                    'periodic/chunk-test/test.xml', 'test.xml', 'xml',
                    'MAIN', '청크 테스트 문서', 'DART_XML',
                    100, ?, 'COMPLETED',
                    'test-parser', 'test-parser-v1', CURRENT_TIMESTAMP
                )
                """,
                DOCUMENT_ID,
                DISCLOSURE_ID,
                "a".repeat(64)
        );

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_sections (
                    id, disclosure_document_id, parent_section_id,
                    section_level, sequence_no, title,
                    source_line_start, source_line_end
                ) VALUES (
                    ?, ?, NULL,
                    1, 1, 'II. 사업의 내용',
                    10, 30
                )
                """,
                SECTION_ID,
                DOCUMENT_ID
        );

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_content_blocks (
                    id, disclosure_document_id, section_id,
                    block_type, sequence_no, text_content,
                    structured_content, source_line_start, source_line_end
                ) VALUES (
                    ?, ?, ?,
                    'PARAGRAPH', 2, '투자금액은 5,000억원입니다.',
                    NULL, 11, 12
                )
                """,
                BLOCK_ID,
                DOCUMENT_ID,
                SECTION_ID
        );
    }
}
