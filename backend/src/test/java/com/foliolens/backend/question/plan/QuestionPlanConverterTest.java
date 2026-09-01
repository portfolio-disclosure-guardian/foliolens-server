package com.foliolens.backend.question.plan;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.repository.CompanyRepository;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.plan.candidate.DateRangeCandidate;
import com.foliolens.backend.question.plan.candidate.PlanStepCandidate;
import com.foliolens.backend.question.plan.candidate.PlanTimeCandidate;
import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;
import com.foliolens.backend.question.plan.confirmation.PlanStep;
import com.foliolens.backend.question.plan.confirmation.PlanTime;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.question.plan.confirmation.ResolvedCompanyRef;
import com.foliolens.backend.question.plan.toolinput.CalculateInput;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.question.plan.toolinput.LookupFactsInput;
import com.foliolens.backend.question.plan.toolinput.SearchDisclosuresInput;
import com.foliolens.backend.question.plan.toolinput.SearchEvidenceInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// PLAN_STEP_INPUT_CONTRACT.md 12절 4번: 후보 -> 검증 계획 변환 테스트
class QuestionPlanConverterTest {

    private static final DateRangeCandidate RECEIPT_PERIOD =
            new DateRangeCandidate("2024-04-01", "2024-04-30");
    private static final DateRangeCandidate REPORT_PERIOD =
            new DateRangeCandidate("2024-01-01", "2024-03-31");
    private static final String AS_OF = "2024-04-24";
    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 50;

    private CompanyRepository companyRepository;
    private ObjectMapper objectMapper;
    private QuestionPlanConverter converter;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        objectMapper = JsonMapper.builder().build();
        converter = new QuestionPlanConverter(companyRepository, objectMapper, LIMIT_MIN, LIMIT_MAX);
    }

    @Test
    void 후보_계획이_기업_시간_도구입력을_모두_검증된_계획으로_변환한다() {
        UUID companyId = UUID.randomUUID();
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        when(company.getCorpName()).thenReturn("SK하이닉스");
        when(companyRepository.findByCorpName("SK하이닉스")).thenReturn(List.of(company));

        PlanStepCandidate searchStep = new PlanStepCandidate(
                "s1",
                ToolType.SEARCH_DISCLOSURES,
                objectMapper.valueToTree(Map.of(
                        "categories", List.of("EXCHANGE"),
                        "subtypes", List.of("신규시설투자등"),
                        "titleTerms", List.of(),
                        "limit", 10
                )),
                List.of()
        );
        PlanStepCandidate lookupStep = new PlanStepCandidate(
                "s2",
                ToolType.LOOKUP_FACTS,
                objectMapper.valueToTree(Map.of(
                        "disclosureIdsFrom", "s1",
                        "factKeys", List.of("facility.amount", "facility.equity_amount")
                )),
                List.of("s1")
        );
        PlanStepCandidate calculateStep = new PlanStepCandidate(
                "s3",
                ToolType.CALCULATE,
                objectMapper.valueToTree(Map.of(
                        "factsFrom", "s2",
                        "operation", "RATIO",
                        "inputBindings", List.of("facility.amount", "facility.equity_amount")
                )),
                List.of("s2")
        );

        QuestionPlanCandidate candidate = new QuestionPlanCandidate(
                1L,
                List.of("SK하이닉스"),
                new PlanTimeCandidate(RECEIPT_PERIOD, REPORT_PERIOD, AS_OF),
                Set.of(),
                List.of(searchStep, lookupStep, calculateStep),
                List.of()
        );

        QuestionPlan plan = converter.candidateToConfirmation(candidate);

        assertThat(plan.schemaVersion()).isEqualTo(1L);
        assertThat(plan.companies()).containsExactly(new ResolvedCompanyRef(companyId, "SK하이닉스"));
        assertThat(plan.time()).isEqualTo(PlanTime.parse(
                "2024-04-01", "2024-04-30",
                "2024-01-01", "2024-03-31",
                "2024-04-24"));
        assertThat(plan.steps()).containsExactly(
                new PlanStep(
                        "s1",
                        ToolType.SEARCH_DISCLOSURES,
                        new SearchDisclosuresInput(
                                List.of(DisclosureCategory.EXCHANGE), List.of("신규시설투자등"), List.of(), 10),
                        List.of()
                ),
                new PlanStep(
                        "s2",
                        ToolType.LOOKUP_FACTS,
                        new LookupFactsInput("s1", List.of("facility.amount", "facility.equity_amount")),
                        List.of("s1")
                ),
                new PlanStep(
                        "s3",
                        ToolType.CALCULATE,
                        new CalculateInput(
                                "s2", CalculationOperation.RATIO,
                                List.of("facility.amount", "facility.equity_amount")),
                        List.of("s2")
                )
        );
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void 일치하는_기업이_없으면_COMPANY_404_1_예외를_던진다() {
        when(companyRepository.findByCorpName("없는기업")).thenReturn(List.of());

        QuestionPlanCandidate candidate = candidateWithCompanyOnly("없는기업");

        assertThatThrownBy(() -> converter.candidateToConfirmation(candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_404_1);
    }

    @Test
    void 기업명이_여러건과_일치하면_COMPANY_409_1_예외를_던진다() {
        when(companyRepository.findByCorpName("중복기업"))
                .thenReturn(List.of(mock(Company.class), mock(Company.class)));

        QuestionPlanCandidate candidate = candidateWithCompanyOnly("중복기업");

        assertThatThrownBy(() -> converter.candidateToConfirmation(candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_409_1);
    }

    @Test
    void 도구별_input_JSON이_toolType과_맞지_않으면_QUESTION_400_4_예외를_던진다() {
        JsonNode invalidInput = objectMapper.valueToTree(List.of("EXCHANGE"));
        PlanStepCandidate invalidStep = new PlanStepCandidate(
                "s1", ToolType.SEARCH_DISCLOSURES, invalidInput, List.of());

        QuestionPlanCandidate candidate = new QuestionPlanCandidate(
                1L,
                List.of(),
                new PlanTimeCandidate(RECEIPT_PERIOD, REPORT_PERIOD, AS_OF),
                Set.of(),
                List.of(invalidStep),
                List.of()
        );

        assertThatThrownBy(() -> converter.candidateToConfirmation(candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUESTION_400_4);
    }

    @Test
    void limit이_최소값보다_작으면_QUESTION_400_6_예외를_던진다() {
        QuestionPlanCandidate candidate = candidateWithStepOnly(searchDisclosuresStep("s1", LIMIT_MIN - 1));

        assertThatThrownBy(() -> converter.candidateToConfirmation(candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUESTION_400_6);
    }

    @Test
    void limit이_최대값보다_크면_QUESTION_400_6_예외를_던진다() {
        QuestionPlanCandidate candidate = candidateWithStepOnly(searchDisclosuresStep("s1", LIMIT_MAX + 1));

        assertThatThrownBy(() -> converter.candidateToConfirmation(candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUESTION_400_6);
    }

    @Test
    void limit이_경계값이면_허용된다() {
        QuestionPlanCandidate candidate = new QuestionPlanCandidate(
                1L,
                List.of(),
                new PlanTimeCandidate(RECEIPT_PERIOD, REPORT_PERIOD, AS_OF),
                Set.of(),
                List.of(searchDisclosuresStep("s1", LIMIT_MIN), searchDisclosuresStep("s2", LIMIT_MAX)),
                List.of()
        );

        QuestionPlan plan = converter.candidateToConfirmation(candidate);

        assertThat(plan.steps()).hasSize(2);
    }

    private PlanStepCandidate searchDisclosuresStep(String stepId, int limit) {
        JsonNode input = objectMapper.valueToTree(Map.of(
                "categories", List.of("EXCHANGE"),
                "subtypes", List.of("신규시설투자등"),
                "titleTerms", List.of(),
                "limit", limit
        ));
        return new PlanStepCandidate(stepId, ToolType.SEARCH_DISCLOSURES, input, List.of());
    }

    private QuestionPlanCandidate candidateWithCompanyOnly(String companyMention) {
        return new QuestionPlanCandidate(
                1L,
                List.of(companyMention),
                new PlanTimeCandidate(RECEIPT_PERIOD, REPORT_PERIOD, AS_OF),
                Set.of(),
                List.of(),
                List.of()
        );
    }

    private QuestionPlanCandidate candidateWithStepOnly(PlanStepCandidate step) {
        return new QuestionPlanCandidate(
                1L,
                List.of(),
                new PlanTimeCandidate(RECEIPT_PERIOD, REPORT_PERIOD, AS_OF),
                Set.of(),
                List.of(step),
                List.of()
        );
    }

    @Test
    void stepId가_중복되면_QUESTION_400_7_예외를_던진다() {
        QuestionPlanCandidate candidate = candidateWithSteps(List.of(
                searchDisclosuresStep("s1", 10),
                searchDisclosuresStep("s1", 10)
        ));

        assertQuestion4007(candidate);
    }

    @Test
    void step이_자기_자신을_dependsOn으로_참조하면_QUESTION_400_7_예외를_던진다() {
        JsonNode input = searchDisclosuresStep("unused", 10).input();
        PlanStepCandidate selfReferencing = new PlanStepCandidate(
                "s1", ToolType.SEARCH_DISCLOSURES, input, List.of("s1"));

        assertQuestion4007(candidateWithStepOnly(selfReferencing));
    }

    @Test
    void dependsOn이_존재하지_않는_step을_참조하면_QUESTION_400_7_예외를_던진다() {
        JsonNode input = searchDisclosuresStep("unused", 10).input();
        PlanStepCandidate danglingReference = new PlanStepCandidate(
                "s1", ToolType.SEARCH_DISCLOSURES, input, List.of("s99"));

        assertQuestion4007(candidateWithStepOnly(danglingReference));
    }

    @Test
    void dependsOn이_뒤에_나오는_step을_참조하면_QUESTION_400_7_예외를_던진다() {
        JsonNode input = searchDisclosuresStep("unused", 10).input();
        PlanStepCandidate refersToLaterStep = new PlanStepCandidate(
                "s1", ToolType.SEARCH_DISCLOSURES, input, List.of("s2"));
        PlanStepCandidate laterStep = new PlanStepCandidate(
                "s2", ToolType.SEARCH_DISCLOSURES, input, List.of());

        assertQuestion4007(candidateWithSteps(List.of(refersToLaterStep, laterStep)));
    }

    @Test
    void from값이_dependsOn에_없으면_QUESTION_400_7_예외를_던진다() {
        PlanStepCandidate searchStep = searchDisclosuresStep("s1", 10);
        PlanStepCandidate lookupStep = new PlanStepCandidate(
                "s2",
                ToolType.LOOKUP_FACTS,
                objectMapper.valueToTree(Map.of("disclosureIdsFrom", "s1", "factKeys", List.of("facility.amount"))),
                List.of() // s1을 쓰면서 dependsOn에는 안 넣음
        );

        assertQuestion4007(candidateWithSteps(List.of(searchStep, lookupStep)));
    }

    @Test
    void from값이_가리키는_step의_tool이_기대와_다르면_QUESTION_400_7_예외를_던진다() {
        PlanStepCandidate searchStep = searchDisclosuresStep("s1", 10);
        PlanStepCandidate calculateStep = new PlanStepCandidate(
                "s2",
                ToolType.CALCULATE,
                objectMapper.valueToTree(Map.of(
                        "factsFrom", "s1", // CALCULATE.factsFrom은 LOOKUP_FACTS step을 가리켜야 하는데 SEARCH_DISCLOSURES를 가리킴
                        "operation", "RATIO",
                        "inputBindings", List.of("facility.amount", "facility.equity_amount")
                )),
                List.of("s1")
        );

        assertQuestion4007(candidateWithSteps(List.of(searchStep, calculateStep)));
    }

    @Test
    void SEARCH_EVIDENCE가_공시검색_step을_참조하는_입력으로_변환된다() {
        PlanStepCandidate searchStep = searchDisclosuresStep("s1", 10);
        PlanStepCandidate evidenceStep = new PlanStepCandidate(
                "s2",
                ToolType.SEARCH_EVIDENCE,
                objectMapper.valueToTree(Map.of(
                        "disclosureIdsFrom", "s1",
                        "concepts", List.of("FACILITY_INVESTMENT"),
                        "factKeys", List.of("facility.amount"),
                        "sectionHints", List.of("투자내역"),
                        "keywords", List.of("투자금액"),
                        "blockTypes", List.of("paragraph", "table_row"),
                        "topK", 5
                )),
                List.of("s1")
        );

        QuestionPlan plan = converter.candidateToConfirmation(
                candidateWithSteps(List.of(searchStep, evidenceStep))
        );

        assertThat(plan.steps().get(1).input()).isEqualTo(
                new SearchEvidenceInput(
                        "s1",
                        List.of("FACILITY_INVESTMENT"),
                        List.of("facility.amount"),
                        List.of("투자내역"),
                        List.of("투자금액"),
                        List.of("PARAGRAPH", "TABLE_ROW"),
                        5
                )
        );
    }

    @Test
    void SEARCH_EVIDENCE의_from값이_dependsOn에_없으면_QUESTION_400_7_예외를_던진다() {
        PlanStepCandidate searchStep = searchDisclosuresStep("s1", 10);
        PlanStepCandidate evidenceStep = new PlanStepCandidate(
                "s2",
                ToolType.SEARCH_EVIDENCE,
                objectMapper.valueToTree(Map.of(
                        "disclosureIdsFrom", "s1",
                        "factKeys", List.of("facility.amount"),
                        "topK", 5
                )),
                List.of()
        );

        assertQuestion4007(candidateWithSteps(List.of(searchStep, evidenceStep)));
    }

    private void assertQuestion4007(QuestionPlanCandidate candidate) {
        assertThatThrownBy(() -> converter.candidateToConfirmation(candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUESTION_400_7);
    }

    private QuestionPlanCandidate candidateWithSteps(List<PlanStepCandidate> steps) {
        return new QuestionPlanCandidate(
                1L,
                List.of(),
                new PlanTimeCandidate(RECEIPT_PERIOD, REPORT_PERIOD, AS_OF),
                Set.of(),
                steps,
                List.of()
        );
    }
}
