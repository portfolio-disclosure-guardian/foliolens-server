package com.foliolens.backend.answer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.FactNecessity;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.RetrievedFact;

@Component
public class AnswerOutcomeJudge {

    public AnswerOutcome deriveOutcome(
            AnswerPolicy policy,
            List<String> missingFactKeys,
            CalculationVerdict calculationVerdict) {
        List<String> requiredFactKeys = policy.facts().stream()
                .filter(fact -> fact.necessity() == FactNecessity.REQUIRED)
                .map(fact -> fact.factKey())
                .toList();
        long missingRequiredCount = requiredFactKeys.stream()
                .filter(missingFactKeys::contains)
                .count();

        if (!requiredFactKeys.isEmpty() && missingRequiredCount == requiredFactKeys.size()) {
            return AnswerOutcome.UNANSWERABLE;
        }
        if (missingRequiredCount > 0 || calculationVerdict == CalculationVerdict.NOT_CALCULABLE) {
            return AnswerOutcome.PARTIAL;
        }
        return AnswerOutcome.COMPLETED;
    }

    public List<AnswerClaim> buildClaims(RetrievalResult retrieval, CalculationResult calculation) {
        List<AnswerClaim> claims = new ArrayList<>();

        for (RetrievedFact fact : retrieval.facts()) {
            claims.add(new AnswerClaim(
                    AnswerClaimType.FACT,
                    fact.factKey(),
                    List.of(fact.factId()),
                    fact.evidenceIds(),
                    null));
        }
        List<String> calculationEvidenceIds = retrieval.facts().stream()
                .filter(fact -> calculation.inputFactIds().contains(fact.factId()))
                .flatMap(fact -> fact.evidenceIds().stream())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
        if (calculation.verdict() != CalculationVerdict.NOT_CALCULABLE && !calculationEvidenceIds.isEmpty()) {
            claims.add(new AnswerClaim(
                    AnswerClaimType.CALCULATION,
                    "자기자본 대비 비율",
                    calculation.inputFactIds(),
                    calculationEvidenceIds,
                    calculation.operation()));
        }
        return List.copyOf(claims);
    }
}
