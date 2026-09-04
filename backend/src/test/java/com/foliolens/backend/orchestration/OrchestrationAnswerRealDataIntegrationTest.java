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
import com.foliolens.backend.question.plan.toolinput.SearchEvidenceInput;
import com.foliolens.backend.retrieval.DisclosureRetriever;
import com.foliolens.backend.retrieval.RetrievalResult;

import tools.jackson.databind.ObjectMapper;

/**
 * A8("실제 데이터 연결") 인수 테스트. FakeDisclosureRetriever/FakeDisclosureCalculator가 아니라
 * 실제 Spring 프로필(default)에서 활성화되는 {@link com.foliolens.backend.retrieval.DefaultDisclosureRetriever}가
 * 진짜 PostgreSQL 스키마(companies/disclosures/disclosure_chunks 등)에 심어 둔 골든 케이스
 * (SK하이닉스 20240424800596) 데이터를 실제로 찾아오는지 검증한다.
 *
 * 아직 LOOKUP_FACTS 구현과 실제 DisclosureCalculator(역할 B 소유)가 없어서
 * {@code AnswerReferenceValidator.verifiedOnly}가 VERIFIED 근거가 하나도 없는 이번 결과를
 * 전부 걸러낸다. 두 번째 테스트는 그 결과로 지금 실제로 반환되는 값(UNANSWERABLE, 빈
 * retrieved_context)을 고정해 둔다. 역할 B가 fact 저장·검증과 실제 계산기를 연결하면
 * 이 테스트의 outcome/평가 응답 기대값을 COMPLETED 계열로 갱신해야 한다.
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
        when(hcxPlanGenerator.generatePlan(goldenCase.question())).thenReturn(goldenPlanCandidate());
    }

    @Test
    void 실제_DB에서_골든_공시의_근거를_검색한다() {
        RetrievalResult result = disclosureRetriever.retrieve(goldenPlan());

        assertThat(result.executedSteps()).hasSize(2);
        assertThat(result.documents()).isNotEmpty();
        assertThat(result.documents().getFirst().documentId()).isEqualTo(goldenCase.receiptNo());
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences())
                .anyMatch(evidence -> evidence.content().contains("5,296,200,000,000"));
        // LOOKUP_FACTS는 아직 실제 구현이 없어 fact는 항상 비어 있다(역할 B 의존).
        assertThat(result.facts()).isEmpty();
    }

    @Test
    void LOOKUP_FACTS_와_실제_계산기가_없으면_전체_파이프라인은_아직_UNANSWERABLE을_반환한다() {
        AnswerResult result = service.getAnswer(command());

        assertThat(result.outcome()).isEqualTo(AnswerOutcome.UNANSWERABLE);
        assertThat(result.calculations()).hasSize(1);
        assertThat(result.calculations().getFirst().verdict()).isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        // 검증된 fact가 없어 근거가 claim에 연결되지 않는다 - 역할 B의 fact 저장·검증이 남은 부분.
        assertThat(result.usedEvidences()).isEmpty();
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
                .andExpect(jsonPath("$.retrieved_context").isEmpty())
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
        PlanStep searchEvidence = new PlanStep(
                "s2",
                ToolType.SEARCH_EVIDENCE,
                new SearchEvidenceInput(
                        "s1",
                        List.of(),
                        List.of("facility.amount", "facility.equity_amount", "facility.equity_ratio",
                                "facility.purpose"),
                        List.of(),
                        List.of(),
                        List.of(),
                        10),
                List.of("s1"));
        return new QuestionPlan(
                1L,
                List.of(new ResolvedCompanyRef(COMPANY_ID, goldenCase.companyName())),
                new PlanTime(
                        new DateRange(decisionDate.withDayOfMonth(1), decisionDate.withDayOfMonth(decisionDate.lengthOfMonth())),
                        new DateRange(decisionDate.withDayOfMonth(1), decisionDate.withDayOfMonth(decisionDate.lengthOfMonth())),
                        decisionDate),
                List.of(searchDisclosures, searchEvidence),
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
        PlanStepCandidate searchEvidence = new PlanStepCandidate(
                "s2",
                ToolType.SEARCH_EVIDENCE,
                objectMapper.valueToTree(new SearchEvidenceInput(
                        "s1",
                        List.of(),
                        List.of("facility.amount", "facility.equity_amount", "facility.equity_ratio",
                                "facility.purpose"),
                        List.of(),
                        List.of(),
                        List.of(),
                        10)),
                List.of("s1"));

        return new QuestionPlanCandidate(
                1L,
                List.of(goldenCase.companyName()),
                new PlanTimeCandidate(decisionMonth, decisionMonth, decisionDate.toString()),
                Set.of(),
                List.of(searchDisclosures, searchEvidence),
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

    private String sha(UUID id) {
        return "%064x".formatted(id.getMostSignificantBits() ^ id.getLeastSignificantBits()).replace('-', '0');
    }
}
