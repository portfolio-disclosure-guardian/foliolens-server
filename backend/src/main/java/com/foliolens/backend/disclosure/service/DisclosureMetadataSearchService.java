package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchResult;
import com.foliolens.backend.disclosure.repository.DisclosureMetadataSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 공시 본문 검색 전에 검색 대상 공시를 기업·기간·유형·보고서명으로
 * 제한하는 1단계 검색 서비스.
 */
@Service
@Transactional(readOnly = true)
public class DisclosureMetadataSearchService {

    public static final String RETRIEVAL_VERSION = "metadata-search-v1";

    private final DisclosureMetadataSearchRepository searchRepository;

    public DisclosureMetadataSearchService(
            DisclosureMetadataSearchRepository searchRepository
    ) {
        this.searchRepository = Objects.requireNonNull(
                searchRepository,
                "searchRepository는 필수입니다."
        );
    }

    public DisclosureMetadataSearchResult search(
            DisclosureMetadataSearchCondition condition
    ) {
        Objects.requireNonNull(condition, "condition은 필수입니다.");

        DisclosureMetadataSearchRepository.SearchResult repositoryResult =
                searchRepository.search(condition);

        boolean truncated = repositoryResult.candidateCount()
                > repositoryResult.items().size();

        return new DisclosureMetadataSearchResult(
                repositoryResult.items(),
                repositoryResult.candidateCount(),
                truncated,
                warnings(condition, truncated),
                RETRIEVAL_VERSION
        );
    }

    private List<String> warnings(
            DisclosureMetadataSearchCondition condition,
            boolean truncated
    ) {
        List<String> warnings = new ArrayList<>();

        if (condition.companyIds().isEmpty()) {
            warnings.add(
                    "기업이 지정되지 않아 전체 기업을 대상으로 검색했습니다."
            );
        }

        if (condition.receiptDateFrom() == null
                && condition.receiptDateTo() == null
                && condition.asOf() == null) {
            warnings.add(
                    "접수 기간이 지정되지 않아 전체 기간을 대상으로 검색했습니다."
            );
        }

        if (truncated) {
            warnings.add(
                    "검색 상한으로 인해 일부 공시 후보가 결과에서 제외됐습니다."
            );
        }

        return List.copyOf(warnings);
    }
}
