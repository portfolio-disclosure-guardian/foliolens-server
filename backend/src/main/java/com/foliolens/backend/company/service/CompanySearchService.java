package com.foliolens.backend.company.service;

import com.foliolens.backend.common.exception.CustomException;
import com.foliolens.backend.common.exception.ErrorCode;
import com.foliolens.backend.company.dto.response.CompanySearchResponse;
import com.foliolens.backend.company.entity.Company;
import com.foliolens.backend.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanySearchService {

    private static final int MIN_QUERY_LENGTH = 1;
    private static final int MAX_QUERY_LENGTH = 100;

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyRepository companyRepository;

    public CompanySearchResponse search(
            String query,
            boolean listedOnly, // 상장 회사만 검사인지
            int page,
            int size
    ) {
        String normalizedQuery = normalizeAndValidateQuery(query);

        validatePage(page, size);

        Pageable pageable = PageRequest.of(page, size);

        Page<Company> companyPage = companyRepository.search(
                        normalizedQuery,
                        listedOnly,
                        pageable
                );

        return CompanySearchResponse.from(companyPage, normalizedQuery);
    }


    // ================== 검증 로직 ========================
    // 검색어 정규화 및 쿼리 검증 로직
    private String normalizeAndValidateQuery(String query) {
        if (query == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 공백 제거
        String normalizedQuery = query.trim();

        int queryLength = normalizedQuery.length();

        if (queryLength < MIN_QUERY_LENGTH || queryLength > MAX_QUERY_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        return normalizedQuery;
    }

    // 페이지 검증
    // page: 0 이상
    // size: 1 이상 100 이하
    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
