package com.foliolens.backend.question.plan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.foliolens.backend.company.domain.Company;

// Spring 검증을 통과한 경우에만 생성 必
// 개념적으로 QueryPlan의 구현체 -> QuestionPlan
// AI가 만든 QuestionPlanCandidate에서 검증이 통과되어 허가된 QueryPlan
public record QuestionPlan(Long schemaVersion,
                List<Company> companies,
                LocalDateTime time,
                Set<IntentType> intents,
                // List<> interestProfiles,
                List<PlanStep> steps,
                List<String> warnings) {
}
