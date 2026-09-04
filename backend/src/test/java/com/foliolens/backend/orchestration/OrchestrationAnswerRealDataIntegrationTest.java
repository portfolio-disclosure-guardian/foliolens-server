package com.foliolens.backend.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.evaluation.controller.EvaluationAnswerController;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.RequestChannel;
import com.foliolens.backend.question.plan.HcxPlanGenerator;
import com.foliolens.backend.question.plan.ToolType;
import com.foliolens.backend.question.plan.candidate.DateRangeCandidate;
import com.foliolens.backend.question.plan.candidate.PlanStepCandidate;
import com.foliolens.backend.question.plan.candidate.PlanTimeCandidate;
import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;
import com.foliolens.backend.question.plan.confirmation.DateRange;
import com.foliolens.backend.question.plan.confirmation.PlanStep;
import com.foliolens.backend.question.plan.confirmation.PlanTime;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.question.plan.confirmation.ResolvedCompanyRef;
import com.foliolens.backend.question.plan.toolinput.SearchDisclosuresInput;
import com.foliolens.backend.question.plan.toolinput.CalculateInput;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.question.plan.toolinput.LookupFactsInput;
import com.foliolens.backend.retrieval.DisclosureRetriever;
import com.foliolens.backend.retrieval.RetrievalResult;

import tools.jackson.databind.ObjectMapper;

/**
 * A8("실제 데이터 연결") 인수 테스트. FakeDisclosureRetriever/FakeDisclosureCalculator가 아니라
 * 실제 Spring 프로필(default)에서 활성화되는 {@link com.foliolens.backend.retrieval.DefaultDisclosureRetriever}가
 * 진짜 PostgreSQL 스키마(companies/disclosures/disclosure_chunks 등)에 심어 둔 골든 케이스
 * (SK하이닉스 20240424800596) 데이터를 실제로 찾아오는지 검증한다.
 *
 * V12 VERIFIED Fact/Evidence 조회와 결정적 계산기까지 연결해 서비스 경계를
 * 실제 PostgreSQL에서 끝까지 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrchestrationAnswerRealDataIntegrationTest {

    private static final UUID COMPANY_ID = new UUID(901, 1);
    private static final UUID DISCLOSURE_ID = new UUID(902, 1);
    private static final UUID DOCUMENT_ID = new UUID(903, 1);
    private static final UUID SECTION_ID = new UUID(904, 1);
    private static final UUID BLOCK_ID = new UUID(905, 1);
    private static final UUID CHUNK_ID = new UUID(906, 1);
    private static final UUID CHUNK_SOURCE_ID = new UUID(907, 1);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("foliolens_test")
                    .withUsername("foliolens")
                    .withPassword("foliolens");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final GoldenCase goldenCase = GoldFacility001Fixture.policy().goldenCases().getFirst();
    private final LocalDate decisionDate =
            LocalDate.parse(goldenCase.expectedNormalizedFacts().get("facility.decision_date"));

    @MockitoBean
    private HcxPlanGenerator hcxPlanGenerator;

    @Autowired
    private DisclosureRetriever disclosureRetriever;

    @Autowired
    private OrchestrationAnswerService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE companies CASCADE");
        seedGoldenDisclosure();
        seedVerifiedFactsAndEvidences();
        when(hcxPlanGenerator.generatePlan(goldenCase.question())).thenReturn(goldenPlanCandidate());
    }

    @Test
    void 실제_DB에서_골든_공시의_근거를_검색한다() {
        RetrievalResult result = disclosureRetriever.retrieve(goldenPlan());

        assertThat(result.executedSteps()).hasSize(2);
        assertThat(result.documents()).isNotEmpty();
        assertThat(result.documents().getFirst().documentId())
                .isEqualTo(DOCUMENT_ID.toString());
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences())
                .anyMatch(evidence -> evidence.content().contains("5,296,200,000,000"));
        assertThat(result.evidences())
                .allMatch(evidence -> evidence.status()
                        == com.foliolens.backend.disclosure.domain.fact.EvidenceStatus.VERIFIED);
        assertThat(result.facts()).hasSize(8);
        assertThat(result.missingFactKeys()).isEmpty();
    }

    @Test
    void LOOKUP_FACTS와_결정적_계산기로_골든_질문을_COMPLETED로_처리한다() {
        AnswerResult result = service.getAnswer(command());

        assertThat(result.outcome()).isEqualTo(AnswerOutcome.COMPLETED);
        assertThat(result.calculations()).hasSize(1);
        assertThat(result.calculations().getFirst().verdict())
                .isEqualTo(CalculationVerdict.MATCH);
        assertThat(result.usedEvidences()).isNotEmpty();
    }

    @Test
    void HCX가_비교대상_factKey를_빠뜨려도_필수_fact를_보강해_COMPLETED로_처리한다() {
        when(hcxPlanGenerator.generatePlan(goldenCase.question()))
                .thenReturn(goldenPlanCandidateMissingEquityRatio());

        AnswerResult result = service.getAnswer(command());

        assertThat(result.outcome()).isEqualTo(AnswerOutcome.COMPLETED);
        assertThat(result.calculations()).hasSize(1);
        assertThat(result.calculations().getFirst().verdict())
                .isEqualTo(CalculationVerdict.MATCH);
    }

    @Test
    void GET_answer는_실제_데이터_경로에서도_5개_키_계약을_지킨다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EvaluationAnswerController(service)).build();

        mockMvc.perform(get("/answer")
                        .param("question_id", goldenCase.goldenCaseId())
                        .param("question", goldenCase.question()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question_id").value(goldenCase.goldenCaseId()))
                .andExpect(jsonPath("$.question").value(goldenCase.question()))
                .andExpect(jsonPath("$.retrieved_context").isNotEmpty())
                .andExpect(jsonPath("$.think_trace").isNotEmpty())
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    private AnswerQuestionCommand command() {
        return new AnswerQuestionCommand(
                goldenCase.goldenCaseId(), goldenCase.question(), RequestChannel.EVALUATION, "a8-real-data-request");
    }

    private QuestionPlan goldenPlan() {
        PlanStep searchDisclosures = new PlanStep(
                "s1",
                ToolType.SEARCH_DISCLOSURES,
                new SearchDisclosuresInput(
                        List.of(DisclosureCategory.EXCHANGE), List.of("신규시설투자등"), List.of(), 10),
                List.of());
        PlanStep lookupFacts = new PlanStep(
                "s2",
                ToolType.LOOKUP_FACTS,
                new LookupFactsInput(
                        "s1",
                        List.of("facility.amount", "facility.equity_amount", "facility.equity_ratio",
                                "facility.purpose", "facility.target", "facility.start_date",
                                "facility.end_date", "facility.decision_date")),
                List.of("s1"));
        PlanStep calculate = new PlanStep(
                "s3",
                ToolType.CALCULATE,
                new CalculateInput(
                        "s2",
                        CalculationOperation.RATIO,
                        List.of("facility.amount", "facility.equity_amount")),
                List.of("s2"));
        return new QuestionPlan(
                1L,
                List.of(new ResolvedCompanyRef(COMPANY_ID, goldenCase.companyName())),
                new PlanTime(
                        new DateRange(decisionDate.withDayOfMonth(1), decisionDate.withDayOfMonth(decisionDate.lengthOfMonth())),
                        new DateRange(decisionDate.withDayOfMonth(1), decisionDate.withDayOfMonth(decisionDate.lengthOfMonth())),
                        decisionDate),
                List.of(searchDisclosures, lookupFacts, calculate),
                List.of());
    }

    private QuestionPlanCandidate goldenPlanCandidate() {
        String monthStart = decisionDate.withDayOfMonth(1).toString();
        String monthEnd = decisionDate.withDayOfMonth(decisionDate.lengthOfMonth()).toString();
        DateRangeCandidate decisionMonth = new DateRangeCandidate(monthStart, monthEnd);

        PlanStepCandidate searchDisclosures = new PlanStepCandidate(
                "s1",
                ToolType.SEARCH_DISCLOSURES,
                objectMapper.valueToTree(new SearchDisclosuresInput(
                        List.of(DisclosureCategory.EXCHANGE), List.of("신규시설투자등"), List.of(), 10)),
                List.of());
        PlanStepCandidate lookupFacts = new PlanStepCandidate(
                "s2",
                ToolType.LOOKUP_FACTS,
                objectMapper.valueToTree(new LookupFactsInput(
                        "s1",
                        List.of("facility.amount", "facility.equity_amount", "facility.equity_ratio",
                                "facility.purpose", "facility.target", "facility.start_date",
                                "facility.end_date", "facility.decision_date"))),
                List.of("s1"));
        PlanStepCandidate calculate = new PlanStepCandidate(
                "s3",
                ToolType.CALCULATE,
                objectMapper.valueToTree(new CalculateInput(
                        "s2",
                        CalculationOperation.RATIO,
                        List.of("facility.amount", "facility.equity_amount"))),
                List.of("s2"));

        return new QuestionPlanCandidate(
                1L,
                List.of(goldenCase.companyName()),
                new PlanTimeCandidate(decisionMonth, decisionMonth, decisionDate.toString()),
                Set.of(),
                List.of(searchDisclosures, lookupFacts, calculate),
                List.of());
    }

    // 실 HCX가 계산 입력 fact(facility.amount/equity_amount)만 요청하고 비교 대상인
    // facility.equity_ratio를 LOOKUP_FACTS factKeys에서 빠뜨리는 경우를 그대로 재현한다.
    private QuestionPlanCandidate goldenPlanCandidateMissingEquityRatio() {
        String monthStart = decisionDate.withDayOfMonth(1).toString();
        String monthEnd = decisionDate.withDayOfMonth(decisionDate.lengthOfMonth()).toString();
        DateRangeCandidate decisionMonth = new DateRangeCandidate(monthStart, monthEnd);

        PlanStepCandidate searchDisclosures = new PlanStepCandidate(
                "s1",
                ToolType.SEARCH_DISCLOSURES,
                objectMapper.valueToTree(new SearchDisclosuresInput(
                        List.of(DisclosureCategory.EXCHANGE), List.of("신규시설투자등"), List.of(), 10)),
                List.of());
        PlanStepCandidate lookupFacts = new PlanStepCandidate(
                "s2",
                ToolType.LOOKUP_FACTS,
                objectMapper.valueToTree(new LookupFactsInput(
                        "s1",
                        List.of("facility.amount", "facility.equity_amount", "facility.purpose"))),
                List.of("s1"));
        PlanStepCandidate calculate = new PlanStepCandidate(
                "s3",
                ToolType.CALCULATE,
                objectMapper.valueToTree(new CalculateInput(
                        "s2",
                        CalculationOperation.RATIO,
                        List.of("facility.amount", "facility.equity_amount"))),
                List.of("s2"));

        return new QuestionPlanCandidate(
                1L,
                List.of(goldenCase.companyName()),
                new PlanTimeCandidate(decisionMonth, decisionMonth, decisionDate.toString()),
                Set.of(),
                List.of(searchDisclosures, lookupFacts, calculate),
                List.of());
    }

    private void seedGoldenDisclosure() {
        jdbcTemplate.update(
                """
                INSERT INTO companies (
                    id, corp_code, stock_code, corp_name, listed_name,
                    corp_eng_name, market, industry, sector_no, sector,
                    listing_date, fiscal_month, market_cap,
                    market_cap_as_of, listed, source_provider,
                    source_dataset_version
                ) VALUES (
                    ?, '00000660', '000660', ?, ?,
                    'SK hynix', 'KOSPI', '반도체', 1, '반도체',
                    DATE '1996-12-26', 12, 1000,
                    DATE '2026-07-24', TRUE, 'CONTEST', 'test-v1'
                )
                """,
                COMPANY_ID, goldenCase.companyName(), goldenCase.companyName());

        jdbcTemplate.update(
                """
                INSERT INTO disclosures (
                    id, source_doc_id, company_id, receipt_no,
                    category, source_group, raw_subtype, report_name,
                    correction, receipt_date, submitter,
                    manifest_path, file_format, expected_file_count,
                    source_provider, source_dataset_version
                ) VALUES (
                    ?, ?, ?, ?,
                    'EXCHANGE', 'exchange', '신규시설투자등', '신규시설투자등',
                    FALSE, ?, ?,
                    'exchange/test', 'xml', 1,
                    'CONTEST', 'test-v1'
                )
                """,
                DISCLOSURE_ID, "exchange_" + goldenCase.receiptNo(), COMPANY_ID, goldenCase.receiptNo(),
                decisionDate, goldenCase.companyName());

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
                    ?, ?, 'exchange/test/facility-main.xml',
                    'exchange/test/facility-main.xml', 'facility-main.xml', 'xml',
                    'MAIN', NULL, 'DART_XML',
                    100, ?, 'COMPLETED',
                    'test-parser', 'test-parser-v1', CURRENT_TIMESTAMP,
                    'COMPLETED', 'DartXmlDisclosureChunkGenerator',
                    'dart-xml-chunk-v3', CURRENT_TIMESTAMP
                )
                """,
                DOCUMENT_ID, DISCLOSURE_ID, sha(DOCUMENT_ID));

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
                SECTION_ID, DOCUMENT_ID);

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_content_blocks (
                    id, disclosure_document_id, section_id,
                    block_type, sequence_no, text_content,
                    structured_content, source_line_start, source_line_end
                ) VALUES (
                    ?, ?, ?, 'TABLE', 1, NULL,
                    '{"schemaVersion":2,"table":{"rows":[]}}'::jsonb,
                    100, 120
                )
                """,
                BLOCK_ID, DOCUMENT_ID, SECTION_ID);

        String bodyText = "투자대상 " + goldenCase.expectedNormalizedFacts().get("facility.target")
                + "\n투자금액 5,296,200,000,000원"
                + "\n자기자본 53,503,752,397,611원"
                + "\n자기자본대비 9.90%"
                + "\n투자목적 " + goldenCase.expectedNormalizedFacts().get("facility.purpose");
        String searchText = "신규시설투자 투자내역 투자대상 투자금액 5,296,200,000,000원 "
                + "자기자본 53,503,752,397,611원 자기자본대비 9.90% "
                + "투자목적 " + goldenCase.expectedNormalizedFacts().get("facility.purpose");

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_chunks (
                    id, disclosure_document_id, section_id,
                    chunk_type, chunk_sequence_no, section_path,
                    body_text, search_text,
                    generator_name, generator_version
                ) VALUES (
                    ?, ?, ?, 'TABLE', 1,
                    'II. 사업의 내용 > 신규시설투자', ?, ?,
                    'DartXmlDisclosureChunkGenerator', 'dart-xml-chunk-v3'
                )
                """,
                CHUNK_ID, DOCUMENT_ID, SECTION_ID, bodyText, searchText);

        jdbcTemplate.update(
                """
                INSERT INTO disclosure_chunk_sources (
                    id, disclosure_chunk_id, disclosure_document_id,
                    content_block_id, source_order, block_sequence_no,
                    source_line_start, source_line_end,
                    table_row_index_start, table_row_index_end
                ) VALUES (
                    ?, ?, ?, ?, 1, 1, 100, 120, 0, 4
                )
                """,
                CHUNK_SOURCE_ID, CHUNK_ID, DOCUMENT_ID, BLOCK_ID);
    }

    private void seedVerifiedFactsAndEvidences() {
        var values = new java.util.LinkedHashMap<String, FactSeed>();
        values.put("facility.target", FactSeed.text(
                goldenCase.expectedNormalizedFacts().get("facility.target")));
        values.put("facility.amount", FactSeed.decimal(
                "5,296,200,000,000", "원", "5296200000000", "KRW", "KRW"));
        values.put("facility.equity_amount", FactSeed.decimal(
                "53,503,752,397,611", "원", "53503752397611", "KRW", "KRW"));
        values.put("facility.equity_ratio", FactSeed.decimal(
                "9.90", "%", "9.90", "PERCENT", null));
        values.put("facility.purpose", FactSeed.text(
                goldenCase.expectedNormalizedFacts().get("facility.purpose")));
        values.put("facility.start_date", FactSeed.date("2024-04-24"));
        values.put("facility.end_date", FactSeed.date("2026-10-30"));
        values.put("facility.decision_date", FactSeed.date("2024-04-24"));

        int index = 0;
        for (var entry : values.entrySet()) {
            UUID evidenceId = new UUID(910, ++index);
            UUID factId = new UUID(920, index);
            FactSeed seed = entry.getValue();

            jdbcTemplate.update(
                    """
                    INSERT INTO disclosure_evidences (
                        id, disclosure_id, disclosure_document_id,
                        receipt_no, document_name, document_file_role,
                        event_document_role, section_id, section_path,
                        content_block_id, block_type, table_index_or_name,
                        source_line_start, source_line_end,
                        table_nesting_path, table_row_index, table_cell_index,
                        source_text, row_label, column_label,
                        raw_value, raw_unit, status
                    ) VALUES (
                        ?, ?, ?, ?, '신규시설투자등', 'MAIN',
                        'ORIGINAL', ?, 'II. 사업의 내용 > 신규시설투자',
                        ?, 'TABLE_CELL', '투자내역',
                        100, 120, 'root', ?, 1,
                        ?, ?, '내용', ?, ?, 'VERIFIED'
                    )
                    """,
                    evidenceId, DISCLOSURE_ID, DOCUMENT_ID,
                    goldenCase.receiptNo(), SECTION_ID, BLOCK_ID, index,
                    entry.getKey() + " " + seed.rawValue,
                    entry.getKey(), seed.rawValue, seed.rawUnit);

            jdbcTemplate.update(
                    """
                    INSERT INTO disclosure_facts (
                        id, disclosure_id, disclosure_document_id,
                        fact_key, value_type, raw_value, raw_unit,
                        normalized_decimal_value, normalized_date_value,
                        normalized_text_value, normalized_unit, currency,
                        accounting_basis, generation_method,
                        availability_status, normalization_status,
                        validation_status, source_receipt_no, policy_version
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?,
                        'UNKNOWN', 'DIRECT_NORMALIZED',
                        'AVAILABLE', 'MAPPED', 'VERIFIED', ?, 'facility-v1'
                    )
                    """,
                    factId, DISCLOSURE_ID, DOCUMENT_ID,
                    entry.getKey(), seed.valueType, seed.rawValue, seed.rawUnit,
                    seed.decimalValue, seed.dateValue, seed.textValue,
                    seed.normalizedUnit, seed.currency,
                    goldenCase.receiptNo());

            jdbcTemplate.update(
                    """
                    INSERT INTO disclosure_fact_evidences (
                        disclosure_fact_id, disclosure_evidence_id,
                        disclosure_document_id, evidence_order
                    ) VALUES (?, ?, ?, 1)
                    """,
                    factId, evidenceId, DOCUMENT_ID);
        }
    }

    private String sha(UUID id) {
        return "%064x".formatted(id.getMostSignificantBits() ^ id.getLeastSignificantBits()).replace('-', '0');
    }

    private record FactSeed(
            String valueType,
            String rawValue,
            String rawUnit,
            java.math.BigDecimal decimalValue,
            LocalDate dateValue,
            String textValue,
            String normalizedUnit,
            String currency
    ) {
        private static FactSeed decimal(
                String rawValue,
                String rawUnit,
                String normalizedValue,
                String normalizedUnit,
                String currency
        ) {
            return new FactSeed(
                    "DECIMAL", rawValue, rawUnit,
                    new java.math.BigDecimal(normalizedValue), null, null,
                    normalizedUnit, currency
            );
        }

        private static FactSeed text(String value) {
            return new FactSeed(
                    "TEXT", value, null, null, null, value, null, null
            );
        }

        private static FactSeed date(String value) {
            return new FactSeed(
                    "DATE", value, null, null, LocalDate.parse(value), null,
                    null, null
            );
        }
    }
}
