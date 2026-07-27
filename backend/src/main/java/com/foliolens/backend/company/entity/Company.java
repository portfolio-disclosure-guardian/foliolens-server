package com.foliolens.backend.company.entity;


import com.foliolens.backend.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseTimeEntity {

    private static final Pattern CORP_CODE_PATTERN = Pattern.compile("^[0-9]{8}$");

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("^[0-9A-Z]{6}$");

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    // DART에서 사용하는 8자리 기업 고유번호
    @Column(name = "corp_code", nullable = false, unique = true, length = 8)
    private String corpCode;

    // 상장 종목의 6자리 종목코드
    // 비상장 기업은 종목코드가 없을 수 있기 때문에 nullable
    @Column(name = "stock_code", unique = true, length = 6)
    private String stockCode;

    @Column(name = "corp_name", nullable = false, length = 200)
    private String corpName;

    @Enumerated(EnumType.STRING)
    @Column(name = "market", length = 30)
    private Market market;

    // 별도로 입력받지 않고 stockCode 존재 여부로 계산
    @Column(name = "listed", nullable = false)
    private boolean listed;


    // 기업 기준정보의 유효기간
    // MVP에서는 사용하지 않음. 처음에는 두 값 모두 null로 생성
    @Column(name = "valid_from")
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;

    private Company(
            UUID id,
            String corpCode,
            String stockCode,
            String corpName,
            Market market,
            LocalDate validFrom,
            LocalDate validTo
    ) {
        validateCorpCode(corpCode);
        validateCorpName(corpName);
        validateValidPeriod(validFrom, validTo);

        String normalizedStockCode =
                normalizeStockCode(stockCode);

        validateStockCode(normalizedStockCode);

        this.id = id;
        this.corpCode = corpCode;
        this.stockCode = normalizedStockCode;
        this.corpName = corpName.trim();
        this.market = market;
        this.listed = normalizedStockCode != null;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public static Company create(
            String corpCode,
            String stockCode,
            String corpName,
            Market market
    ) {
        return new Company(
                UUID.randomUUID(),
                corpCode,
                stockCode,
                corpName,
                market,
                null,
                null
        );
    }

    public static Company create(
            String corpCode,
            String stockCode,
            String corpName,
            Market market,
            LocalDate validFrom,
            LocalDate validTo
    ) {
        return new Company(
                UUID.randomUUID(),
                corpCode,
                stockCode,
                corpName,
                market,
                validFrom,
                validTo
        );
    }

    public void updateMasterData(
            String stockCode,
            String corpName,
            Market market,
            LocalDate validFrom,
            LocalDate validTo
    ) {
        validateCorpName(corpName);
        validateValidPeriod(validFrom, validTo);

        String normalizedStockCode =
                normalizeStockCode(stockCode);

        validateStockCode(normalizedStockCode);

        this.stockCode = normalizedStockCode;
        this.corpName = corpName.trim();
        this.market = market;
        this.listed = normalizedStockCode != null;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    private static String normalizeStockCode(
            String stockCode
    ) {
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }

        return stockCode.trim().toUpperCase(Locale.ROOT);
    }



    // ================== 검증 로직 ========================
    private static void validateCorpCode(String corpCode) {
        if (corpCode == null || !CORP_CODE_PATTERN.matcher(corpCode).matches()) {
            throw new IllegalArgumentException("기업 고유번호는 8자리 숫자여야 합니다.");
        }
    }

    private static void validateStockCode(String stockCode) {
        if (stockCode != null && !STOCK_CODE_PATTERN.matcher(stockCode).matches()) {
            throw new IllegalArgumentException("종목코드는 6자리 숫자여야 합니다.");
        }
    }

    private static void validateCorpName(String corpName) {
        if (corpName == null || corpName.isBlank()) {
            throw new IllegalArgumentException("기업명은 필수입니다.");
        }

        if (corpName.trim().length() > 200) {
            throw new IllegalArgumentException("기업명은 200자를 초과할 수 없습니다.");
        }
    }

    private static void validateValidPeriod(LocalDate validFrom, LocalDate validTo) {
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("기업정보 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }
}
