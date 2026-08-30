package com.foliolens.backend.policy;

import java.util.List;

// ROLE_A_SPEC.md 5.3절이 요구하는 versioned AnswerPolicy fixture.
// 첫 수직 슬라이스(GOLD-FACILITY-001) 하나의 승인 범위만 담는다 — 공시 유형별 정책은
// 이 fixture가 실제로 동작한 뒤, 유형마다 별도 AnswerPolicy 인스턴스로 확장한다.
public record AnswerPolicy(
        String policyVersion, // GOLD 문서의 policyVersion, 예: "1.0-draft"
        String disclosureSubtype, // 예: "신규시설투자등"
        List<FactPolicy> facts,
        CalculationPolicy calculation,
        List<String> allowedExpressions,
        List<String> forbiddenExpressions,
        GoldenCase goldenCase) {
}
