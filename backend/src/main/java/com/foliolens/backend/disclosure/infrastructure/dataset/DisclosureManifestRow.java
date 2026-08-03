package com.foliolens.backend.disclosure.infrastructure.dataset;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureFileFormat;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;

import java.time.LocalDate;
import java.util.Objects;

public record DisclosureManifestRow(

        @JsonProperty("doc_id")
        String docId,
        @JsonProperty("corp_code")
        String corpCode,
        @JsonProperty("corp_name")
        String corpName,
        @JsonProperty("listed_name")
        String listedName,
        @JsonProperty("stock_code")
        String stockCode,
        @JsonProperty("industry")
        String industry,
        @JsonProperty("sector")
        String sector,
        @JsonProperty("doc_group")
        String docGroup,
        @JsonProperty("doc_subtype")
        String docSubtype,
        @JsonProperty("report_nm")
        String reportName,
        @JsonProperty("is_correction")
        Boolean correction,
        @JsonProperty("rcept_no")
        String receiptNo,
        @JsonProperty("rcept_dt")
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "yyyyMMdd"
        )
        LocalDate receiptDate,
        @JsonProperty("flr_nm")
        String submitter,
        @JsonProperty("base_year")
        Short baseYear,
        @JsonProperty("base_month")
        Short baseMonth,
        @JsonProperty("file_path")
        String filePath,
        @JsonProperty("file_format")
        String fileFormat,
        @JsonProperty("n_files")
        Integer fileCount
) {

    /*
     * compact constructor
     *
     * JSON 한 줄이 DisclosureManifestRow로 변환될 때 자동으로 호출된다.
     * 필수값 정규화 및 행 단위 검증을 수행한다.
     */
    public DisclosureManifestRow {
        docId = normalizeRequired(docId, "doc_id");
        corpCode = normalizeRequired(corpCode, "corp_code");
        corpName = normalizeRequired(corpName, "corp_name");
        listedName = normalizeRequired(listedName, "listed_name");
        stockCode = normalizeRequired(stockCode, "stock_code");
        industry = normalizeRequired(industry, "industry");
        sector = normalizeRequired(sector, "sector");
        docGroup = normalizeRequired(docGroup, "doc_group");
        docSubtype = normalizeNullable(docSubtype);
        reportName = normalizeRequired(reportName, "report_nm");
        receiptNo = normalizeRequired(receiptNo, "rcept_no");
        submitter = normalizeRequired(submitter, "flr_nm");
        filePath = normalizeRequired(filePath, "file_path");
        fileFormat = normalizeRequired(fileFormat, "file_format");

        Objects.requireNonNull(correction, "is_correction은 필수입니다.");
        Objects.requireNonNull(receiptDate, "rcept_dt는 필수입니다.");
        Objects.requireNonNull(fileCount, "n_files는 필수입니다.");

        validatePattern(corpCode, "corp_code", "^[0-9]{8}$");
        validatePattern(stockCode, "stock_code", "^[0-9]{6}$");
        validatePattern(receiptNo, "rcept_no", "^[0-9]{14}$");

        validateMaxLength(docId, "doc_id", 64);
        validateMaxLength(corpName, "corp_name", 200);
        validateMaxLength(listedName, "listed_name", 200);
        validateMaxLength(industry, "industry", 100);
        validateMaxLength(sector, "sector", 100);
        validateMaxLength(docSubtype, "doc_subtype", 200);
        validateMaxLength(reportName, "report_nm", 500);
        validateMaxLength(submitter, "flr_nm", 200);

        DisclosureSourceGroup sourceGroup = DisclosureSourceGroup.fromValue(docGroup);

        DisclosureFileFormat.fromValue(fileFormat);

        validateDocId(docId, sourceGroup, receiptNo);

        validateBasePeriod(sourceGroup, baseYear, baseMonth);

        filePath = validateRelativePath(filePath);

        if (fileCount <= 0) {
            throw new IllegalArgumentException(
                    "n_files는 1 이상이어야 합니다: "
                            + fileCount
            );
        }
    }

    /**
     * 신규 공시 Entity 생성
     */
    public Disclosure toDisclosure(Company company, String datasetVersion) {
        validateCompany(company);

        return Disclosure.create(
                docId,
                company,
                receiptNo,
                sourceGroup(),
                docSubtype,
                reportName,
                correction,
                receiptDate,
                submitter,
                baseYear,
                baseMonth,
                filePath,
                disclosureFileFormat(),
                fileCount,
                datasetVersion
        );
    }

    /**
     * 기존 공시의 변경 가능한 메타데이터 갱신
     */
    public void updateDisclosure(Disclosure disclosure, String datasetVersion) {
        Objects.requireNonNull(
                disclosure,
                "disclosure는 필수입니다."
        );

        validateDisclosureIdentity(disclosure);
        validateCompany(disclosure.getCompany());

        disclosure.updateMetadata(
                docSubtype,
                reportName,
                correction,
                receiptDate,
                submitter,
                baseYear,
                baseMonth,
                filePath,
                disclosureFileFormat(),
                fileCount,
                datasetVersion
        );
    }

    /**
     * manifest의 원본 문자열을 Java Enum으로 변환
     */
    public DisclosureSourceGroup sourceGroup() {
        return DisclosureSourceGroup.fromValue(docGroup);
    }

    /**
     * manifest의 파일 형식 문자열을 Java Enum으로 변환
     */
    public DisclosureFileFormat disclosureFileFormat() {
        return DisclosureFileFormat.fromValue(fileFormat);
    }

    /**
     * manifest의 기업 정보와 DB Company가 동일한 기업인지 검증
     */
    public void validateCompany(Company company) {
        Objects.requireNonNull(
                company,
                "company는 필수입니다."
        );

        validateSameValue(
                "corp_code",
                corpCode,
                company.getCorpCode()
        );
        validateSameValue(
                "stock_code",
                stockCode,
                company.getStockCode()
        );
        validateSameValue(
                "corp_name",
                corpName,
                company.getCorpName()
        );
        validateSameValue(
                "listed_name",
                listedName,
                company.getListedName()
        );
        validateSameValue(
                "industry",
                industry,
                company.getIndustry()
        );
        validateSameValue(
                "sector",
                sector,
                company.getSector()
        );
    }

    private void validateDisclosureIdentity(
            Disclosure disclosure
    ) {
        validateSameValue(
                "doc_id",
                docId,
                disclosure.getSourceDocId()
        );
        validateSameValue(
                "rcept_no",
                receiptNo,
                disclosure.getReceiptNo()
        );

        if (sourceGroup() != disclosure.getSourceGroup()) {
            throw new IllegalArgumentException(
                    "기존 공시의 source_group과 manifest가 다릅니다. "
                            + "existing="
                            + disclosure.getSourceGroup()
                            + ", input="
                            + sourceGroup()
            );
        }
    }

    private static void validateDocId(
            String docId,
            DisclosureSourceGroup sourceGroup,
            String receiptNo
    ) {
        String expected =
                sourceGroup.getValue() + "_" + receiptNo;

        if (!expected.equals(docId)) {
            throw new IllegalArgumentException(
                    "doc_id가 doc_group과 rcept_no 조합에 맞지 않습니다. "
                            + "expected="
                            + expected
                            + ", actual="
                            + docId
            );
        }
    }

    private static void validateBasePeriod(
            DisclosureSourceGroup sourceGroup,
            Short baseYear,
            Short baseMonth
    ) {
        if (sourceGroup == DisclosureSourceGroup.PERIODIC) {
            if (baseYear == null || baseMonth == null) {
                throw new IllegalArgumentException(
                        "정기공시는 base_year와 base_month가 필수입니다."
                );
            }

            if (baseYear < 1900 || baseYear > 2100) {
                throw new IllegalArgumentException(
                        "base_year는 1900~2100 범위여야 합니다: "
                                + baseYear
                );
            }

            if (
                    baseMonth != 3
                            && baseMonth != 6
                            && baseMonth != 9
                            && baseMonth != 12
            ) {
                throw new IllegalArgumentException(
                        "base_month는 3, 6, 9, 12 중 하나여야 합니다: "
                                + baseMonth
                );
            }

            return;
        }

        if (baseYear != null || baseMonth != null) {
            throw new IllegalArgumentException(
                    "정기공시가 아니면 base_year와 base_month는 null이어야 합니다."
            );
        }
    }

    private static String validateRelativePath(String value) {
        if (
                value.startsWith("/")
                        || value.startsWith("\\")
                        || value.matches("^[A-Za-z]:.*")
                        || value.contains("\\")
        ) {
            throw new IllegalArgumentException(
                    "file_path는 / 구분자를 사용하는 상대경로여야 합니다: "
                            + value
            );
        }

        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                        "file_path에는 상위 경로(..)를 사용할 수 없습니다: "
                                + value
                );
            }
        }

        return value;
    }

    private static void validatePattern(
            String value,
            String fieldName,
            String pattern
    ) {
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException(
                    fieldName + " 형식이 올바르지 않습니다: "
                            + value
            );
        }
    }

    private static void validateMaxLength(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName
                            + "의 최대 길이는 "
                            + maxLength
                            + "입니다."
            );
        }
    }

    private static void validateSameValue(
            String fieldName,
            Object manifestValue,
            Object databaseValue
    ) {
        if (!Objects.equals(manifestValue, databaseValue)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " 값이 기업 또는 공시 데이터와 다릅니다. "
                            + "manifest="
                            + manifestValue
                            + ", database="
                            + databaseValue
            );
        }
    }

    private static String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 필수입니다."
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
}
