package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import com.foliolens.backend.disclosure.infrastructure.search.CorrectionFilter;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchResult;
import com.foliolens.backend.disclosure.service.DisclosureMetadataSearchService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DisclosureMetadataSearchRepositoryIntegrationTest {

    private static final UUID COMPANY_ID = new UUID(201, 1);
    private static final UUID OTHER_COMPANY_ID = new UUID(201, 2);
    private static final UUID ORIGINAL_ID = new UUID(202, 1);
    private static final UUID CORRECTION_ID = new UUID(202, 2);
    private static final UUID OTHER_ID = new UUID(202, 3);

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
    DisclosureMetadataSearchService searchService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE companies CASCADE");
        insertCompanies();
        insertDisclosures();
        insertDocuments();
    }

    @Test
    void filtersMetadataAndReturnsActualDocumentCount() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(COMPANY_ID),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                null,
                CorrectionFilter.ORIGINAL_ONLY,
                List.of("시설투자"),
                10
        );

        DisclosureMetadataSearchResult result =
                searchService.search(condition);

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
        assertThat(result.items()).singleElement().satisfies(hit -> {
            assertThat(hit.disclosureId()).isEqualTo(ORIGINAL_ID);
            assertThat(hit.companyId()).isEqualTo(COMPANY_ID);
            assertThat(hit.companyName()).isEqualTo("테스트전자");
            assertThat(hit.stockCode()).isEqualTo("000201");
            assertThat(hit.documentCount()).isEqualTo(2);
            assertThat(hit.matchedTerms()).containsExactly("시설투자");
        });
    }

    @Test
    void usesAsOfAsReceiptDateUpperBound() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(COMPANY_ID),
                null,
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 4, 30),
                CorrectionFilter.ALL,
                List.of("시설투자"),
                10
        );

        DisclosureMetadataSearchResult result =
                searchService.search(condition);

        assertThat(result.items())
                .extracting(item -> item.disclosureId())
                .containsExactly(ORIGINAL_ID);
    }

    @Test
    void ranksExactTitleMatchBeforeNewerPrefixMatchAndReportsTruncation() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(COMPANY_ID),
                null,
                null,
                null,
                CorrectionFilter.ALL,
                List.of("신규시설투자등"),
                1
        );

        DisclosureMetadataSearchResult result =
                searchService.search(condition);

        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
        assertThat(result.items())
                .extracting(item -> item.disclosureId())
                .containsExactly(ORIGINAL_ID);
        assertThat(result.items().getFirst().searchScore()).isEqualTo(3.0);
    }

    @Test
    void usesTitleOnlyForRankingWhenStructuredTypeFilterExists() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(COMPANY_ID),
                null,
                null,
                null,
                CorrectionFilter.ALL,
                List.of("CAPEX"),
                10
        );

        DisclosureMetadataSearchResult result =
                searchService.search(condition);

        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.items())
                .extracting(item -> item.disclosureId())
                .containsExactly(CORRECTION_ID, ORIGINAL_ID);
        assertThat(result.items())
                .allSatisfy(hit -> {
                    assertThat(hit.searchScore()).isZero();
                    assertThat(hit.matchedTerms()).isEmpty();
                });
    }

    @Test
    void usesTitleAsFilterWhenStructuredTypeFiltersAreMissing() {
        DisclosureMetadataSearchCondition condition =
                new DisclosureMetadataSearchCondition(
                        Set.of(),
                        null,
                        null,
                        null,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        List.of("공급계약"),
                        CorrectionFilter.ALL,
                        10
                );

        DisclosureMetadataSearchResult result =
                searchService.search(condition);

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(hit -> {
            assertThat(hit.disclosureId()).isEqualTo(OTHER_ID);
            assertThat(hit.matchedTerms()).containsExactly("공급계약");
            assertThat(hit.searchScore()).isEqualTo(1.0);
        });
    }

    @Test
    void returnsEmptyResultInsteadOfThrowingWhenNoDisclosureMatches() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(new UUID(999, 999)),
                null,
                null,
                null,
                CorrectionFilter.ALL,
                List.of("시설투자"),
                10
        );

        DisclosureMetadataSearchResult result =
                searchService.search(condition);

        assertThat(result.items()).isEmpty();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.truncated()).isFalse();
    }

    private DisclosureMetadataSearchCondition condition(
            Set<UUID> companyIds,
            LocalDate dateFrom,
            LocalDate dateTo,
            LocalDate asOf,
            CorrectionFilter correctionFilter,
            List<String> titleTerms,
            int limit
    ) {
        return new DisclosureMetadataSearchCondition(
                companyIds,
                dateFrom,
                dateTo,
                asOf,
                Set.of(DisclosureSourceGroup.MAJOR),
                Set.of(DisclosureCategory.MATERIAL),
                Set.of("신규시설투자등"),
                titleTerms,
                correctionFilter,
                limit
        );
    }

    private void insertCompanies() {
        insertCompany(
                COMPANY_ID,
                "00000201",
                "000201",
                "테스트전자 주식회사",
                "테스트전자"
        );
        insertCompany(
                OTHER_COMPANY_ID,
                "00000202",
                "000202",
                "다른기업 주식회사",
                "다른기업"
        );
    }

    private void insertCompany(
            UUID id,
            String corpCode,
            String stockCode,
            String corpName,
            String listedName
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO companies (
                    id, corp_code, stock_code, corp_name, listed_name,
                    corp_eng_name, market, industry, sector_no, sector,
                    listing_date, fiscal_month, market_cap,
                    market_cap_as_of, listed, source_provider,
                    source_dataset_version
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    'Metadata Test Company', 'KOSPI', '테스트업', 1, '테스트섹터',
                    DATE '2020-01-01', 12, 1000,
                    DATE '2026-07-24', TRUE, 'CONTEST', 'test-v1'
                )
                """,
                id,
                corpCode,
                stockCode,
                corpName,
                listedName
        );
    }

    private void insertDisclosures() {
        insertDisclosure(
                ORIGINAL_ID,
                COMPANY_ID,
                "20250410000201",
                "신규시설투자등",
                false,
                LocalDate.of(2025, 4, 10)
        );
        insertDisclosure(
                CORRECTION_ID,
                COMPANY_ID,
                "20250510000202",
                "신규시설투자등 정정",
                true,
                LocalDate.of(2025, 5, 10)
        );
        insertDisclosure(
                OTHER_ID,
                OTHER_COMPANY_ID,
                "20250610000203",
                "단일판매ㆍ공급계약체결",
                false,
                LocalDate.of(2025, 6, 10)
        );
    }

    private void insertDisclosure(
            UUID id,
            UUID companyId,
            String receiptNo,
            String reportName,
            boolean correction,
            LocalDate receiptDate
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
                    ?, 'major_' || ?, ?, ?,
                    'MATERIAL', 'major', '신규시설투자등', ?,
                    ?, ?, '테스트 제출인',
                    'major/test', 'xml', 1,
                    'CONTEST', 'test-v1'
                )
                """,
                id,
                receiptNo,
                companyId,
                receiptNo,
                reportName,
                correction,
                receiptDate
        );
    }

    private void insertDocuments() {
        insertDocument(new UUID(203, 1), ORIGINAL_ID, "original-main.xml");
        insertDocument(
                new UUID(203, 2),
                ORIGINAL_ID,
                "original-attachment.xml"
        );
        insertDocument(
                new UUID(203, 3),
                CORRECTION_ID,
                "correction-main.xml"
        );
    }

    private void insertDocument(
            UUID id,
            UUID disclosureId,
            String fileName
    ) {
        String path = "major/test/" + fileName;

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_documents (
                    id, disclosure_id, relative_path,
                    normalized_relative_path, file_name, file_extension,
                    document_role, content_format,
                    file_size_bytes, sha256
                ) VALUES (
                    ?, ?, ?,
                    ?, ?, 'xml',
                    'MAIN', 'DART_XML',
                    100, ?
                )
                """,
                id,
                disclosureId,
                path,
                path,
                fileName,
                "a".repeat(64)
        );
    }
}
