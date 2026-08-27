package com.foliolens.backend.question.plan.candidate;

import java.util.List;
import java.util.Set;

// 사용자의 프롬프트 요구사항에 맞게끔 AI가 출력한 미검증 공시 검색 계획
public record QuestionPlanCandidate(Long schemaVersion,
        List<String> companiesMention, //AI가 생성하는 답변에서는 Company 객체를 생성하지 않음.
        PlanTimeCandidate time,
        Set<String> interestCodes,
        List<PlanStepCandidate> steps,
        List<String> ambiguities) {
}
