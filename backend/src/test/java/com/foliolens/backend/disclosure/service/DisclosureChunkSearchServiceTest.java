package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchHit;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchResult;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchTermResolver;
import com.foliolens.backend.disclosure.infrastructure.search.ResolvedChunkSearchTerms;
import com.foliolens.backend.disclosure.repository.DisclosureChunkSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureChunkSearchServiceTest {

    private static final UUID DISCLOSURE_ID = new UUID(401, 1);

    private DisclosureChunkSearchTermResolver resolver;
    private DisclosureChunkSearchRepository repository;
    private DisclosureChunkSearchService service;

    @BeforeEach
    void setUp() {
        resolver = mock(DisclosureChunkSearchTermResolver.class);
        repository = mock(DisclosureChunkSearchRepository.class);
        service = new DisclosureChunkSearchService(resolver, repository);
    }

    @Test
    void resolvesTermsSearchesRepositoryAndBuildsVersionedResult() {
        DisclosureChunkSearchCondition condition = condition(0);
        ResolvedChunkSearchTerms terms = terms(List.of());
        DisclosureChunkSearchHit hit = mock(DisclosureChunkSearchHit.class);

        when(hit.disclosureId()).thenReturn(DISCLOSURE_ID);
        when(hit.disclosureDocumentId()).thenReturn(new UUID(402, 1));
        when(hit.chunkId()).thenReturn(new UUID(403, 1));
        when(hit.retrievalVersion()).thenReturn(
                DisclosureChunkSearchService.RETRIEVAL_VERSION
        );
        when(resolver.resolve(condition)).thenReturn(terms);
        when(repository.search(
                condition,
                terms,
                DisclosureChunkSearchService.RETRIEVAL_VERSION
        )).thenReturn(
                new DisclosureChunkSearchRepository.SearchResult(
                        List.of(hit),
                        2,
                        5,
                        List.of("청킹이 완료되지 않은 원문 문서가 1개 있습니다.")
                )
        );

        DisclosureChunkSearchResult result = service.search(condition);

        assertThat(result.items()).containsExactly(hit);
        assertThat(result.searchedDisclosureIds())
                .containsExactly(DISCLOSURE_ID);
        assertThat(result.searchedDocumentCount()).isEqualTo(2);
        assertThat(result.candidateChunkCount()).isEqualTo(5);
        assertThat(result.truncated()).isTrue();
        assertThat(result.retrievalVersion()).isEqualTo(
                DisclosureChunkSearchService.RETRIEVAL_VERSION
        );
        assertThat(result.warnings()).containsExactly(
                "청킹이 완료되지 않은 원문 문서가 1개 있습니다.",
                "topK 상한으로 인해 일부 청크 후보가 결과에서 제외됐습니다."
        );
    }

    @Test
    void returnsWarningsWhenNoLogicalInputCanBeResolved() {
        DisclosureChunkSearchCondition condition = condition(0);
        ResolvedChunkSearchTerms terms = terms(
                List.of("지원하지 않는 factKey입니다: facility.unknown")
        );
        terms = new ResolvedChunkSearchTerms(
                List.of(),
                List.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("facility.unknown"),
                terms.warnings()
        );

        when(resolver.resolve(condition)).thenReturn(terms);
        when(repository.search(
                condition,
                terms,
                DisclosureChunkSearchService.RETRIEVAL_VERSION
        )).thenReturn(
                new DisclosureChunkSearchRepository.SearchResult(
                        List.of(),
                        1,
                        0,
                        List.of()
                )
        );

        DisclosureChunkSearchResult result = service.search(condition);

        assertThat(result.items()).isEmpty();
        assertThat(result.warnings()).containsExactly(
                "지원하지 않는 factKey입니다: facility.unknown",
                "해석 가능한 검색어 또는 Section 힌트가 없어 "
                        + "청크 검색을 실행하지 않았습니다."
        );
    }

    @Test
    void rejectsUnsupportedNeighborExpansionBeforeDependenciesAreCalled() {
        DisclosureChunkSearchCondition condition = condition(1);

        assertThatThrownBy(() -> service.search(condition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neighborRadius=0");

        verify(resolver, never()).resolve(condition);
    }

    private DisclosureChunkSearchCondition condition(int neighborRadius) {
        return new DisclosureChunkSearchCondition(
                Set.of(DISCLOSURE_ID),
                Set.of(),
                Set.of(),
                Set.of("facility.amount"),
                List.of(),
                List.of(),
                Set.of(
                        DisclosureChunkType.TEXT,
                        DisclosureChunkType.TABLE
                ),
                10,
                neighborRadius
        );
    }

    private ResolvedChunkSearchTerms terms(List<String> warnings) {
        return new ResolvedChunkSearchTerms(
                List.of("투자금액"),
                List.of("신규시설투자"),
                Set.of("투자금액"),
                Set.of(),
                Set.of("facility.amount"),
                Set.of(),
                Set.of(),
                warnings
        );
    }
}
