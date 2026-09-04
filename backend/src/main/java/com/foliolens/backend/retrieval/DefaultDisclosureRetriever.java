package com.foliolens.backend.retrieval;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.infrastructure.search.CorrectionFilter;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchHit;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchResult;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSourceReference;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchHit;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchResult;
import com.foliolens.backend.disclosure.service.DisclosureChunkSearchService;
import com.foliolens.backend.disclosure.service.DisclosureFactLookupResult;
import com.foliolens.backend.disclosure.service.DisclosureFactLookupService;
import com.foliolens.backend.disclosure.service.DisclosureMetadataSearchService;
import com.foliolens.backend.question.plan.ToolType;
import com.foliolens.backend.question.plan.confirmation.PlanStep;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.question.plan.toolinput.LookupFactsInput;
import com.foliolens.backend.question.plan.toolinput.SearchDisclosuresInput;
import com.foliolens.backend.question.plan.toolinput.SearchEvidenceInput;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 검증된 QuestionPlan의 SEARCH_DISCLOSURES, LOOKUP_FACTS,
 * SEARCH_EVIDENCE step을 실제 PostgreSQL 검색 서비스에 연결한다.
 *
 * RESOLVE_DISCLOSURE_HISTORY와 CALCULATE는 이 Retriever의 책임이
 * 아니므로 실행했다고 표시하지 않고 경고로 남긴다.
 */
@Component
@Profile("!fake-retrieval")
public class DefaultDisclosureRetriever implements DisclosureRetriever {

    public static final String RETRIEVAL_VERSION =
            "question-plan-search-v2";

    private final DisclosureMetadataSearchService metadataSearchService;
    private final DisclosureChunkSearchService chunkSearchService;
    private final DisclosureFactLookupService factLookupService;
    private final DisclosureFactRetrievalMapper factRetrievalMapper;

    public DefaultDisclosureRetriever(
            DisclosureMetadataSearchService metadataSearchService,
            DisclosureChunkSearchService chunkSearchService,
            DisclosureFactLookupService factLookupService,
            DisclosureFactRetrievalMapper factRetrievalMapper
    ) {
        this.metadataSearchService = Objects.requireNonNull(
                metadataSearchService,
                "metadataSearchService는 필수입니다."
        );
        this.chunkSearchService = Objects.requireNonNull(
                chunkSearchService,
                "chunkSearchService는 필수입니다."
        );
        this.factLookupService = Objects.requireNonNull(
                factLookupService,
                "factLookupService는 필수입니다."
        );
        this.factRetrievalMapper = Objects.requireNonNull(
                factRetrievalMapper,
                "factRetrievalMapper는 필수입니다."
        );
    }

    @Override
    public RetrievalResult retrieve(QuestionPlan plan) {
        Objects.requireNonNull(plan, "plan은 필수입니다.");
        Objects.requireNonNull(plan.time(), "plan.time은 필수입니다.");

        ExecutionContext context = new ExecutionContext();

        for (PlanStep step : plan.steps()) {
            switch (step.toolType()) {
                case SEARCH_DISCLOSURES -> executeSearchDisclosures(
                        plan,
                        step,
                        context
                );
                case LOOKUP_FACTS -> executeLookupFacts(step, context);
                case SEARCH_EVIDENCE -> executeSearchEvidence(step, context);
                default -> context.warnings.add(
                        "[" + step.stepId() + "] " + step.toolType()
                                + " 도구는 실제 검색 Retriever의 현재 "
                                + "구현 범위가 아니어서 실행하지 않았습니다."
                );
            }
        }

        return context.toResult();
    }

    private void executeLookupFacts(
            PlanStep step,
            ExecutionContext context
    ) {
        LookupFactsInput input = requireInput(step, LookupFactsInput.class);
        DisclosureMetadataSearchResult source =
                context.disclosureResults.get(input.disclosureIdsFrom());

        if (source == null) {
            throw new IllegalArgumentException(
                    "[" + step.stepId() + "] disclosureIdsFrom이 실행된 "
                            + "SEARCH_DISCLOSURES step을 가리키지 않습니다: "
                            + input.disclosureIdsFrom()
            );
        }

        Set<UUID> disclosureIds = source.items().stream()
                .map(DisclosureMetadataSearchHit::disclosureId)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new
                ));
        DisclosureFactLookupResult result = factLookupService.lookup(
                disclosureIds,
                input.factKeys()
        );

        result.facts().stream()
                .map(factRetrievalMapper::toRetrievedFact)
                .forEach(fact -> context.verifiedFacts.putIfAbsent(
                        fact.factId(),
                        fact
                ));
        result.evidences().stream()
                .map(factRetrievalMapper::toRetrievedEvidence)
                .forEach(evidence -> context.verifiedEvidences.putIfAbsent(
                        evidence.evidenceId(),
                        evidence
                ));

        Map<UUID, DisclosureMetadataSearchHit> sourceMetadata =
                source.items().stream().collect(
                        java.util.stream.Collectors.toMap(
                                DisclosureMetadataSearchHit::disclosureId,
                                item -> item,
                                (left, right) -> left,
                                LinkedHashMap::new
                        )
                );
        factRetrievalMapper.toRetrievedDocuments(
                        result.evidences(),
                        sourceMetadata
                ).forEach(document -> context.verifiedDocuments.putIfAbsent(
                        document.documentId(),
                        document
                ));

        context.missingFactKeys.addAll(result.missingFactKeys());
        context.executedSteps.add(step);
        if (!result.missingFactKeys().isEmpty()) {
            context.warnings.add(
                    "[" + step.stepId() + "] VERIFIED Fact를 찾지 못한 key: "
                            + String.join(", ", result.missingFactKeys())
            );
        }
    }

    private void executeSearchDisclosures(
            QuestionPlan plan,
            PlanStep step,
            ExecutionContext context
    ) {
        SearchDisclosuresInput input = requireInput(
                step,
                SearchDisclosuresInput.class
        );

        DisclosureMetadataSearchCondition condition =
                new DisclosureMetadataSearchCondition(
                        plan.companies().stream()
                                .map(company -> company.companyId())
                                .collect(java.util.stream.Collectors.toSet()),
                        plan.time().receiptPeriod().from(),
                        plan.time().receiptPeriod().to(),
                        plan.time().asOf(),
                        Set.of(),
                        Set.copyOf(input.categories()),
                        Set.copyOf(input.subtypes()),
                        input.titleTerms(),
                        CorrectionFilter.ALL,
                        input.limit()
                );

        DisclosureMetadataSearchResult result =
                metadataSearchService.search(condition);
        context.disclosureResults.put(step.stepId(), result);
        result.items().forEach(item -> context.metadataByDisclosureId
                .putIfAbsent(item.disclosureId(), item));
        context.executedSteps.add(step);
        context.metadataCandidateCount += result.candidateCount();
        context.metadataTruncated |= result.truncated();
        context.addWarnings(step.stepId(), result.warnings());
        context.warnings.add(
                "[" + step.stepId() + "] 현재 메타데이터 검색 스키마에는 "
                        + "보고기간 필드가 없어 receiptPeriod와 asOf만 "
                        + "검색 조건에 적용했습니다."
        );
    }

    private void executeSearchEvidence(
            PlanStep step,
            ExecutionContext context
    ) {
        SearchEvidenceInput input = requireInput(
                step,
                SearchEvidenceInput.class
        );
        DisclosureMetadataSearchResult source =
                context.disclosureResults.get(input.disclosureIdsFrom());

        if (source == null) {
            throw new IllegalArgumentException(
                    "[" + step.stepId() + "] disclosureIdsFrom이 실행된 "
                            + "SEARCH_DISCLOSURES step을 가리키지 않습니다: "
                            + input.disclosureIdsFrom()
            );
        }

        Set<UUID> disclosureIds = source.items().stream()
                .map(DisclosureMetadataSearchHit::disclosureId)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new
                ));

        if (disclosureIds.isEmpty()) {
            context.executedSteps.add(step);
            context.warnings.add(
                    "[" + step.stepId() + "] 앞선 공시 검색 결과가 없어 "
                            + "근거 청크 검색을 실행하지 않았습니다."
            );
            return;
        }

        DisclosureChunkSearchCondition condition =
                new DisclosureChunkSearchCondition(
                        disclosureIds,
                        Set.of(),
                        Set.copyOf(input.concepts()),
                        Set.copyOf(input.factKeys()),
                        input.sectionHints(),
                        input.keywords(),
                        toChunkTypes(input.blockTypes()),
                        input.topK(),
                        0
                );

        DisclosureChunkSearchResult result = chunkSearchService.search(
                condition
        );
        context.evidenceResults.put(step.stepId(), result);
        context.executedSteps.add(step);
        context.evidenceSearchExecuted = true;
        context.evidenceCandidateCount += result.candidateChunkCount();
        context.evidenceTruncated |= result.truncated();
        context.addWarnings(step.stepId(), result.warnings());

        Map<UUID, DisclosureMetadataSearchHit> metadataByDisclosureId =
                source.items().stream().collect(java.util.stream.Collectors.toMap(
                        DisclosureMetadataSearchHit::disclosureId,
                        item -> item
                ));
        for (DisclosureChunkSearchHit hit : result.items()) {
            context.chunkHits.putIfAbsent(hit.chunkId(), hit);
            DisclosureMetadataSearchHit metadata = metadataByDisclosureId.get(
                    hit.disclosureId()
            );
            if (metadata != null) {
                context.metadataByDisclosureId.putIfAbsent(
                        metadata.disclosureId(),
                        metadata
                );
            }
        }
    }

    private Set<DisclosureChunkType> toChunkTypes(List<String> blockTypes) {
        if (blockTypes.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<DisclosureChunkType> chunkTypes = new LinkedHashSet<>();
        for (String blockType : blockTypes) {
            switch (blockType) {
                case "TABLE", "TABLE_ROW", "TABLE_CELL" ->
                        chunkTypes.add(DisclosureChunkType.TABLE);
                case "TEXT", "TITLE", "HEADING", "SECTION", "PARAGRAPH" ->
                        chunkTypes.add(DisclosureChunkType.TEXT);
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 근거 blockType입니다: " + blockType
                );
            }
        }
        return Set.copyOf(chunkTypes);
    }

    private <T> T requireInput(PlanStep step, Class<T> inputType) {
        if (!inputType.isInstance(step.input())) {
            throw new IllegalArgumentException(
                    "[" + step.stepId() + "] " + step.toolType()
                            + " step input은 " + inputType.getSimpleName()
                            + "이어야 합니다."
            );
        }
        return inputType.cast(step.input());
    }

    private static final class ExecutionContext {

        private final Map<String, DisclosureMetadataSearchResult>
                disclosureResults = new LinkedHashMap<>();
        private final Map<String, DisclosureChunkSearchResult>
                evidenceResults = new LinkedHashMap<>();
        private final Map<UUID, DisclosureChunkSearchHit> chunkHits =
                new LinkedHashMap<>();
        private final Map<UUID, DisclosureMetadataSearchHit>
                metadataByDisclosureId = new LinkedHashMap<>();
        private final Map<String, RetrievedFact> verifiedFacts =
                new LinkedHashMap<>();
        private final Map<String, RetrievedEvidence> verifiedEvidences =
                new LinkedHashMap<>();
        private final Map<String, RetrievedDocument> verifiedDocuments =
                new LinkedHashMap<>();
        private final List<PlanStep> executedSteps = new ArrayList<>();
        private final LinkedHashSet<String> warnings = new LinkedHashSet<>();
        private final LinkedHashSet<String> missingFactKeys =
                new LinkedHashSet<>();

        private int metadataCandidateCount;
        private int evidenceCandidateCount;
        private boolean metadataTruncated;
        private boolean evidenceTruncated;
        private boolean evidenceSearchExecuted;

        private void addWarnings(String stepId, List<String> values) {
            values.forEach(value -> warnings.add(
                    "[" + stepId + "] " + value
            ));
        }

        private RetrievalResult toResult() {
            LinkedHashMap<String, RetrievedEvidence> evidencesById =
                    new LinkedHashMap<>();
            chunkHits.values().stream()
                    .map(ExecutionContext::toEvidence)
                    .forEach(evidence -> evidencesById.putIfAbsent(
                            evidence.evidenceId(),
                            evidence
                    ));
            verifiedEvidences.forEach(evidencesById::put);

            LinkedHashMap<String, RetrievedDocument> documentsById =
                    new LinkedHashMap<>();
            chunkHits.values().stream()
                    .map(this::toDocument)
                    .forEach(document -> documentsById.putIfAbsent(
                            document.documentId(),
                            document
                    ));
            verifiedDocuments.forEach(documentsById::put);
            if (documentsById.isEmpty()) {
                metadataByDisclosureId.values().stream()
                        .map(ExecutionContext::toMetadataDocument)
                        .forEach(document -> documentsById.putIfAbsent(
                                document.documentId(),
                                document
                        ));
            }

            int candidateCount = evidenceSearchExecuted
                    ? evidenceCandidateCount
                    : metadataCandidateCount;
            boolean truncated = evidenceSearchExecuted
                    ? evidenceTruncated
                    : metadataTruncated;

            return new RetrievalResult(
                    List.copyOf(documentsById.values()),
                    List.copyOf(verifiedFacts.values()),
                    List.copyOf(evidencesById.values()),
                    List.of(),
                    List.copyOf(executedSteps),
                    List.copyOf(missingFactKeys),
                    new RetrievalCoverage(candidateCount, truncated),
                    List.copyOf(warnings),
                    RETRIEVAL_VERSION
            );
        }

        private RetrievedDocument toDocument(DisclosureChunkSearchHit hit) {
            DisclosureMetadataSearchHit metadata =
                    metadataByDisclosureId.get(hit.disclosureId());
            String stockCode = metadata == null
                    ? null
                    : metadata.stockCode();
            String disclosureType = metadata == null
                    ? "UNKNOWN"
                    : metadata.sourceGroup().getValue();

            return new RetrievedDocument(
                    hit.disclosureDocumentId().toString(),
                    metadata == null ? null : metadata.receiptNo(),
                    hit.companyName(),
                    stockCode,
                    disclosureType,
                    hit.reportName(),
                    hit.receiptDate(),
                    hit.sectionPath(),
                    hit.bodyText(),
                    hit.searchScore()
            );
        }

        private static RetrievedDocument toMetadataDocument(
                DisclosureMetadataSearchHit metadata
        ) {
            return new RetrievedDocument(
                    metadata.receiptNo(),
                    metadata.receiptNo(),
                    metadata.companyName(),
                    metadata.stockCode(),
                    metadata.sourceGroup().getValue(),
                    metadata.reportName(),
                    metadata.receiptDate(),
                    "",
                    "",
                    metadata.searchScore()
            );
        }

        private static RetrievedEvidence toEvidence(
                DisclosureChunkSearchHit hit
        ) {
            return new RetrievedEvidence(
                    hit.chunkId().toString(),
                    hit.disclosureId().toString(),
                    hit.disclosureDocumentId().toString(),
                    hit.documentFileRole(),
                    null,
                    hit.sectionPath(),
                    hit.chunkType() == DisclosureChunkType.TABLE
                            ? EvidenceBlockType.TABLE
                            : EvidenceBlockType.PARAGRAPH,
                    hit.bodyText(),
                    hit.searchScore(),
                    EvidenceStatus.CANDIDATE,
                    hit.sources().stream()
                            .map(ExecutionContext::toEvidenceSource)
                            .toList()
            );
        }

        private static RetrievedEvidenceSource toEvidenceSource(
                DisclosureChunkSourceReference source
        ) {
            return new RetrievedEvidenceSource(
                    source.chunkSourceId().toString(),
                    source.contentBlockId().toString(),
                    source.sourceOrder(),
                    source.blockSequenceNo(),
                    source.sourceLineStart(),
                    source.sourceLineEnd(),
                    source.tableNestingPath(),
                    source.tableRowIndexStart(),
                    source.tableRowIndexEnd()
            );
        }
    }
}
