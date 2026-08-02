package com.foliolens.backend.company.infrastructure.dataset;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.domain.Market;
import com.foliolens.backend.company.domain.SourceProvider;

import java.time.LocalDate;
import java.util.Objects;

/**
 * CompanyCsvReader에 의해 변환된 값 보관
 * 값 검증
 * Company 변환
 */
public record CompanyCsvRow(
        String corpCode,
        String stockCode,
        String corpName,
        String listedName,
        String corpEngName,
        Market market,
        String industry,
        short sectorNo,
        String sector,
        LocalDate listingDate,
        short fiscalMonth,
        long marketCap,
        int nPeriodic,
        int nMajor,
        int nExchange,
        int nHolding,
        String note
) {

    public CompanyCsvRow {
        corpCode = normalizeRequired(corpCode, "corp_code");
        stockCode = normalizeRequired(stockCode, "stock_code");
        corpName = normalizeRequired(corpName, "corp_name");
        listedName = normalizeRequired(listedName, "listed_name");
        corpEngName = normalizeRequired(corpEngName, "corp_eng_name");
        industry = normalizeRequired(industry, "industry");
        sector = normalizeRequired(sector, "sector");
        note = normalizeNullable(note);

        Objects.requireNonNull(market, "market은 필수입니다.");
        Objects.requireNonNull(listingDate, "listing_date는 필수입니다.");

        // 패턴 검증
        validatePattern(corpCode, "corp_code", "^[0-9]{8}$");
        validatePattern(stockCode, "stock_code", "^[0-9]{6}$");

        // 섹터 번호 검증 (1~20번 사이)
        if (sectorNo < 1 || sectorNo > 20) {
            throw new IllegalArgumentException(
                    "sector_no는 1~20이어야 합니다: "
                            + sectorNo
            );
        }

        // 결산월 검증 (1~12월 사이)
        if (fiscalMonth < 1 || fiscalMonth > 12) {
            throw new IllegalArgumentException(
                    "fiscal_month는 1~12여야 합니다: "
                            + fiscalMonth
            );
        }

        // 시가총액 검증 (음수 x)
        if (marketCap < 0) {
            throw new IllegalArgumentException(
                    "market_cap은 음수일 수 없습니다: "
                            + marketCap
            );
        }

        // 공시 건수 검증 (음수 x)
        if (nPeriodic < 0 || nMajor < 0 || nExchange < 0 || nHolding < 0) {
            throw new IllegalArgumentException(
                    "공시 건수는 음수일 수 없습니다."
            );
        }
    }

    public Company toCompany(
            LocalDate marketCapAsOf,
            String datasetVersion
    ) {
        return Company.create(
                corpCode,
                stockCode,
                corpName,
                listedName,
                corpEngName,
                market,
                industry,
                sectorNo,
                sector,
                listingDate,
                fiscalMonth,
                marketCap,
                marketCapAsOf,
                SourceProvider.CONTEST,
                datasetVersion,
                note
        );
    }

    public void updateCompany(
            Company company,
            LocalDate marketCapAsOf,
            String datasetVersion
    ) {
        company.updateMasterData(
                stockCode,
                corpName,
                listedName,
                corpEngName,
                market,
                industry,
                sectorNo,
                sector,
                listingDate,
                fiscalMonth,
                marketCap,
                marketCapAsOf,
                datasetVersion,
                note
        );

        company.markListed();
    }

    // 정규화 -> 앞 뒤 공백 제거
    private static String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " 값은 필수입니다."
            );
        }

        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // 패턴 검증
    private static void validatePattern(
            String value,
            String fieldName,
            String pattern
    ) {
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " 형식이 올바르지 않습니다: "
                            + value
            );
        }
    }
}
