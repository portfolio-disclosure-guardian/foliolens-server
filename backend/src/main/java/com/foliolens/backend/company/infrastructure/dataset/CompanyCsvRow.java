package com.foliolens.backend.company.infrastructure.dataset;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.domain.Market;
import com.foliolens.backend.company.domain.SourceProvider;
import org.apache.commons.csv.CSVRecord;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * CSV 한 행을 자바의 자료형으로 변환하고, Company를 생성하거나 갱신하는 역할
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
        requirePattern(corpCode, "corp_code", "^[0-9]{8}$"); // 기업코드 검증
        requirePattern(stockCode, "stock_code", "^[0-9]{6}$"); // 종목코드 검증
        // 필수 문자열 검증
        requireNotBlank(corpName, "corp_name");
        requireNotBlank(listedName, "listed_name");
        requireNotBlank(corpEngName, "corp_eng_name");
        requireNotBlank(industry, "industry");
        requireNotBlank(sector, "sector");
        // null 검증
        Objects.requireNonNull(market, "market은 필수입니다.");
        Objects.requireNonNull(listingDate, "listing_date는 필수입니다.");

        // 섹터 번호 검증 ( 1~20 사이)
        if (sectorNo < 1 || sectorNo > 20) {
            throw new IllegalArgumentException(
                    "sector_no는 1~20이어야 합니다: " + sectorNo
            );
        }

        // 결산월 검증 ( 1~12월 사이)
        if (fiscalMonth < 1 || fiscalMonth > 12) {
            throw new IllegalArgumentException(
                    "fiscal_month는 1~12여야 합니다: " + fiscalMonth
            );
        }

        // 시가총액 검증 (음수 x)
        if (marketCap < 0) {
            throw new IllegalArgumentException(
                    "market_cap은 음수일 수 없습니다: " + marketCap
            );
        }

        // 공시 건수 검증 (음수 x)
        if (nPeriodic < 0 || nMajor < 0 || nExchange < 0 || nHolding < 0) {
            throw new IllegalArgumentException(
                    "공시 건수는 음수일 수 없습니다."
            );
        }

        // note 정규화 -> 앞뒤 공백 제거
        note = normalizeNullable(note);
    }

    /**
     * Apache Commons CSV가 읽은 CSVRecord를 CompanyCsvRow로 변환하는 팩토리 메서드
     */
    public static CompanyCsvRow from(CSVRecord record) {
        try {
            return new CompanyCsvRow(
                    required(record, "corp_code"),
                    required(record, "stock_code"),
                    required(record, "corp_name"),
                    required(record, "listed_name"),
                    required(record, "corp_eng_name"),
                    parseMarket(required(record, "market")),
                    required(record, "industry"),
                    Short.parseShort(required(record, "sector_no")),
                    required(record, "sector"),
                    LocalDate.parse(required(record, "listing_date")),
                    parseFiscalMonth(required(record, "fiscal_month")),
                    Long.parseLong(required(record, "market_cap")),
                    Integer.parseInt(required(record, "n_periodic")),
                    Integer.parseInt(required(record, "n_major")),
                    Integer.parseInt(required(record, "n_exchange")),
                    Integer.parseInt(required(record, "n_holding")),
                    optional(record, "note")
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "기업 CSV " + record.getRecordNumber()
                            + "번째 데이터 행을 변환할 수 없습니다.",
                    exception
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


    private static Market parseMarket(String value) {
        try {
            return Market.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "지원하지 않는 시장입니다: " + value,
                    exception
            );
        }
    }

    private static short parseFiscalMonth(String value) {
        String normalized = value.trim();

        if (!normalized.endsWith("월")) {
            throw new IllegalArgumentException(
                    "결산월 형식이 올바르지 않습니다: " + value
            );
        }

        return Short.parseShort(
                normalized.substring(0, normalized.length() - 1)
        );
    }


    private static String required(CSVRecord record, String header) {
        String value = record.get(header);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    header + " 값은 필수입니다."
            );
        }

        return value.trim();
    }

    private static String optional(CSVRecord record, String header) {
        if (!record.isMapped(header)) {
            return null;
        }

        return normalizeNullable(record.get(header));
    }

    /**
     * note 정규화 -> 앞 뒤 공백 제거
     */
    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    /**
     * 필수 문자열 검증
     * @param value = 필수 검증 필드
     * @param fieldName = 필수 검증 문자열
     */
    private static void requireNotBlank(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " 값은 필수입니다."
            );
        }
    }

    /**
     * 패턴 검증
     */
    private static void requirePattern(
            String value,
            String fieldName,
            String pattern
    ) {
        requireNotBlank(value, fieldName);

        if (!value.matches(pattern)) {
            throw new IllegalArgumentException(
                    fieldName + " 형식이 올바르지 않습니다: " + value
            );
        }
    }
}
