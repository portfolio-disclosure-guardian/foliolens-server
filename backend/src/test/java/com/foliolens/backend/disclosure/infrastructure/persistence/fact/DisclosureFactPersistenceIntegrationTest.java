package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.AccountingBasis;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceLocation;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactAvailabilityStatus;
import com.foliolens.backend.disclosure.domain.fact.FactGenerationMethod;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.disclosure.domain.fact.TextFactValue;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.repository.DisclosureEvidenceRepository;
import com.foliolens.backend.disclosure.repository.DisclosureFactEvidenceRepository;
import com.foliolens.backend.disclosure.repository.DisclosureFactRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DisclosureFactPersistenceIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID DISCLOSURE_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000002"
    );
    private static final UUID DOCUMENT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000003"
    );
    private static final UUID SECTION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000004"
    );
    private static final UUID BLOCK_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000005"
    );
    private static final UUID AMOUNT_EVIDENCE_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000006"
    );
    private static final UUID AMOUNT_NOTE_EVIDENCE_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000007"
    );
    private static final UUID PURPOSE_EVIDENCE_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000008"
    );
    private static final UUID AMOUNT_FACT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000009"
    );
    private static final UUID PURPOSE_FACT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000010"
    );
    private static final String RECEIPT_NO = "20240424800596";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    )
            .withDatabaseName("foliolens_test")
            .withUsername("foliolens")
            .withPassword("foliolens");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DisclosureFactPersistenceService persistenceService;

    @Autowired
    DisclosureFactRepository factRepository;

    @Autowired
    DisclosureEvidenceRepository evidenceRepository;

    @Autowired
    DisclosureFactEvidenceRepository linkRepository;

    @Autowired
    DisclosureFactEntityMapper mapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE companies CASCADE");
        insertFixture();
    }

    @Test
    void storesReadsAndIdempotentlyReplacesVerifiedFacts() {
        List<DisclosureEvidence> evidences = evidences(EvidenceStatus.VERIFIED);
        List<DisclosureFact> facts = facts();

        DisclosureFactPersistenceResult first = persistenceService
                .replaceVerifiedFacts(DOCUMENT_ID, evidences, facts);

        assertThat(first).isEqualTo(new DisclosureFactPersistenceResult(
                0,
                0,
                2,
                3,
                3
        ));
        assertStoredCounts();

        List<DisclosureFact> restoredFacts = persistenceService
                .findFactsByDocumentId(DOCUMENT_ID);
        List<DisclosureEvidence> restoredEvidences = persistenceService
                .findEvidencesByDocumentId(DOCUMENT_ID);

        assertThat(restoredFacts).extracting(DisclosureFact::factKey)
                .containsExactly("facility.amount", "facility.purpose");
        DisclosureFact restoredAmount = restoredFacts.getFirst();
        assertThat(restoredAmount.normalizedValue())
                .isEqualTo(new DecimalFactValue(new BigDecimal("5296200000000")));
        assertThat(restoredAmount.evidenceIds()).containsExactly(
                AMOUNT_EVIDENCE_ID,
                AMOUNT_NOTE_EVIDENCE_ID
        );
        assertThat(restoredEvidences).hasSize(3)
                .allMatch(DisclosureEvidence::verified);

        List<DisclosureFactEntity> lookup = factRepository.findAllForLookup(
                Set.of(DISCLOSURE_ID),
                Set.of("facility.amount"),
                FactValidationStatus.VERIFIED
        );
        assertThat(lookup).hasSize(1);
        assertThat(mapper.toDomain(lookup.getFirst()).evidenceIds())
                .containsExactly(AMOUNT_EVIDENCE_ID, AMOUNT_NOTE_EVIDENCE_ID);

        DisclosureFactPersistenceResult repeated = persistenceService
                .replaceVerifiedFacts(DOCUMENT_ID, evidences, facts);

        assertThat(repeated).isEqualTo(new DisclosureFactPersistenceResult(
                2,
                3,
                2,
                3,
                3
        ));
        assertStoredCounts();
        assertThat(persistenceService.findFactsByDocumentId(DOCUMENT_ID))
                .containsExactlyElementsOf(restoredFacts);
    }

    @Test
    void rejectsCandidateEvidenceBeforeReplacingExistingRows() {
        persistenceService.replaceVerifiedFacts(
                DOCUMENT_ID,
                evidences(EvidenceStatus.VERIFIED),
                facts()
        );

        assertThatThrownBy(() -> persistenceService.replaceVerifiedFacts(
                DOCUMENT_ID,
                evidences(EvidenceStatus.CANDIDATE),
                facts()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VERIFIED Evidence");

        assertStoredCounts();
    }

    @Test
    void deletingDisclosureCascadesFactsEvidencesAndLinks() {
        persistenceService.replaceVerifiedFacts(
                DOCUMENT_ID,
                evidences(EvidenceStatus.VERIFIED),
                facts()
        );

        assertThat(jdbcTemplate.update(
                "DELETE FROM disclosures WHERE id = ?",
                DISCLOSURE_ID
        )).isEqualTo(1);

        assertThat(tableCount("disclosure_facts")).isZero();
        assertThat(tableCount("disclosure_evidences")).isZero();
        assertThat(tableCount("disclosure_fact_evidences")).isZero();
    }

    private void assertStoredCounts() {
        assertThat(factRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(2);
        assertThat(evidenceRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(3);
        assertThat(linkRepository.countByDisclosureDocumentId(DOCUMENT_ID))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM disclosure_facts WHERE validation_status = 'VERIFIED'",
                Long.class
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM disclosure_evidences WHERE status = 'VERIFIED'",
                Long.class
        )).isEqualTo(3L);
    }

    private long tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tableName,
                Long.class
        );
    }

    private List<DisclosureEvidence> evidences(EvidenceStatus status) {
        return List.of(
                evidence(
                        AMOUNT_EVIDENCE_ID,
                        EvidenceBlockType.TABLE_CELL,
                        2,
                        1,
                        "투자금액(원) | 5,296,200,000,000",
                        "투자금액(원)",
                        "5,296,200,000,000",
                        "원",
                        null,
                        status
                ),
                evidence(
                        AMOUNT_NOTE_EVIDENCE_ID,
                        EvidenceBlockType.TABLE_ROW,
                        3,
                        null,
                        "투자금액은 향후 경영환경에 따라 변동될 수 있음",
                        "투자금액",
                        "5,296,200,000,000",
                        "원",
                        "계획금액",
                        status
                ),
                evidence(
                        PURPOSE_EVIDENCE_ID,
                        EvidenceBlockType.TABLE_CELL,
                        4,
                        1,
                        "투자목적 | 차세대 DRAM 생산능력 확장",
                        "투자목적",
                        "차세대 DRAM 생산능력 확장",
                        null,
                        null,
                        status
                )
        );
    }

    private DisclosureEvidence evidence(
            UUID evidenceId,
            EvidenceBlockType blockType,
            int rowIndex,
            Integer cellIndex,
            String sourceText,
            String rowLabel,
            String rawValue,
            String rawUnit,
            String noteText,
            EvidenceStatus status
    ) {
        return new DisclosureEvidence(
                evidenceId,
                DISCLOSURE_ID,
                DOCUMENT_ID,
                RECEIPT_NO,
                "신규시설투자등",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                SECTION_ID,
                "신규시설투자등 > 투자내역",
                BLOCK_ID,
                blockType,
                "투자내역",
                new DisclosureEvidenceLocation(
                        90,
                        110,
                        "$.table",
                        rowIndex,
                        cellIndex
                ),
                new DisclosureEvidenceValue(
                        sourceText,
                        rowLabel,
                        "내용",
                        rawValue,
                        rawUnit,
                        noteText
                ),
                status
        );
    }

    private List<DisclosureFact> facts() {
        return List.of(
                new DisclosureFact(
                        AMOUNT_FACT_ID,
                        DISCLOSURE_ID,
                        DOCUMENT_ID,
                        FacilityInvestmentFactDefinition.AMOUNT.factKey(),
                        FactValueType.DECIMAL,
                        "5,296,200,000,000",
                        "원",
                        new DecimalFactValue(new BigDecimal("5296200000000")),
                        "KRW",
                        "KRW",
                        null,
                        null,
                        LocalDate.of(2024, 4, 24),
                        AccountingBasis.UNKNOWN,
                        FactGenerationMethod.DIRECT_NORMALIZED,
                        FactAvailabilityStatus.AVAILABLE,
                        FactNormalizationStatus.MAPPED,
                        FactValidationStatus.VERIFIED,
                        RECEIPT_NO,
                        "facility-fact-v1",
                        List.of(AMOUNT_EVIDENCE_ID, AMOUNT_NOTE_EVIDENCE_ID)
                ),
                new DisclosureFact(
                        PURPOSE_FACT_ID,
                        DISCLOSURE_ID,
                        DOCUMENT_ID,
                        FacilityInvestmentFactDefinition.PURPOSE.factKey(),
                        FactValueType.TEXT,
                        "차세대 DRAM 생산능력 확장",
                        null,
                        new TextFactValue("차세대 DRAM 생산능력 확장"),
                        null,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 4, 24),
                        AccountingBasis.UNKNOWN,
                        FactGenerationMethod.DIRECT_RAW,
                        FactAvailabilityStatus.AVAILABLE,
                        FactNormalizationStatus.NOT_APPLICABLE,
                        FactValidationStatus.VERIFIED,
                        RECEIPT_NO,
                        null,
                        List.of(PURPOSE_EVIDENCE_ID)
                )
        );
    }

    private void insertFixture() {
        jdbcTemplate.update("""
                INSERT INTO companies (
                    id, corp_code, stock_code, corp_name, listed_name,
                    corp_eng_name, market, industry, sector_no, sector,
                    listing_date, fiscal_month, market_cap,
                    market_cap_as_of, listed, source_provider,
                    source_dataset_version
                ) VALUES (
                    ?, '00066001', '000660', '에스케이하이닉스 주식회사', 'SK하이닉스',
                    'SK hynix Inc.', 'KOSPI', '반도체', 1, 'IT',
                    DATE '1996-12-26', 12, 1000,
                    DATE '2026-07-24', TRUE, 'CONTEST', 'test-v1'
                )
                """, COMPANY_ID);

        jdbcTemplate.update("""
                INSERT INTO disclosures (
                    id, source_doc_id, company_id, receipt_no,
                    category, source_group, raw_subtype, report_name,
                    correction, receipt_date, submitter,
                    base_year, base_month, manifest_path, file_format,
                    expected_file_count, source_provider,
                    source_dataset_version
                ) VALUES (
                    ?, 'exchange_20240424800596', ?, ?,
                    'EXCHANGE', 'exchange', '신규시설투자등', '신규시설투자등',
                    FALSE, DATE '2024-04-24', 'SK하이닉스',
                    NULL, NULL, 'exchange/facility-test', 'xml',
                    1, 'CONTEST', 'test-v1'
                )
                """, DISCLOSURE_ID, COMPANY_ID, RECEIPT_NO);

        jdbcTemplate.update("""
                INSERT INTO disclosure_documents (
                    id, disclosure_id, relative_path,
                    normalized_relative_path, file_name, file_extension,
                    document_role, document_name, content_format,
                    file_size_bytes, sha256, parse_status,
                    parser_name, parser_version, parsed_at
                ) VALUES (
                    ?, ?, 'exchange/facility-test/facility.xml',
                    'exchange/facility-test/facility.xml', 'facility.xml', 'xml',
                    'MAIN', '신규시설투자등', 'HTML',
                    100, ?, 'COMPLETED',
                    'DartHtmlDisclosureParser', '1.1.0', CURRENT_TIMESTAMP
                )
                """, DOCUMENT_ID, DISCLOSURE_ID, "a".repeat(64));

        jdbcTemplate.update("""
                INSERT INTO disclosure_sections (
                    id, disclosure_document_id, parent_section_id,
                    section_level, sequence_no, title,
                    source_line_start, source_line_end
                ) VALUES (
                    ?, ?, NULL, 1, 1, '신규시설투자등', 80, 120
                )
                """, SECTION_ID, DOCUMENT_ID);

        jdbcTemplate.update("""
                INSERT INTO disclosure_content_blocks (
                    id, disclosure_document_id, section_id,
                    block_type, sequence_no, text_content,
                    structured_content, source_line_start, source_line_end
                ) VALUES (
                    ?, ?, ?, 'TABLE', 2, NULL,
                    '{"schemaVersion":2,"table":{"rows":[]}}'::jsonb,
                    90, 110
                )
                """, BLOCK_ID, DOCUMENT_ID, SECTION_ID);
    }
}
