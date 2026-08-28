package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisclosureChunkSearchModelTest {

    private static final UUID DISCLOSURE_ID = new UUID(301, 1);
    private static final UUID DOCUMENT_ID = new UUID(302, 1);
    private static final UUID CHUNK_ID = new UUID(303, 1);

    @Test
    void conditionNormalizesSignalsAndUsesDefaultChunkTypes() {
        Set<UUID> disclosureIds = new HashSet<>(Set.of(DISCLOSURE_ID));
        List<String> keywords = new ArrayList<>(
                List.of("  투자금액  ", "투자금액")
        );

        DisclosureChunkSearchCondition condition =
                new DisclosureChunkSearchCondition(
                        disclosureIds,
                        null,
                        null,
                        Set.of(" facility.amount "),
                        null,
                        keywords,
                        null,
                        10,
                        1
                );

        disclosureIds.clear();
        keywords.clear();

        assertThat(condition.disclosureIds())
                .containsExactly(DISCLOSURE_ID);
        assertThat(condition.factKeys())
                .containsExactly("facility.amount");
        assertThat(condition.keywords())
                .containsExactly("투자금액");
        assertThat(condition.effectiveChunkTypes())
                .containsExactlyInAnyOrder(
                        DisclosureChunkType.TEXT,
                        DisclosureChunkType.TABLE
                );
        assertThatThrownBy(() -> condition.keywords().add("증설"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void conditionRejectsMissingScopeSignalAndUnsupportedLimits() {
        assertThatThrownBy(() -> condition(
                Set.of(),
                List.of("투자금액"),
                Set.of(DisclosureChunkType.TEXT),
                10,
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disclosureIds");

        assertThatThrownBy(() -> condition(
                Set.of(DISCLOSURE_ID),
                List.of(),
                Set.of(DisclosureChunkType.TEXT),
                10,
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("검색 신호");

        assertThatThrownBy(() -> condition(
                Set.of(DISCLOSURE_ID),
                List.of("투자금액"),
                Set.of(DisclosureChunkType.IMAGE_CAPTION),
                10,
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXT와 TABLE");

        assertThatThrownBy(() -> condition(
                Set.of(DISCLOSURE_ID),
                List.of("투자금액"),
                Set.of(DisclosureChunkType.TEXT),
                21,
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");

        assertThatThrownBy(() -> condition(
                Set.of(DISCLOSURE_ID),
                List.of("투자금액"),
                Set.of(DisclosureChunkType.TEXT),
                10,
                3
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neighborRadius");
    }

    @Test
    void sourceReferencePreservesOriginalAndTableLocation() {
        DisclosureChunkSourceReference source = source(
                1,
                10,
                12,
                "  $.rows[0].cells[1].nestedTables[0]  ",
                2,
                4
        );

        assertThat(source.tableNestingPath())
                .isEqualTo("$.rows[0].cells[1].nestedTables[0]");
        assertThat(source.tableRowIndexStart()).isEqualTo(2);
        assertThat(source.tableRowIndexEnd()).isEqualTo(4);
    }

    @Test
    void sourceReferenceRejectsInvalidLineAndTableRanges() {
        assertThatThrownBy(() -> source(
                1,
                20,
                10,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("원문 종료 행");

        assertThatThrownBy(() -> source(
                1,
                10,
                20,
                "$.nestedTables[0]",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("표 행 범위");

        assertThatThrownBy(() -> source(
                1,
                10,
                20,
                null,
                3,
                2
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("표 행 범위");
    }

    @Test
    void scoreBreakdownProvidesReproducibleComponentTotal() {
        SearchScoreBreakdown breakdown = scoreBreakdown(7.5);

        assertThat(breakdown.componentTotal()).isEqualTo(7.5);

        assertThatThrownBy(() -> new SearchScoreBreakdown(
                Double.NaN,
                0,
                0,
                0,
                0,
                0,
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportNameScore");
    }

    @Test
    void hitPreservesVerifiedSearchAndSourceInformation() {
        DisclosureChunkSearchHit hit = hit(
                DISCLOSURE_ID,
                DOCUMENT_ID,
                CHUNK_ID,
                "chunk-search-v1",
                7.5,
                scoreBreakdown(7.5),
                List.of(source(1, 10, 12, null, null, null))
        );

        assertThat(hit.companyName()).isEqualTo("테스트전자");
        assertThat(hit.sectionPath()).isEqualTo("II. 사업의 내용 > 신규시설투자");
        assertThat(hit.matchedTerms()).containsExactly("투자금액");
        assertThat(hit.sources()).hasSize(1);
        assertThat(hit.eventDocumentRole())
                .isEqualTo(EventDocumentRole.ORIGINAL);
    }

    @Test
    void hitRejectsScoreMismatchAndNonSequentialSources() {
        assertThatThrownBy(() -> hit(
                DISCLOSURE_ID,
                DOCUMENT_ID,
                CHUNK_ID,
                "chunk-search-v1",
                8.0,
                scoreBreakdown(7.5),
                List.of(source(1, 10, 12, null, null, null))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finalScore");

        assertThatThrownBy(() -> hit(
                DISCLOSURE_ID,
                DOCUMENT_ID,
                CHUNK_ID,
                "chunk-search-v1",
                1.0,
                null,
                List.of(source(2, 10, 12, null, null, null))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceOrder");
    }

    @Test
    void resultChecksScopeCountsVersionAndEmptyResult() {
        DisclosureChunkSearchHit hit = hit(
                DISCLOSURE_ID,
                DOCUMENT_ID,
                CHUNK_ID,
                "chunk-search-v1",
                1.0,
                null,
                List.of(source(1, 10, 12, null, null, null))
        );

        DisclosureChunkSearchResult result =
                new DisclosureChunkSearchResult(
                        List.of(hit),
                        Set.of(DISCLOSURE_ID),
                        2,
                        5,
                        true,
                        List.of("  일부 문서는 청킹이 완료되지 않았습니다.  "),
                        "chunk-search-v1"
                );

        assertThat(result.items()).containsExactly(hit);
        assertThat(result.warnings()).containsExactly(
                "일부 문서는 청킹이 완료되지 않았습니다."
        );

        DisclosureChunkSearchResult empty =
                DisclosureChunkSearchResult.empty(
                        Set.of(DISCLOSURE_ID),
                        0,
                        List.of(),
                        "chunk-search-v1"
                );

        assertThat(empty.items()).isEmpty();
        assertThat(empty.candidateChunkCount()).isZero();
        assertThat(empty.truncated()).isFalse();
    }

    @Test
    void resultRejectsHitOutsideScopeAndVersionMismatch() {
        DisclosureChunkSearchHit outsideScope = hit(
                new UUID(999, 1),
                DOCUMENT_ID,
                CHUNK_ID,
                "chunk-search-v1",
                1.0,
                null,
                List.of(source(1, 10, 12, null, null, null))
        );

        assertThatThrownBy(() -> new DisclosureChunkSearchResult(
                List.of(outsideScope),
                Set.of(DISCLOSURE_ID),
                1,
                1,
                false,
                List.of(),
                "chunk-search-v1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("검색 대상 공시");

        DisclosureChunkSearchHit otherVersion = hit(
                DISCLOSURE_ID,
                DOCUMENT_ID,
                CHUNK_ID,
                "chunk-search-v2",
                1.0,
                null,
                List.of(source(1, 10, 12, null, null, null))
        );

        assertThatThrownBy(() -> new DisclosureChunkSearchResult(
                List.of(otherVersion),
                Set.of(DISCLOSURE_ID),
                1,
                1,
                false,
                List.of(),
                "chunk-search-v1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retrievalVersion");
    }

    private DisclosureChunkSearchCondition condition(
            Set<UUID> disclosureIds,
            List<String> keywords,
            Set<DisclosureChunkType> chunkTypes,
            int topK,
            int neighborRadius
    ) {
        return new DisclosureChunkSearchCondition(
                disclosureIds,
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(),
                keywords,
                chunkTypes,
                topK,
                neighborRadius
        );
    }

    private DisclosureChunkSourceReference source(
            int sourceOrder,
            int sourceLineStart,
            int sourceLineEnd,
            String tableNestingPath,
            Integer tableRowIndexStart,
            Integer tableRowIndexEnd
    ) {
        return new DisclosureChunkSourceReference(
                new UUID(304, sourceOrder),
                new UUID(305, sourceOrder),
                sourceOrder,
                sourceOrder,
                sourceLineStart,
                sourceLineEnd,
                tableNestingPath,
                tableRowIndexStart,
                tableRowIndexEnd
        );
    }

    private SearchScoreBreakdown scoreBreakdown(double finalScore) {
        return new SearchScoreBreakdown(
                1.0,
                2.0,
                3.0,
                0.5,
                1.0,
                0.0,
                finalScore
        );
    }

    private DisclosureChunkSearchHit hit(
            UUID disclosureId,
            UUID documentId,
            UUID chunkId,
            String retrievalVersion,
            double searchScore,
            SearchScoreBreakdown breakdown,
            List<DisclosureChunkSourceReference> sources
    ) {
        return new DisclosureChunkSearchHit(
                chunkId,
                disclosureId,
                documentId,
                new UUID(306, 1),
                "테스트전자",
                "20250410000301",
                LocalDate.of(2025, 4, 10),
                "신규시설투자등",
                false,
                "신규시설투자 본문",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                DisclosureChunkType.TABLE,
                3,
                " II. 사업의 내용 > 신규시설투자 ",
                "투자금액은 5,000억원입니다.",
                "신규시설투자 투자금액은 5,000억원입니다.",
                searchScore,
                breakdown,
                List.of(" 투자금액 "),
                sources,
                "dart-xml-chunk-v3",
                retrievalVersion
        );
    }
}
