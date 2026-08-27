package com.foliolens.backend.question.plan;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.repository.CompanyRepository;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ROLE_A_TO_C_REQUEST.md 3.1절 기준 질문의 골든케이스.
 * "SK하이닉스가 2024년 4월 발표한 신규시설투자(접수번호 20240424800596)의
 *  투자금액과 목적은 무엇이고, 자기자본 대비 비율은 맞는가?"
 *
 * 실제 검색·fact·계산 구현(역할 B) 없이, ROLE_A_SPEC.md 6.2절이 지시한 대로
 * 고정 fake(CompanyRepository mock)로 A3 계획 변환 계약만 검증한다.
 * PLAN_STEP_INPUT_CONTRACT.md 5절의 s1->s2->s3 예시 JSON을 그대로 후보로 사용한다.
 */
class QuestionPlanConverterGoldenCaseTest {

    private static final UUID SK_HYNIX_ID = UUID.randomUUID();
    private static final List<String> FACILITY_FACT_KEYS = List.of(
            "facility.target",
            "facility.amount",
            "facility.equity_amount",
            "facility.equity_ratio",
            "facility.purpose",
            "facility.start_date",
            "facility.end_date",
            "facility.decision_date"
    );

    private CompanyRepository companyRepository;
    private ObjectMapper objectMapper;
    private QuestionPlanConverter converter;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        objectMapper = JsonMapper.builder().build();
        converter = new QuestionPlanConverter(companyRepository, objectMapper, 1, 50);

        Company skHynix = mock(Company.class);
        when(skHynix.getId()).thenReturn(SK_HYNIX_ID);
        when(skHynix.getCorpName()).thenReturn("SK하이닉스");
        when(companyRepository.findByCorpName("SK하이닉스")).thenReturn(List.of(skHynix));
    }

    @Test
    void SK하이닉스_신규시설투자_골든케이스가_검증된_3단계_계획으로_변환된다() {
        QuestionPlanCandidate candidate = goldenCandidate();

        QuestionPlan plan = converter.candidateToConfirmation(candidate);

        assertThat(plan.companies()).containsExactly(new ResolvedCompanyRef(SK_HYNIX_ID, "SK하이닉스"));

        assertThat(plan.time()).isEqualTo(PlanTime.parse(
                "2024-04-01", "2024-04-30",
                "2024-04-01", "2024-04-30",
                "2024-04-24"));

        assertThat(plan.steps()).containsExactly(
                new PlanStep(
                        "s1",
                        ToolType.SEARCH_DISCLOSURES,
                        new SearchDisclosuresInput(
                                List.of(DisclosureCategory.EXCHANGE),
                                List.of("신규시설투자등"),
                                List.of(),
                                10),
                        List.of()
                ),
                new PlanStep(
                        "s2",
                        ToolType.LOOKUP_FACTS,
                        new LookupFactsInput("s1", FACILITY_FACT_KEYS),
                        List.of("s1")
                ),
                new PlanStep(
                        "s3",
                        ToolType.CALCULATE,
                        new CalculateInput(
                                "s2",
                                CalculationOperation.RATIO,
                                List.of("facility.amount", "facility.equity_amount")),
                        List.of("s2")
                )
        );

        assertThat(plan.warnings()).isEmpty();
    }

    /**
     * PLAN_STEP_INPUT_CONTRACT.md 5절의 s1 -> s2 -> s3 예시 JSON과 동일한 후보.
     */
    private QuestionPlanCandidate goldenCandidate() {
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
                        "factKeys", FACILITY_FACT_KEYS
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

        return new QuestionPlanCandidate(
                1L,
                List.of("SK하이닉스"),
                new PlanTimeCandidate(
                        new DateRangeCandidate("2024-04-01", "2024-04-30"),
                        new DateRangeCandidate("2024-04-01", "2024-04-30"),
                        "2024-04-24"),
                Set.of(),
                List.of(searchStep, lookupStep, calculateStep),
                List.of()
        );
    }
}
