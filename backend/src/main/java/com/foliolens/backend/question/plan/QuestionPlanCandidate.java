package com.foliolens.backend.question.plan;

import java.time.Instant;
import java.util.List;
import java.util.Set;

// 사용자의 프롬프트 요구사항에 맞게끔 AI가 출력한 미검증 공시 검색 계획
public record QuestionPlanCandidate(Long schemaVersion,
        String companiesMention,
        Instant from,
        Instant to,
        Set<String> interestCodes,
        List<PlanStep> steps,
        List<String> ambiguities) {
}
