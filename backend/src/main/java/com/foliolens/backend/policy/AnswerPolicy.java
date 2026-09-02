package com.foliolens.backend.policy;

import java.util.List;

// ROLE_A_SPEC.md 5.3절과 finance_domain 00 공통규격이 요구하는 유형별 정책.
// 한 유형의 Fact·계산·표현 규칙과 복수 골든케이스를 함께 담는다.
public record AnswerPolicy(
        String policyVersion, // GOLD 문서의 policyVersion, 예: "1.0-draft"
        String disclosureSubtype, // 예: "신규시설투자등"
        List<FactPolicy> facts,
        List<CalculationPolicy> calculations,
        List<String> allowedExpressions,
        List<String> forbiddenExpressions,
        List<GoldenCase> goldenCases) {

    public AnswerPolicy(
            String policyVersion,
            String disclosureSubtype,
            List<FactPolicy> facts,
            CalculationPolicy calculation,
            List<String> allowedExpressions,
            List<String> forbiddenExpressions,
            List<GoldenCase> goldenCases) {
        this(
                policyVersion,
                disclosureSubtype,
                facts,
                List.of(calculation),
                allowedExpressions,
                forbiddenExpressions,
                goldenCases);
    }

    // 기존 GOLD-FACILITY-001 단일 계산 경로의 호환 accessor.
    public CalculationPolicy calculation() {
        return calculations.getFirst();
    }
}
