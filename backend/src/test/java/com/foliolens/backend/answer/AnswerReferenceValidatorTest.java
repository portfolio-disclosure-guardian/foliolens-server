package com.foliolens.backend.answer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.RetrievedEvidence;
import com.foliolens.backend.retrieval.RetrievedFact;
import com.foliolens.backend.retrieval.fake.FakeDisclosureRetriever;

class AnswerReferenceValidatorTest {

    private final AnswerReferenceValidator validator = new AnswerReferenceValidator();
    private final AnswerOutcomeJudge judge = new AnswerOutcomeJudge();
    private final RetrievalResult retrieval = FakeDisclosureRetriever.complete()
            .retrieve(GoldFacility001Fixture.questionPlan());
    private final CalculationResult calculation = new FakeDisclosureCalculator().calculate(
            new CalculationCommand(
                    CalculationOperation.RATIO,
                    new ComparisonBasis(true, true, true, true)),
            retrieval.facts());

    @Test
    void fixture의_fact_evidence_document와_계산_참조가_연결된다() {
        assertThat(validator.validate(
                retrieval,
                List.of(calculation),
                judge.buildClaims(retrieval, calculation)))
                .containsExactly(retrieval.documents().getFirst());
    }

    @Test
    void HCX에_전달할_검색결과에는_검증된_fact와_그_근거만_남긴다() {
        RetrievedEvidence verified = retrieval.evidences().getFirst();
        RetrievedEvidence candidate = new RetrievedEvidence(
                "EVD-CANDIDATE",
                verified.disclosureId(),
                verified.documentId(),
                verified.documentRole(),
                "candidate-section",
                verified.blockType(),
                "미검증 후보 내용",
                0.9,
                com.foliolens.backend.disclosure.domain.fact.EvidenceStatus.CANDIDATE);
        RetrievedFact original = retrieval.facts().getFirst();
        RetrievedFact unvalidated = new RetrievedFact(
                "FACT-CANDIDATE",
                original.disclosureId(),
                "facility.unverified",
                original.valueType(),
                "미검증 후보 값",
                "미검증 후보 값",
                original.unit(),
                original.periodStart(),
                original.periodEnd(),
                List.of(candidate.evidenceId()),
                com.foliolens.backend.disclosure.domain.fact.FactValidationStatus.UNVALIDATED);
        RetrievalResult mixed = new RetrievalResult(
                retrieval.documents(),
                java.util.stream.Stream.concat(retrieval.facts().stream(), java.util.stream.Stream.of(unvalidated)).toList(),
                List.of(verified, candidate),
                retrieval.history(),
                retrieval.executedSteps(),
                retrieval.missingFactKeys(),
                retrieval.coverage(),
                List.of("미검증 후보 경고"),
                retrieval.retrievalVersion());

        RetrievalResult filtered = validator.verifiedOnly(mixed, GoldFacility001Fixture.policy());

        assertThat(filtered.facts()).doesNotContain(unvalidated);
        assertThat(filtered.evidences()).containsExactly(verified);
        assertThat(filtered.documents()).containsExactlyElementsOf(retrieval.documents());
        assertThat(filtered.history()).isEmpty();
        assertThat(filtered.warnings()).isEmpty();
    }

    @Test
    void 알_수_없는_evidence를_참조한_claim은_거부한다() {
        AnswerClaim invalid = new AnswerClaim(
                AnswerClaimType.FACT,
                "invalid",
                List.of(),
                List.of("EVD-UNKNOWN"),
                null);

        assertInvalidReference(() -> validator.validate(retrieval, List.of(calculation), List.of(invalid)));
    }

    @Test
    void evidence가_알_수_없는_document를_참조하면_거부한다() {
        RetrievedEvidence original = retrieval.evidences().getFirst();
        RetrievedEvidence invalid = new RetrievedEvidence(
                original.evidenceId(),
                original.disclosureId(),
                "DOC-UNKNOWN",
                original.documentRole(),
                original.sectionId(),
                original.blockType(),
                original.content(),
                original.relevanceScore(),
                original.status());
        RetrievalResult invalidRetrieval = new RetrievalResult(
                retrieval.documents(),
                retrieval.facts(),
                List.of(invalid),
                retrieval.history(),
                retrieval.executedSteps(),
                retrieval.missingFactKeys(),
                retrieval.coverage(),
                retrieval.warnings(),
                retrieval.retrievalVersion());

        assertInvalidReference(() -> validator.validate(
                invalidRetrieval,
                List.of(calculation),
                judge.buildClaims(invalidRetrieval, calculation)));
    }

    @Test
    void calculation_claim이_계산_입력_fact를_바꾸면_거부한다() {
        List<AnswerClaim> claims = new ArrayList<>(judge.buildClaims(retrieval, calculation));
        AnswerClaim calculationClaim = claims.getLast();
        claims.set(claims.size() - 1, new AnswerClaim(
                calculationClaim.type(),
                calculationClaim.text(),
                List.of(retrieval.facts().getFirst().factId()),
                calculationClaim.evidenceIds(),
                calculationClaim.calculationOperation()));

        assertInvalidReference(() -> validator.validate(retrieval, List.of(calculation), claims));
    }

    private static void assertInvalidReference(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AGENT_502_1);
    }
}
