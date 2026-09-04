package com.foliolens.backend.retrieval;

import com.foliolens.backend.disclosure.domain.fact.CodeFactValue;
import com.foliolens.backend.disclosure.domain.fact.DateFactValue;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.FactValue;
import com.foliolens.backend.disclosure.domain.fact.TextFactValue;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchHit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 영속 도메인 Fact/Evidence를 역할 A의 검색 결과 계약으로 변환한다.
 */
@Component
public class DisclosureFactRetrievalMapper {

    public RetrievedFact toRetrievedFact(DisclosureFact source) {
        Objects.requireNonNull(source, "source Fact는 필수입니다.");
        return new RetrievedFact(
                source.factId().toString(),
                source.disclosureId().toString(),
                source.factKey(),
                source.valueType(),
                source.rawValue(),
                normalizedValue(source.normalizedValue()),
                source.normalizedUnit(),
                source.periodStart(),
                source.periodEnd(),
                source.evidenceIds().stream().map(UUID::toString).toList(),
                source.validationStatus()
        );
    }

    public RetrievedEvidence toRetrievedEvidence(
            DisclosureEvidence source
    ) {
        Objects.requireNonNull(source, "source Evidence는 필수입니다.");
        return new RetrievedEvidence(
                source.evidenceId().toString(),
                source.disclosureId().toString(),
                source.disclosureDocumentId().toString(),
                source.documentFileRole(),
                source.sectionId() == null
                        ? null
                        : source.sectionId().toString(),
                source.sectionPath(),
                source.blockType(),
                source.value().sourceText(),
                1.0,
                source.status(),
                List.of()
        );
    }

    public List<RetrievedDocument> toRetrievedDocuments(
            List<DisclosureEvidence> evidences,
            Map<UUID, DisclosureMetadataSearchHit> metadataByDisclosureId
    ) {
        Objects.requireNonNull(evidences, "evidences는 필수입니다.");
        Objects.requireNonNull(
                metadataByDisclosureId,
                "metadataByDisclosureId는 필수입니다."
        );

        LinkedHashMap<UUID, DocumentAccumulator> documents =
                new LinkedHashMap<>();
        for (DisclosureEvidence evidence : evidences) {
            DisclosureMetadataSearchHit metadata = metadataByDisclosureId.get(
                    evidence.disclosureId()
            );
            if (metadata == null) {
                continue;
            }
            documents.computeIfAbsent(
                    evidence.disclosureDocumentId(),
                    ignored -> new DocumentAccumulator(metadata)
            ).add(evidence);
        }

        return documents.entrySet().stream()
                .map(entry -> entry.getValue().toDocument(entry.getKey()))
                .toList();
    }

    private String normalizedValue(FactValue value) {
        if (value == null) {
            return null;
        }
        if (value instanceof DecimalFactValue decimal) {
            return decimal.value().toPlainString();
        }
        if (value instanceof DateFactValue date) {
            return date.value().toString();
        }
        if (value instanceof TextFactValue text) {
            return text.value();
        }
        if (value instanceof CodeFactValue code) {
            return code.value();
        }
        throw new IllegalStateException(
                "지원하지 않는 FactValue 타입입니다: "
                        + value.getClass().getName()
        );
    }

    private static final class DocumentAccumulator {

        private final DisclosureMetadataSearchHit metadata;
        private final LinkedHashMap<UUID, DisclosureEvidence> evidences =
                new LinkedHashMap<>();

        private DocumentAccumulator(DisclosureMetadataSearchHit metadata) {
            this.metadata = metadata;
        }

        private void add(DisclosureEvidence evidence) {
            evidences.putIfAbsent(evidence.evidenceId(), evidence);
        }

        private RetrievedDocument toDocument(UUID documentId) {
            DisclosureEvidence first = evidences.values().iterator().next();
            String content = evidences.values().stream()
                    .map(evidence -> evidence.value().sourceText())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("\n"));
            return new RetrievedDocument(
                    documentId.toString(),
                    metadata.companyName(),
                    metadata.stockCode(),
                    metadata.sourceGroup().getValue(),
                    metadata.reportName(),
                    metadata.receiptDate(),
                    first.sectionPath(),
                    content,
                    1.0
            );
        }
    }
}
