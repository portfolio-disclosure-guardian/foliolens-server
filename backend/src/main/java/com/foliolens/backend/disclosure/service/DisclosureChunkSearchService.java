package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchResult;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchTermResolver;
import com.foliolens.backend.disclosure.infrastructure.search.ResolvedChunkSearchTerms;
import com.foliolens.backend.disclosure.repository.DisclosureChunkSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 선택된 공시 안에서 검색어를 해석하고 관련 청크와 원본 위치 후보를
 * 조회하는 2단계 검색 서비스.
 */
@Service
@Transactional(readOnly = true)
public class DisclosureChunkSearchService {

    public static final String RETRIEVAL_VERSION = "chunk-search-v1";

    private final DisclosureChunkSearchTermResolver termResolver;
    private final DisclosureChunkSearchRepository searchRepository;

    public DisclosureChunkSearchService(
            DisclosureChunkSearchTermResolver termResolver,
            DisclosureChunkSearchRepository searchRepository
    ) {
        this.termResolver = Objects.requireNonNull(
                termResolver,
                "termResolver는 필수입니다."
        );
        this.searchRepository = Objects.requireNonNull(
                searchRepository,
                "searchRepository는 필수입니다."
        );
    }

    public DisclosureChunkSearchResult search(
            DisclosureChunkSearchCondition condition
    ) {
        Objects.requireNonNull(condition, "condition은 필수입니다.");
        validateNeighborPolicy(condition);

        ResolvedChunkSearchTerms terms = termResolver.resolve(condition);
        DisclosureChunkSearchRepository.SearchResult repositoryResult =
                searchRepository.search(
                        condition,
                        terms,
                        RETRIEVAL_VERSION
                );

        boolean truncated = repositoryResult.candidateChunkCount()
                > repositoryResult.items().size();

        return new DisclosureChunkSearchResult(
                repositoryResult.items(),
                condition.disclosureIds(),
                repositoryResult.searchedDocumentCount(),
                repositoryResult.candidateChunkCount(),
                truncated,
                mergeWarnings(
                        terms,
                        repositoryResult.warnings(),
                        truncated
                ),
                RETRIEVAL_VERSION
        );
    }

    private void validateNeighborPolicy(
            DisclosureChunkSearchCondition condition
    ) {
        if (condition.neighborRadius() != 0) {
            throw new IllegalArgumentException(
                    "chunk-search-v1은 neighborRadius=0만 지원합니다. "
                            + "이웃 청크 문맥 모델을 추가한 뒤 확장해야 합니다."
            );
        }
    }

    private List<String> mergeWarnings(
            ResolvedChunkSearchTerms terms,
            List<String> repositoryWarnings,
            boolean truncated
    ) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        warnings.addAll(terms.warnings());
        warnings.addAll(repositoryWarnings);

        if (!terms.hasExecutableTerms()) {
            warnings.add(
                    "해석 가능한 검색어 또는 Section 힌트가 없어 "
                            + "청크 검색을 실행하지 않았습니다."
            );
        }

        if (truncated) {
            warnings.add(
                    "topK 상한으로 인해 일부 청크 후보가 결과에서 제외됐습니다."
            );
        }

        return List.copyOf(new ArrayList<>(warnings));
    }
}
