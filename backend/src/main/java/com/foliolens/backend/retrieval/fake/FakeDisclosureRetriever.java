package com.foliolens.backend.retrieval.fake;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.retrieval.DisclosureRetriever;
import com.foliolens.backend.retrieval.RetrievalCoverage;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.RetrievedDocument;
import com.foliolens.backend.retrieval.RetrievedEvidence;
import com.foliolens.backend.retrieval.RetrievedFact;

// A5 fake 수직 연결: GOLD-FACILITY-001 fixture 데이터를 QuestionPlan 내용과 무관하게 고정 반환한다.
// 실제 검색은 하지 않으며, 어떤 fact를 뺄지만 골라 완료/부분/답변불가 시나리오를 만든다.
@Component
@Profile("fake-retrieval")
public final class FakeDisclosureRetriever implements DisclosureRetriever {

    private static final Map<String, FactValueType> VALUE_TYPES = Map.of(
            "facility.target", FactValueType.TEXT,
            "facility.amount", FactValueType.DECIMAL,
            "facility.equity_amount", FactValueType.DECIMAL,
            "facility.equity_ratio", FactValueType.DECIMAL,
            "facility.purpose", FactValueType.TEXT,
            "facility.start_date", FactValueType.DATE,
            "facility.end_date", FactValueType.DATE,
            "facility.decision_date", FactValueType.DATE);

    private final Set<String> omitFactKeys;
    private final boolean includeDocuments;

    public FakeDisclosureRetriever() {
        this(Set.of(), true);
    }

    private FakeDisclosureRetriever(Set<String> omitFactKeys, boolean includeDocuments) {
        this.omitFactKeys = omitFactKeys;
        this.includeDocuments = includeDocuments;
    }

    public static FakeDisclosureRetriever complete() {
        return new FakeDisclosureRetriever(Set.of(), true);
    }

    public static FakeDisclosureRetriever missingFacts(String... factKeys) {
        return new FakeDisclosureRetriever(Set.of(factKeys), true);
    }

    public static FakeDisclosureRetriever noDocuments() {
        return new FakeDisclosureRetriever(VALUE_TYPES.keySet(), false);
    }

    @Override
    public RetrievalResult retrieve(QuestionPlan plan) {
        GoldenCase goldenCase = GoldFacility001Fixture.policy().goldenCases().getFirst();

        if (!includeDocuments) {
            return new RetrievalResult(List.of(), List.of(), List.of(), List.of(), plan.steps(),
                    List.copyOf(VALUE_TYPES.keySet()), new RetrievalCoverage(0, false), List.of(), "fake-1.0");
        }

        RetrievedDocument document = new RetrievedDocument(
                goldenCase.receiptNo(), goldenCase.receiptNo(), goldenCase.companyName(), "000660", "exchange",
                "신규시설투자등",
                LocalDate.parse(goldenCase.expectedNormalizedFacts().get("facility.decision_date")),
                "2. 투자내역", "투자내역 원문", 1.0);

        RetrievedEvidence evidence = new RetrievedEvidence(
                "EVD-1", goldenCase.receiptNo(), document.documentId(), DisclosureDocumentRole.MAIN,
                "2", EvidenceBlockType.PARAGRAPH, "투자내역 원문", 1.0, EvidenceStatus.VERIFIED);

        List<RetrievedFact> facts = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        goldenCase.expectedNormalizedFacts().forEach((factKey, value) -> {
            if (omitFactKeys.contains(factKey)) {
                missing.add(factKey);
                return;
            }
            facts.add(new RetrievedFact(
                    "FACT-" + factKey, goldenCase.receiptNo(), factKey, VALUE_TYPES.get(factKey),
                    value, value, unitFor(factKey), null, null,
                    List.of(evidence.evidenceId()), FactValidationStatus.VERIFIED));
        });

        return new RetrievalResult(List.of(document), facts, List.of(evidence), List.of(), plan.steps(),
                missing, new RetrievalCoverage(facts.size(), false), List.of(), "fake-1.0");
    }

    private static String unitFor(String factKey) {
        return switch (factKey) {
            case "facility.amount", "facility.equity_amount" -> "KRW";
            case "facility.equity_ratio" -> "%";
            default -> null;
        };
    }
}
