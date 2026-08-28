package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import com.foliolens.backend.disclosure.infrastructure.search.CorrectionFilter;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchHit;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchResult;
import com.foliolens.backend.disclosure.repository.DisclosureMetadataSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureMetadataSearchServiceTest {

    private DisclosureMetadataSearchRepository repository;
    private DisclosureMetadataSearchService service;

    @BeforeEach
    void setUp() {
        repository = mock(DisclosureMetadataSearchRepository.class);
        service = new DisclosureMetadataSearchService(repository);
    }

    @Test
    void returnsRankedRepositoryResultsWithRetrievalMetadata() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(UUID.randomUUID()),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                1
        );
        DisclosureMetadataSearchHit hit = hit();

        when(repository.search(condition)).thenReturn(
                new DisclosureMetadataSearchRepository.SearchResult(
                        List.of(hit),
                        3
                )
        );

        DisclosureMetadataSearchResult result = service.search(condition);

        assertThat(result.items()).containsExactly(hit);
        assertThat(result.candidateCount()).isEqualTo(3);
        assertThat(result.truncated()).isTrue();
        assertThat(result.retrievalVersion()).isEqualTo(
                DisclosureMetadataSearchService.RETRIEVAL_VERSION
        );
        assertThat(result.warnings()).containsExactly(
                "검색 상한으로 인해 일부 공시 후보가 결과에서 제외됐습니다."
        );
        verify(repository).search(condition);
    }

    @Test
    void warnsWhenCompanyAndDateFiltersAreMissing() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(),
                null,
                null,
                20
        );

        when(repository.search(condition)).thenReturn(
                new DisclosureMetadataSearchRepository.SearchResult(
                        List.of(),
                        0
                )
        );

        DisclosureMetadataSearchResult result = service.search(condition);

        assertThat(result.items()).isEmpty();
        assertThat(result.truncated()).isFalse();
        assertThat(result.warnings()).containsExactly(
                "기업이 지정되지 않아 전체 기업을 대상으로 검색했습니다.",
                "접수 기간이 지정되지 않아 전체 기간을 대상으로 검색했습니다."
        );
    }

    @Test
    void rejectsNullConditionBeforeCallingRepository() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.search(null))
                .withMessage("condition은 필수입니다.");
    }

    private DisclosureMetadataSearchCondition condition(
            Set<UUID> companyIds,
            LocalDate dateFrom,
            LocalDate dateTo,
            int limit
    ) {
        return new DisclosureMetadataSearchCondition(
                companyIds,
                dateFrom,
                dateTo,
                null,
                Set.of(DisclosureSourceGroup.MAJOR),
                Set.of(DisclosureCategory.MATERIAL),
                Set.of("신규시설투자등"),
                List.of("시설투자"),
                CorrectionFilter.ALL,
                limit
        );
    }

    private DisclosureMetadataSearchHit hit() {
        return new DisclosureMetadataSearchHit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "삼성전자",
                "005930",
                "20250410000123",
                LocalDate.of(2025, 4, 10),
                "신규시설투자등",
                DisclosureSourceGroup.MAJOR,
                DisclosureCategory.MATERIAL,
                "신규시설투자등",
                false,
                SourceProvider.CONTEST,
                2,
                1.0,
                List.of("시설투자")
        );
    }
}
