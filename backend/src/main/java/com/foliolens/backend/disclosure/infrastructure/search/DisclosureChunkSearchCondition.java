package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 메타데이터 검색으로 선택된 공시 안에서 TEXT·TABLE 청크를 찾기 위한 검색 조건
 * “어느 공시 안에서 어떤 내용을 몇 개까지 검색할 것인가?”
 */
public record DisclosureChunkSearchCondition(
        Set<UUID> disclosureIds, // 메타데이터 검색에서 선택된 공시 ID
        Set<UUID> documentIds, // 공시 안에서도 특정 원문 파일만 검색하고 싶을 때 사용

        // todo: 현재 Fact 저장이 완성되기 전에는 개념에 연결된 동의어·Section·행 레이블을 선택하는 검색 힌트로 사용
        // 예:
        // CAPACITY_EXPANSION
        // INVESTMENT_SCALE
        // COMPLETION_SCHEDULE
        // CONTRACT_TERMINATION
        Set<String> concepts, // 질문의 의미를 나타내는 상위 개념

        // 정확히 어떤 Fact를 찾고 싶은지 나타내는 식별자
        // todo: 아직 Fact 테이블에 값이 적재되지 않은 단계에서는 다음 검색어를 선택하는 데 사용할 수 있
        // facility.amount
        // → 투자금액, 투자예정금액, 투자규모
        //
        // facility.purpose
        // → 투자목적, 투자 사유, 기대효과
        Set<String> factKeys,

        List<String> sectionHints, // 관련 내용이 있을 것으로 예상되는 장·절·표 제목 -> 관련도 점수를 높이는 힌트로 사용
        List<String> keywords, // bodyText나 searchText에서 실제로 찾을 문자열
        Set<DisclosureChunkType> chunkTypes, // 검색할 청크의 종류
        int topK, // 검색 점수가 높은 청크를 최대 몇 개까지 반환할지 지정
        int neighborRadius // 검색된 청크의 앞뒤 청크를 얼마나 함께 살펴볼지 나타냄
) {

    public static final int MIN_TOP_K = 1;
    public static final int MAX_TOP_K = 20;
    public static final int MIN_NEIGHBOR_RADIUS = 0;
    public static final int MAX_NEIGHBOR_RADIUS = 2;

    public DisclosureChunkSearchCondition {
        disclosureIds = immutableSet(disclosureIds, "disclosureIds");
        documentIds = immutableSet(documentIds, "documentIds");
        concepts = immutableTextSet(concepts, "concepts");
        factKeys = immutableTextSet(factKeys, "factKeys");
        sectionHints = immutableTextList(sectionHints, "sectionHints");
        keywords = immutableTextList(keywords, "keywords");
        chunkTypes = immutableSet(chunkTypes, "chunkTypes");

        if (disclosureIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "disclosureIds는 하나 이상이어야 합니다."
            );
        }

        if (concepts.isEmpty()
                && factKeys.isEmpty()
                && sectionHints.isEmpty()
                && keywords.isEmpty()) {
            throw new IllegalArgumentException(
                    "concepts, factKeys, sectionHints, keywords 중 "
                            + "하나 이상의 검색 신호가 필요합니다."
            );
        }

        if (chunkTypes.contains(DisclosureChunkType.IMAGE_CAPTION)) {
            throw new IllegalArgumentException(
                    "청크 검색은 TEXT와 TABLE 유형만 지원합니다."
            );
        }

        if (topK < MIN_TOP_K || topK > MAX_TOP_K) {
            throw new IllegalArgumentException(
                    "topK는 " + MIN_TOP_K + " 이상 "
                            + MAX_TOP_K + " 이하여야 합니다."
            );
        }

        if (neighborRadius < MIN_NEIGHBOR_RADIUS
                || neighborRadius > MAX_NEIGHBOR_RADIUS) {
            throw new IllegalArgumentException(
                    "neighborRadius는 " + MIN_NEIGHBOR_RADIUS + " 이상 "
                            + MAX_NEIGHBOR_RADIUS + " 이하여야 합니다."
            );
        }
    }

    /**
     * chunkTypes가 비어 있으면 현재 검색 가능한 TEXT와 TABLE을 모두
     * 대상으로 한다.
     */
    public Set<DisclosureChunkType> effectiveChunkTypes() {
        if (chunkTypes.isEmpty()) {
            return Set.of(
                    DisclosureChunkType.TEXT,
                    DisclosureChunkType.TABLE
            );
        }

        return chunkTypes;
    }

    private static <T> Set<T> immutableSet(
            Set<T> values,
            String fieldName
    ) {
        if (values == null) {
            return Set.of();
        }

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    fieldName + "에는 null 원소가 포함될 수 없습니다."
            );
        }

        return Set.copyOf(values);
    }

    private static Set<String> immutableTextSet(
            Set<String> values,
            String fieldName
    ) {
        if (values == null) {
            return Set.of();
        }

        return values.stream()
                .map(value -> requireText(value, fieldName))
                .collect(Collectors.toUnmodifiableSet());
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

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "에는 빈 문자열이 포함될 수 없습니다."
            );
        }

        return value.strip();
    }
}
