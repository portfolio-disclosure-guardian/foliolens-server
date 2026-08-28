package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 메타데이터 검색에서 순위가 부여된 공시 후보 한 건.
 *
 * 공시 본문의 근거 청크가 아니라, 다음 단계에서 청크를 검색할 대상 공시를 나타낸다.
 */
public record DisclosureMetadataSearchHit(
        UUID disclosureId,
        UUID companyId,
        String companyName,
        String stockCode,
        String receiptNo,
        LocalDate receiptDate,
        String reportName,
        DisclosureSourceGroup sourceGroup,
        DisclosureCategory category,
        String rawSubtype,
        boolean correction,
        SourceProvider sourceProvider,
        int documentCount,
        double searchScore,
        List<String> matchedTerms // 실제로 일치한 검색어
) {

    public DisclosureMetadataSearchHit {
        disclosureId = Objects.requireNonNull(
                disclosureId,
                "disclosureId는 필수입니다."
        );
        companyId = Objects.requireNonNull(
                companyId,
                "companyId는 필수입니다."
        );
        companyName = requireText(companyName, "companyName");
        stockCode = normalizeOptionalText(stockCode);
        receiptNo = requireText(receiptNo, "receiptNo");
        receiptDate = Objects.requireNonNull(
                receiptDate,
                "receiptDate는 필수입니다."
        );
        reportName = requireText(reportName, "reportName");
        sourceGroup = Objects.requireNonNull(
                sourceGroup,
                "sourceGroup은 필수입니다."
        );
        category = Objects.requireNonNull(
                category,
                "category는 필수입니다."
        );
        rawSubtype = normalizeOptionalText(rawSubtype);
        sourceProvider = Objects.requireNonNull(
                sourceProvider,
                "sourceProvider는 필수입니다."
        );

        if (documentCount < 0) {
            throw new IllegalArgumentException(
                    "documentCount는 0 이상이어야 합니다."
            );
        }

        if (!Double.isFinite(searchScore)) {
            throw new IllegalArgumentException(
                    "searchScore는 유한한 숫자여야 합니다."
            );
        }

        matchedTerms = immutableTextList(matchedTerms, "matchedTerms");
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
                .toList();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
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
