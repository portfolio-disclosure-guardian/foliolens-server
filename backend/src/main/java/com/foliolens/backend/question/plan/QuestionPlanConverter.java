package com.foliolens.backend.question.plan;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.repository.CompanyRepository;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.plan.candidate.PlanStepCandidate;
import com.foliolens.backend.question.plan.candidate.PlanTimeCandidate;
import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;
import com.foliolens.backend.question.plan.confirmation.PlanStep;
import com.foliolens.backend.question.plan.confirmation.PlanTime;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.question.plan.confirmation.ResolvedCompanyRef;
import com.foliolens.backend.question.plan.toolinput.CalculateInput;
import com.foliolens.backend.question.plan.toolinput.LookupFactsInput;
import com.foliolens.backend.question.plan.toolinput.ResolveDisclosureHistoryInput;
import com.foliolens.backend.question.plan.toolinput.SearchDisclosuresInput;
import com.foliolens.backend.question.plan.toolinput.SearchEvidenceInput;
import com.foliolens.backend.question.plan.toolinput.ToolInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class QuestionPlanConverter {
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;
    private final int searchDisclosuresLimitMin;
    private final int searchDisclosuresLimitMax;

    public QuestionPlanConverter(
            CompanyRepository companyRepository,
            ObjectMapper objectMapper,
            @Value("${foliolens.question.plan.search-disclosures.limit-min:1}") int searchDisclosuresLimitMin,
            @Value("${foliolens.question.plan.search-disclosures.limit-max:50}") int searchDisclosuresLimitMax
    ) {
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
        this.searchDisclosuresLimitMin = searchDisclosuresLimitMin;
        this.searchDisclosuresLimitMax = searchDisclosuresLimitMax;
    }

    QuestionPlan candidateToConfirmation(QuestionPlanCandidate candidate) {
        validateStepReferences(candidate.steps());
        List<PlanStep> steps = candidate.steps().stream().map(this::convertToPlanStep).toList();
        validateFromBindings(steps);

        QuestionPlan questionPlan = new QuestionPlan(
                candidate.schemaVersion(),
                verifyCompaniesExistence(candidate.companiesMention()),
                convertToPlanTime(candidate.time()),
                steps,
                candidate.ambiguities()
        );
        return questionPlan;
    }

    // PLAN_STEP_INPUT_CONTRACT.md 6절 4번: stepId 중복, 자기 참조, 존재하지 않는 참조, 순환 의존성 거부.
    // 첫 슬라이스는 dependsOn이 앞선 step만 참조하도록 제한해 위상 정렬 없이 순환도 함께 막는다(6절 마지막 문단).
    private void validateStepReferences(List<PlanStepCandidate> steps) {
        Set<String> knownStepIds = new HashSet<>();
        for (PlanStepCandidate step : steps) {
            if (!knownStepIds.add(step.stepId())) {
                throw new BusinessException(ErrorCode.QUESTION_400_7, "stepId가 중복되었습니다: " + step.stepId());
            }
        }

        Set<String> precedingStepIds = new HashSet<>();
        for (PlanStepCandidate step : steps) {
            for (String dependsOnId : step.dependsOn()) {
                if (dependsOnId.equals(step.stepId())) {
                    throw new BusinessException(
                            ErrorCode.QUESTION_400_7, "step이 자기 자신을 dependsOn으로 참조합니다: " + step.stepId());
                }
                if (!precedingStepIds.contains(dependsOnId)) {
                    throw new BusinessException(
                            ErrorCode.QUESTION_400_7,
                            step.stepId() + ".dependsOn이 앞선 step에 없는 " + dependsOnId
                                    + "를 참조합니다(존재하지 않거나 순환 의존성일 수 있음)."
                    );
                }
            }
            precedingStepIds.add(step.stepId());
        }
    }

    // PLAN_STEP_INPUT_CONTRACT.md 6절 5·9번: *From 값이 dependsOn에 포함되고,
    // 가리키는 step의 tool이 소비 도구가 기대하는 출력 종류와 일치하는지 확인한다.
    private void validateFromBindings(List<PlanStep> steps) {
        Map<String, PlanStep> stepsById = new HashMap<>();
        for (PlanStep step : steps) {
            stepsById.put(step.stepId(), step);
        }

        for (PlanStep step : steps) {
            if (step.input() instanceof LookupFactsInput lookupFactsInput) {
                validateFromBinding(step, lookupFactsInput.disclosureIdsFrom(), ToolType.SEARCH_DISCLOSURES, stepsById);
            } else if (step.input() instanceof SearchEvidenceInput searchEvidenceInput) {
                validateFromBinding(
                        step,
                        searchEvidenceInput.disclosureIdsFrom(),
                        ToolType.SEARCH_DISCLOSURES,
                        stepsById
                );
            } else if (step.input() instanceof CalculateInput calculateInput) {
                validateFromBinding(step, calculateInput.factsFrom(), ToolType.LOOKUP_FACTS, stepsById);
            }
        }
    }

    private void validateFromBinding(
            PlanStep step, String fromStepId, ToolType expectedSourceTool, Map<String, PlanStep> stepsById) {
        if (!step.dependsOn().contains(fromStepId)) {
            throw new BusinessException(
                    ErrorCode.QUESTION_400_7,
                    step.stepId() + "의 참조(" + fromStepId + ")가 dependsOn에 포함되어 있지 않습니다."
            );
        }
        PlanStep sourceStep = stepsById.get(fromStepId);
        if (sourceStep == null || sourceStep.toolType() != expectedSourceTool) {
            throw new BusinessException(
                    ErrorCode.QUESTION_400_7,
                    step.stepId() + "의 참조(" + fromStepId + ")는 " + expectedSourceTool + " step이어야 합니다."
            );
        }
    }

    private PlanTime convertToPlanTime(PlanTimeCandidate timeCandidate) {
        if (timeCandidate == null
                || timeCandidate.receiptPeriod() == null
                || timeCandidate.reportPeriod() == null) {
            throw new BusinessException(ErrorCode.QUESTION_400_5, "QuestionPlanCandidate.time의 날짜 범위가 비어 있습니다.");
        }
        return PlanTime.parse(
                timeCandidate.receiptPeriod().from(),
                timeCandidate.receiptPeriod().to(),
                timeCandidate.reportPeriod().from(),
                timeCandidate.reportPeriod().to(),
                timeCandidate.asOf());
    }

    private PlanStep convertToPlanStep(PlanStepCandidate stepCandidate) {
        return new PlanStep(
                stepCandidate.stepId(),
                stepCandidate.toolType(),
                convertToToolInput(stepCandidate.toolType(), stepCandidate.input()),
                stepCandidate.dependsOn()
        );
    }

    private ToolInput convertToToolInput(ToolType toolType, JsonNode input) {
        Class<? extends ToolInput> inputType = switch (toolType) {
            case SEARCH_DISCLOSURES -> SearchDisclosuresInput.class;
            case LOOKUP_FACTS -> LookupFactsInput.class;
            case SEARCH_EVIDENCE -> SearchEvidenceInput.class;
            case RESOLVE_DISCLOSURE_HISTORY -> ResolveDisclosureHistoryInput.class;
            case CALCULATE -> CalculateInput.class;
        };

        ToolInput toolInput;
        try {
            toolInput = objectMapper.treeToValue(input, inputType);
        } catch (JacksonException e) {
            throw new BusinessException(
                    ErrorCode.QUESTION_400_4,
                    "step input이 " + toolType + " 형식과 맞지 않습니다: " + e.getMessage(), e
            );
        }

        if (toolInput instanceof SearchDisclosuresInput searchDisclosuresInput) {
            validateSearchDisclosuresLimit(searchDisclosuresInput.limit());
        }

        return toolInput;
    }

    private void validateSearchDisclosuresLimit(int limit) {
        if (limit < searchDisclosuresLimitMin || limit > searchDisclosuresLimitMax) {
            throw new BusinessException(
                    ErrorCode.QUESTION_400_6,
                    "SearchDisclosuresInput.limit은 " + searchDisclosuresLimitMin + ".."
                            + searchDisclosuresLimitMax + " 범위여야 합니다: " + limit
            );
        }
    }

    private List<ResolvedCompanyRef> verifyCompaniesExistence(List<String> companiesMention) {
        return companiesMention.stream()
                .map(this::resolveCompany)
                .toList();
    }

    // 대회에서 제공된 기업 데이터를 토대로 기업이름이 겹치는 경우가 없다고 가정하고 구현함.
    private ResolvedCompanyRef resolveCompany(String companyMention) {
        List<Company> matches = companyRepository.findByCorpName(companyMention);
        if (matches.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPANY_404_1, "기업을 찾을 수 없습니다: " + companyMention);
        }
        if (matches.size() > 1) {
            throw new BusinessException(ErrorCode.COMPANY_409_1, "기업명이 여러 기업과 일치합니다: " + companyMention);
        }
        Company company = matches.get(0);
        return new ResolvedCompanyRef(company.getId(), company.getCorpName());
    }
}
