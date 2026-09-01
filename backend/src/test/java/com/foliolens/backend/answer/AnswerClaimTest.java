package com.foliolens.backend.answer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

// ROLE_A_SPEC.md 2.3-5 불변 규칙: FACT는 evidence/fact, CALCULATION은 계산+입력 근거가 있어야 한다.
class AnswerClaimTest {

    @Test
    void FACT_주장은_evidence나_fact가_없으면_거부된다() {
        assertThrows(IllegalArgumentException.class, () -> new AnswerClaim(
                AnswerClaimType.FACT, "text", List.of(), List.of(), null));
    }

    @Test
    void CALCULATION_주장은_계산_참조나_근거가_없으면_거부된다() {
        assertThrows(IllegalArgumentException.class, () -> new AnswerClaim(
                AnswerClaimType.CALCULATION, "text", List.of(), List.of("doc-1"), null));
        assertThrows(IllegalArgumentException.class, () -> new AnswerClaim(
                AnswerClaimType.CALCULATION, "text", List.of(), List.of(), CalculationOperation.RATIO));
    }

    @Test
    void 근거를_갖춘_주장은_생성된다() {
        new AnswerClaim(AnswerClaimType.FACT, "text", List.of("fact-1"), List.of(), null);
        new AnswerClaim(AnswerClaimType.CALCULATION, "text", List.of(), List.of("doc-1"), CalculationOperation.RATIO);
    }
}
