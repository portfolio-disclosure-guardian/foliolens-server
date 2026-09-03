package com.foliolens.backend.answer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.RetrievedDocument;
import com.foliolens.backend.retrieval.RetrievedEvidence;
import com.foliolens.backend.retrieval.RetrievedFact;

@Component
public class AnswerReferenceValidator {

    public RetrievalResult verifiedOnly(RetrievalResult retrieval, AnswerPolicy policy) {
        List<RetrievedFact> facts = retrieval.facts().stream()
                .filter(fact -> fact.validationStatus() == FactValidationStatus.VERIFIED)
                .toList();
        Set<String> evidenceIds = facts.stream()
                .flatMap(fact -> fact.evidenceIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        List<RetrievedEvidence> evidences = retrieval.evidences().stream()
                .filter(evidence -> evidence.status() == EvidenceStatus.VERIFIED)
                .filter(evidence -> evidenceIds.contains(evidence.evidenceId()))
                .toList();
        Set<String> documentIds = evidences.stream()
                .map(RetrievedEvidence::documentId)
                .collect(java.util.stream.Collectors.toSet());
        List<RetrievedDocument> documents = retrieval.documents().stream()
                .filter(document -> documentIds.contains(document.documentId()))
                .toList();

        Set<String> verifiedFactKeys = facts.stream()
                .map(RetrievedFact::factKey)
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> missingFactKeys = new LinkedHashSet<>(retrieval.missingFactKeys());
        policy.facts().stream()
                .map(fact -> fact.factKey())
                .filter(factKey -> !verifiedFactKeys.contains(factKey))
                .forEach(missingFactKeys::add);

        return new RetrievalResult(
                documents,
                facts,
                evidences,
                List.of(),
                retrieval.executedSteps(),
                List.copyOf(missingFactKeys),
                retrieval.coverage(),
                List.of(),
                retrieval.retrievalVersion());
    }

    public List<RetrievedDocument> validate(
            RetrievalResult retrieval,
            List<CalculationResult> calculations,
            List<AnswerClaim> claims) {
        Map<String, RetrievedDocument> documents = uniqueBy(
                retrieval.documents(), RetrievedDocument::documentId, "document");
        Map<String, RetrievedEvidence> evidences = uniqueBy(
                retrieval.evidences(), RetrievedEvidence::evidenceId, "evidence");
        Map<String, RetrievedFact> facts = uniqueBy(
                retrieval.facts(), RetrievedFact::factId, "fact");

        for (RetrievedEvidence evidence : evidences.values()) {
            require(documents.containsKey(evidence.documentId()),
                    "evidence가 알 수 없는 document를 참조합니다: " + evidence.evidenceId());
        }
        for (RetrievedFact fact : facts.values()) {
            require(fact.validationStatus() == FactValidationStatus.VERIFIED,
                    "검증되지 않은 fact는 답변에 사용할 수 없습니다: " + fact.factId());
            require(!fact.evidenceIds().isEmpty(),
                    "fact에 원문 evidence가 없습니다: " + fact.factId());
            for (String evidenceId : fact.evidenceIds()) {
                RetrievedEvidence evidence = evidences.get(evidenceId);
                require(evidence != null, "fact가 알 수 없는 evidence를 참조합니다: " + fact.factId());
                require(evidence.status() == EvidenceStatus.VERIFIED,
                        "fact가 검증되지 않은 evidence를 참조합니다: " + fact.factId());
            }
        }
        for (CalculationResult calculation : calculations) {
            for (String factId : calculation.inputFactIds()) {
                RetrievedFact fact = facts.get(factId);
                require(fact != null, "계산이 알 수 없는 fact를 참조합니다: " + factId);
                require(fact.validationStatus() == FactValidationStatus.VERIFIED,
                        "계산이 검증되지 않은 fact를 참조합니다: " + factId);
            }
        }
        for (AnswerClaim claim : claims) {
            validateClaim(claim, facts, evidences, calculations);
        }

        Set<String> usedDocumentIds = claims.stream()
                .flatMap(claim -> claim.evidenceIds().stream())
                .map(evidences::get)
                .map(RetrievedEvidence::documentId)
                .collect(java.util.stream.Collectors.toSet());
        return retrieval.documents().stream()
                .filter(document -> usedDocumentIds.contains(document.documentId()))
                .toList();
    }

    private void validateClaim(
            AnswerClaim claim,
            Map<String, RetrievedFact> facts,
            Map<String, RetrievedEvidence> evidences,
            List<CalculationResult> calculations) {
        Set<String> requiredEvidenceIds = new HashSet<>();
        for (String factId : claim.factIds()) {
            RetrievedFact fact = facts.get(factId);
            require(fact != null, "claim이 알 수 없는 fact를 참조합니다: " + factId);
            require(fact.validationStatus() == FactValidationStatus.VERIFIED,
                    "claim이 검증되지 않은 fact를 참조합니다: " + factId);
            requiredEvidenceIds.addAll(fact.evidenceIds());
        }
        for (String evidenceId : claim.evidenceIds()) {
            require(evidences.containsKey(evidenceId),
                    "claim이 알 수 없는 evidence를 참조합니다: " + evidenceId);
        }
        require(new HashSet<>(claim.evidenceIds()).containsAll(requiredEvidenceIds),
                "claim이 fact의 입력 근거를 모두 참조하지 않습니다.");

        if (claim.type() == AnswerClaimType.CALCULATION) {
            long matches = calculations.stream()
                    .filter(calculation -> calculation.operation() == claim.calculationOperation())
                    .filter(calculation -> new HashSet<>(calculation.inputFactIds())
                            .equals(new HashSet<>(claim.factIds())))
                    .count();
            require(matches == 1, "claim의 계산 참조가 유일한 계산 결과와 일치하지 않습니다.");
        }
    }

    private static <T> Map<String, T> uniqueBy(
            List<T> values,
            Function<T, String> idExtractor,
            String type) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            String id = idExtractor.apply(value);
            require(id != null && !id.isBlank(), type + " ID가 비어 있습니다.");
            require(result.putIfAbsent(id, value) == null, type + " ID가 중복되었습니다: " + id);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.AGENT_502_1, message);
        }
    }
}
