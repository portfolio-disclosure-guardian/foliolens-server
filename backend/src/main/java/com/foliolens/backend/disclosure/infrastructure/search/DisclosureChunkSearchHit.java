package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 질문과 관련 있다고 검색된 TEXT 또는 TABLE 청크 한 건.
 */
public record DisclosureChunkSearchHit(
        UUID chunkId,
        UUID disclosureId,
        UUID disclosureDocumentId,
        UUID companyId,
        String companyName,
        String receiptNo,
        LocalDate receiptDate,
        String reportName,
        boolean correction, // 해당 청크가 정정공시에서 나온 것인지 나타냄
        String documentName, // 실제 원문 파일의 문서명
        DisclosureDocumentRole documentFileRole, // 한 공시 안에서 파일의 물리적 역할
        EventDocumentRole eventDocumentRole, // 여러 공시를 하나의 사건으로 연결했을 때 해당 공시의 의미
        DisclosureChunkType chunkType,
        int chunkSequenceNo, // 한 원문 문서 안에서 청크가 등장하는 순서
        String sectionPath, // 청크가 속한 전체 장·절 경로 -> II. 사업의 내용 > 신규시설투자 > 투자기간

        // disclosure_chunks에 저장된 값 그대로 사용
        String bodyText, // LLM이나 Fact 추출기에 실제 근거 후보로 전달할 본문
        String searchText, // 검색 품질을 높이기 위해 문맥을 추가한 문자열

        double searchScore, // 검색 순위 점수
        SearchScoreBreakdown scoreBreakdown, // 검색 점수 세부 구성
        List<String> matchedTerms, // 실제로 일치한 검색어
        List<DisclosureChunkSourceReference> sources,
        String generatorVersion,
        String retrievalVersion
) {

    private static final double SCORE_TOLERANCE = 1.0e-9;

    public DisclosureChunkSearchHit {
        chunkId = requireId(chunkId, "chunkId");
        disclosureId = requireId(disclosureId, "disclosureId");
        disclosureDocumentId = requireId(
                disclosureDocumentId,
                "disclosureDocumentId"
        );
        companyId = requireId(companyId, "companyId");
        companyName = requireText(companyName, "companyName");
        receiptNo = requireReceiptNo(receiptNo);
        receiptDate = Objects.requireNonNull(
                receiptDate,
                "receiptDate는 필수입니다."
        );
        reportName = requireText(reportName, "reportName");
        documentName = requireText(documentName, "documentName");
        documentFileRole = Objects.requireNonNull(
                documentFileRole,
                "documentFileRole은 필수입니다."
        );
        chunkType = Objects.requireNonNull(
                chunkType,
                "chunkType은 필수입니다."
        );

        if (chunkType == DisclosureChunkType.IMAGE_CAPTION) {
            throw new IllegalArgumentException(
                    "청크 검색 적중은 TEXT 또는 TABLE이어야 합니다."
            );
        }

        if (chunkSequenceNo < 1) {
            throw new IllegalArgumentException(
                    "chunkSequenceNo는 1 이상이어야 합니다."
            );
        }

        sectionPath = normalizePath(sectionPath);
        bodyText = requireText(bodyText, "bodyText");
        searchText = requireText(searchText, "searchText");

        if (!Double.isFinite(searchScore)) {
            throw new IllegalArgumentException(
                    "searchScore는 유한한 숫자여야 합니다."
            );
        }

        if (scoreBreakdown != null
                && Math.abs(scoreBreakdown.finalScore() - searchScore)
                > SCORE_TOLERANCE) {
            throw new IllegalArgumentException(
                    "scoreBreakdown.finalScore가 searchScore와 다릅니다."
            );
        }

        matchedTerms = immutableTextList(matchedTerms, "matchedTerms");
        sources = immutableSources(sources);
        validateSourceOrder(sources);

        generatorVersion = requireText(
                generatorVersion,
                "generatorVersion"
        );
        retrievalVersion = requireText(
                retrievalVersion,
                "retrievalVersion"
        );
    }

    private static UUID requireId(UUID value, String fieldName) {
        return Objects.requireNonNull(
                value,
                fieldName + "는 필수입니다."
        );
    }

    private static String requireReceiptNo(String value) {
        String normalized = requireText(value, "receiptNo");

        if (!normalized.matches("^[0-9]{14}$")) {
            throw new IllegalArgumentException(
                    "receiptNo는 14자리 숫자 문자열이어야 합니다."
            );
        }

        return normalized;
    }

    private static String normalizePath(String value) {
        return Objects.requireNonNull(
                value,
                "sectionPath는 null일 수 없습니다."
        ).strip();
    }

    private static List<String> immutableTextList(
            List<String> values,
            String fieldName
    ) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .toList();
    }

    private static List<DisclosureChunkSourceReference> immutableSources(
            List<DisclosureChunkSourceReference> values
    ) {
        if (values == null) {
            throw new IllegalArgumentException(
                    "sources는 필수입니다."
            );
        }

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "sources에는 null 원소가 포함될 수 없습니다."
            );
        }

        List<DisclosureChunkSourceReference> copied = List.copyOf(values);
        if (copied.isEmpty()) {
            throw new IllegalArgumentException(
                    "검색된 청크는 원본 출처를 하나 이상 가져야 합니다."
            );
        }

        return copied;
    }

    private static void validateSourceOrder(
            List<DisclosureChunkSourceReference> sources
    ) {
        Set<UUID> sourceIds = new HashSet<>();

        for (int index = 0; index < sources.size(); index++) {
            DisclosureChunkSourceReference source = sources.get(index);
            int expectedOrder = index + 1;

            if (source.sourceOrder() != expectedOrder) {
                throw new IllegalArgumentException(
                        "sources의 sourceOrder는 1부터 연속되어야 합니다."
                );
            }

            if (!sourceIds.add(source.chunkSourceId())) {
                throw new IllegalArgumentException(
                        "sources에 중복된 chunkSourceId가 있습니다."
                );
            }
        }
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }

        return value.strip();
    }
}
