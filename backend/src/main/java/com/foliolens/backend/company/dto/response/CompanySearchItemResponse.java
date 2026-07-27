package com.foliolens.backend.company.dto.response;

import com.foliolens.backend.company.entity.Company;

public record CompanySearchItemResponse(
        String corpCode,
        String stockCode,
        String corpName,
        String market,
        CompanyMatchType matchType,
        boolean listed
) {

    public static CompanySearchItemResponse from(
            Company company,
            String query
    ) {
        return new CompanySearchItemResponse(
                company.getCorpCode(),
                company.getStockCode(),
                company.getCorpName(),
                resolveMarket(company),
                resolveMatchType(company, query),
                company.isListed()
        );
    }

    private static String resolveMarket(Company company) {
        if (company.getMarket() == null) {
            return null;
        }

        return company.getMarket().name();
    }

    private static CompanyMatchType resolveMatchType(
            Company company,
            String query
    ) {
        if (query.equals(company.getStockCode())) {
            return CompanyMatchType.EXACT_STOCK_CODE;
        }

        if (query.equalsIgnoreCase(company.getCorpName())) {
            return CompanyMatchType.EXACT_NAME;
        }

        return CompanyMatchType.PARTIAL_NAME;
    }
}
