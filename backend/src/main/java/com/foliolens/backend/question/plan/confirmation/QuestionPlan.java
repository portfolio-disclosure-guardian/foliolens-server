package com.foliolens.backend.question.plan.confirmation;

import java.util.List;

// Spring 검증을 통과한 경우에만 생성 必
// 개념적으로 QueryPlan의 구현체 -> QuestionPlan
// AI가 만든 QuestionPlanCandidate에서 검증이 확인되된 QuestionPlan 생성
public record QuestionPlan(Long schemaVersion,
                List<ResolvedCompanyRef> companies,
                PlanTime time,
                // List<> interestProfiles,
                List<PlanStep> steps,
                List<String> warnings) {
}
