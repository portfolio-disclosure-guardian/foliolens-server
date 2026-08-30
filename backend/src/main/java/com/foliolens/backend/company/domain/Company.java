package com.foliolens.backend.company.domain;

import com.foliolens.backend.global.basetime.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "companies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Pattern(regexp = "^[0-9]{8}$")
    @Column(name = "corp_code", nullable = false, unique = true, length = 8)
    private String corpCode; // 고유번호

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$")
    @Column(name = "stock_code", nullable = false, unique = true, length = 6)
    private String stockCode; // 거래소 종목코드

    @NotBlank
    @Size(max = 200)
    @Column(name = "corp_name", nullable = false, length = 200)
    private String corpName; // 법인명

    @NotBlank
    @Size(max = 200)
    @Column(name = "listed_name", nullable = false, length = 200)
    private String listedName; // 종목명

    @NotBlank
    @Size(max = 300)
    @Column(name = "corp_eng_name", nullable = false, length = 300)
    private String corpEngName; // 영문 법인명

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "market", nullable = false, length = 20)
    private Market market; // 상장된 시장

    @NotBlank
    @Size(max = 100)
    @Column(name = "industry", nullable = false, length = 100)
    private String industry; // 업종 대분류

    @NotNull
    @Min(1)
    @Max(20)
    @Column(name = "sector_no", nullable = false)
    private Short sectorNo; // 세부 섹터의 번호

    @NotBlank
    @Size(max = 100)
    @Column(name = "sector", nullable = false, length = 100)
    private String sector; // 세부 섹터

    @NotNull
    @Column(name = "listing_date", nullable = false)
    private LocalDate listingDate; // 상장된 날짜

    @NotNull
    @Min(1)
    @Max(12)
    @Column(name = "fiscal_month", nullable = false)
    private Short fiscalMonth; // 기업의 결산월

    @NotNull
    @PositiveOrZero
    @Column(name = "market_cap", nullable = false)
    private Long marketCap; // 시가총액

    @NotNull
    @Column(name = "market_cap_as_of", nullable = false)
    private LocalDate marketCapAsOf; // 시가총액 조회 기준 날짜

    @NotNull
    @Column(name = "listed", nullable = false)
    private Boolean listed; // 상장기업 취급 여부

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_provider", nullable = false, length = 30)
    private SourceProvider sourceProvider; // 기업 정보 제공 출처

    @NotBlank
    @Size(max = 100)
    @Column(name = "source_dataset_version", nullable = false, length = 100)
    private String sourceDatasetVersion; // 데이터셋 버전

    @Size(max = 1000)
    @Column(name = "note", length = 1000)
    private String note; // 참고사항

    private Company(
            String corpCode,
            String stockCode,
            String corpName,
            String listedName,
            String corpEngName,
            Market market,
            String industry,
            Short sectorNo,
            String sector,
            LocalDate listingDate,
            Short fiscalMonth,
            Long marketCap,
            LocalDate marketCapAsOf,
            SourceProvider sourceProvider,
            String sourceDatasetVersion,
            String note
    ) {
        this.corpCode = corpCode;
        this.stockCode = stockCode;
        this.corpName = corpName;
        this.listedName = listedName;
        this.corpEngName = corpEngName;
        this.market = market;
        this.industry = industry;
        this.sectorNo = sectorNo;
        this.sector = sector;
        this.listingDate = listingDate;
        this.fiscalMonth = fiscalMonth;
        this.marketCap = marketCap;
        this.marketCapAsOf = marketCapAsOf;
        this.listed = true;
        this.sourceProvider = sourceProvider;
        this.sourceDatasetVersion = sourceDatasetVersion;
        this.note = normalizeNote(note);
    }

    public static Company create(
            String corpCode,
            String stockCode,
            String corpName,
            String listedName,
            String corpEngName,
            Market market,
            String industry,
            Short sectorNo,
            String sector,
            LocalDate listingDate,
            Short fiscalMonth,
            Long marketCap,
            LocalDate marketCapAsOf,
            SourceProvider sourceProvider,
            String sourceDatasetVersion,
            String note
    ) {
        return new Company(
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
                sourceProvider,
                sourceDatasetVersion,
                note
        );
    }

    public void updateMasterData(
            String stockCode,
            String corpName,
            String listedName,
            String corpEngName,
            Market market,
            String industry,
            Short sectorNo,
            String sector,
            LocalDate listingDate,
            Short fiscalMonth,
            Long marketCap,
            LocalDate marketCapAsOf,
            String sourceDatasetVersion,
            String note
    ) {
        this.stockCode = stockCode;
        this.corpName = corpName;
        this.listedName = listedName;
        this.corpEngName = corpEngName;
        this.market = market;
        this.industry = industry;
        this.sectorNo = sectorNo;
        this.sector = sector;
        this.listingDate = listingDate;
        this.fiscalMonth = fiscalMonth;
        this.marketCap = marketCap;
        this.marketCapAsOf = marketCapAsOf;
        this.sourceDatasetVersion = sourceDatasetVersion;
        this.note = normalizeNote(note);
    }

    public void markListed() {
        this.listed = true;
    }

    public void markUnlisted() {
        this.listed = false;
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }

        return note.trim();
    }
}
