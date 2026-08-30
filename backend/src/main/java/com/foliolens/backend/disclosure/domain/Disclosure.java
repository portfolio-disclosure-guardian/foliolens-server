package com.foliolens.backend.disclosure.domain;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.disclosure.domain.converter.DisclosureFileFormatConverter;
import com.foliolens.backend.disclosure.domain.converter.DisclosureSourceGroupConverter;
import com.foliolens.backend.global.basetime.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "disclosures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disclosure extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^(periodic|major|exchange|holding)_[0-9]{14}$")
    @Column(
            name = "source_doc_id",
            nullable = false,
            unique = true,
            length = 64
    )
    private String sourceDocId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "company_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_disclosures_company"
            )
    )
    private Company company;

    @NotBlank
    @Pattern(regexp = "^[0-9]{14}$")
    @Column(
            name = "receipt_no",
            nullable = false,
            unique = true,
            length = 14
    )
    private String receiptNo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private DisclosureCategory category;

    @NotNull
    @Convert(converter = DisclosureSourceGroupConverter.class)
    @Column(name = "source_group", nullable = false, length = 20)
    private DisclosureSourceGroup sourceGroup;

    @Size(max = 200)
    @Column(name = "raw_subtype", length = 200)
    private String rawSubtype;

    @NotBlank
    @Size(max = 500)
    @Column(name = "report_name", nullable = false, length = 500)
    private String reportName;

    @Column(name = "correction", nullable = false)
    private boolean correction;

    @NotNull
    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @NotBlank
    @Size(max = 200)
    @Column(name = "submitter", nullable = false, length = 200)
    private String submitter;

    @Min(1900)
    @Max(2100)
    @Column(name = "base_year")
    private Short baseYear;

    @Column(name = "base_month")
    private Short baseMonth;

    @NotBlank
    @Column(name = "manifest_path", nullable = false)
    private String manifestPath;

    @NotNull
    @Convert(converter = DisclosureFileFormatConverter.class)
    @Column(name = "file_format", nullable = false, length = 20)
    private DisclosureFileFormat fileFormat;

    @NotNull
    @Positive
    @Column(name = "expected_file_count", nullable = false)
    private Integer expectedFileCount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            name = "source_provider",
            nullable = false,
            length = 30
    )
    private SourceProvider sourceProvider;

    @NotBlank
    @Size(max = 100)
    @Column(
            name = "source_dataset_version",
            nullable = false,
            length = 100
    )
    private String sourceDatasetVersion;

    private Disclosure(
            String sourceDocId,
            Company company,
            String receiptNo,
            DisclosureSourceGroup sourceGroup,
            String rawSubtype,
            String reportName,
            boolean correction,
            LocalDate receiptDate,
            String submitter,
            Short baseYear,
            Short baseMonth,
            String manifestPath,
            DisclosureFileFormat fileFormat,
            Integer expectedFileCount,
            String sourceDatasetVersion
    ) {
        this.sourceDocId = requireNotBlank(
                sourceDocId,
                "sourceDocId"
        );
        this.company = Objects.requireNonNull(
                company,
                "company는 필수입니다."
        );
        this.receiptNo = validateReceiptNo(receiptNo);
        this.sourceGroup = Objects.requireNonNull(
                sourceGroup,
                "sourceGroup은 필수입니다."
        );
        this.category = sourceGroup.getCategory(); // 카테고리는 직접 입력하지 않고 sourceGroup을 통해 자동으로 정해짐
        this.rawSubtype = normalizeNullable(rawSubtype);
        this.reportName = requireNotBlank(
                reportName,
                "reportName"
        );
        this.correction = correction;
        this.receiptDate = Objects.requireNonNull(
                receiptDate,
                "receiptDate는 필수입니다."
        );
        this.submitter = requireNotBlank(
                submitter,
                "submitter"
        );
        this.baseYear = baseYear;
        this.baseMonth = baseMonth;
        this.manifestPath = validateManifestPath(manifestPath);
        this.fileFormat = Objects.requireNonNull(
                fileFormat,
                "fileFormat은 필수입니다."
        );
        this.expectedFileCount = validateExpectedFileCount(
                expectedFileCount
        );
        this.sourceProvider = SourceProvider.CONTEST;
        this.sourceDatasetVersion = requireNotBlank(
                sourceDatasetVersion,
                "sourceDatasetVersion"
        );

        validateSourceDocId();
        validateBasePeriod();
    }

    public static Disclosure create(
            String sourceDocId,
            Company company,
            String receiptNo,
            DisclosureSourceGroup sourceGroup,
            String rawSubtype,
            String reportName,
            boolean correction,
            LocalDate receiptDate,
            String submitter,
            Short baseYear,
            Short baseMonth,
            String manifestPath,
            DisclosureFileFormat fileFormat,
            Integer expectedFileCount,
            String sourceDatasetVersion
    ) {
        return new Disclosure(
                sourceDocId,
                company,
                receiptNo,
                sourceGroup,
                rawSubtype,
                reportName,
                correction,
                receiptDate,
                submitter,
                baseYear,
                baseMonth,
                manifestPath,
                fileFormat,
                expectedFileCount,
                sourceDatasetVersion
        );
    }

    public void updateMetadata(
            String rawSubtype,
            String reportName,
            boolean correction,
            LocalDate receiptDate,
            String submitter,
            Short baseYear,
            Short baseMonth,
            String manifestPath,
            DisclosureFileFormat fileFormat,
            Integer expectedFileCount,
            String sourceDatasetVersion
    ) {
        this.rawSubtype = normalizeNullable(rawSubtype);
        this.reportName = requireNotBlank(
                reportName,
                "reportName"
        );
        this.correction = correction;
        this.receiptDate = Objects.requireNonNull(
                receiptDate,
                "receiptDate는 필수입니다."
        );
        this.submitter = requireNotBlank(
                submitter,
                "submitter"
        );
        this.baseYear = baseYear;
        this.baseMonth = baseMonth;
        this.manifestPath = validateManifestPath(manifestPath);
        this.fileFormat = Objects.requireNonNull(
                fileFormat,
                "fileFormat은 필수입니다."
        );
        this.expectedFileCount = validateExpectedFileCount(
                expectedFileCount
        );
        this.sourceDatasetVersion = requireNotBlank(
                sourceDatasetVersion,
                "sourceDatasetVersion"
        );

        validateBasePeriod();
    }

    private void validateSourceDocId() {
        String expectedSourceDocId =
                sourceGroup.getValue() + "_" + receiptNo;

        if (!sourceDocId.equals(expectedSourceDocId)) {
            throw new IllegalArgumentException(
                    "sourceDocId가 공시 그룹과 접수번호 조합에 맞지 않습니다. "
                            + "expected=" + expectedSourceDocId
                            + ", actual=" + sourceDocId
            );
        }
    }

    private void validateBasePeriod() {
        if (sourceGroup == DisclosureSourceGroup.PERIODIC) {
            if (baseYear == null || baseMonth == null) {
                throw new IllegalArgumentException(
                        "정기공시는 baseYear와 baseMonth가 필수입니다."
                );
            }

            if (baseYear < 1900 || baseYear > 2100) {
                throw new IllegalArgumentException(
                        "baseYear는 1900~2100 범위여야 합니다."
                );
            }

            if (
                    baseMonth != 3
                            && baseMonth != 6
                            && baseMonth != 9
                            && baseMonth != 12
            ) {
                throw new IllegalArgumentException(
                        "baseMonth는 3, 6, 9, 12 중 하나여야 합니다."
                );
            }

            return;
        }

        if (baseYear != null || baseMonth != null) {
            throw new IllegalArgumentException(
                    "정기공시가 아니면 baseYear와 baseMonth는 null이어야 합니다."
            );
        }
    }

    private static String validateReceiptNo(String value) {
        String normalized = requireNotBlank(value, "receiptNo");

        if (!normalized.matches("^[0-9]{14}$")) {
            throw new IllegalArgumentException(
                    "receiptNo는 14자리 숫자 문자열이어야 합니다: "
                            + normalized
            );
        }

        return normalized;
    }

    private static Integer validateExpectedFileCount(
            Integer value
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    "expectedFileCount는 1 이상이어야 합니다."
            );
        }

        return value;
    }

    private static String validateManifestPath(String value) {
        String normalized = requireNotBlank(
                value,
                "manifestPath"
        );

        if (
                normalized.startsWith("/")
                        || normalized.startsWith("\\")
                        || normalized.matches("^[A-Za-z]:.*")
                        || normalized.contains("\\")
        ) {
            throw new IllegalArgumentException(
                    "manifestPath는 / 구분자를 사용하는 상대경로여야 합니다: "
                            + normalized
            );
        }

        String[] segments = normalized.split("/");

        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                        "manifestPath에는 상위 경로(..)를 사용할 수 없습니다: "
                                + normalized
                );
            }
        }

        return normalized;
    }

    private static String requireNotBlank(
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
