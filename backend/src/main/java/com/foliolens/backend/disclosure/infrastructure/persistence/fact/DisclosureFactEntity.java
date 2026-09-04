package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.fact.AccountingBasis;
import com.foliolens.backend.disclosure.domain.fact.FactAvailabilityStatus;
import com.foliolens.backend.disclosure.domain.fact.FactGenerationMethod;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.domain.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "disclosure_facts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureFactEntity extends BaseTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disclosure_id", nullable = false)
    private Disclosure disclosure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disclosure_document_id", nullable = false)
    private DisclosureDocument disclosureDocument;

    @Column(name = "fact_key", nullable = false, length = 200)
    private String factKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private FactValueType valueType;

    @Column(name = "raw_value", columnDefinition = "text")
    private String rawValue;

    @Column(name = "raw_unit", columnDefinition = "text")
    private String rawUnit;

    @Column(name = "normalized_decimal_value", columnDefinition = "numeric")
    private BigDecimal normalizedDecimalValue;

    @Column(name = "normalized_date_value")
    private LocalDate normalizedDateValue;

    @Column(name = "normalized_text_value", columnDefinition = "text")
    private String normalizedTextValue;

    @Column(name = "normalized_unit", length = 50)
    private String normalizedUnit;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_basis", nullable = false, length = 30)
    private AccountingBasis accountingBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_method", nullable = false, length = 30)
    private FactGenerationMethod generationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 30)
    private FactAvailabilityStatus availabilityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "normalization_status", nullable = false, length = 30)
    private FactNormalizationStatus normalizationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private FactValidationStatus validationStatus;

    @Column(name = "source_receipt_no", nullable = false, length = 14)
    private String sourceReceiptNo;

    @Column(name = "policy_version", length = 100)
    private String policyVersion;

    @OneToMany(
            mappedBy = "disclosureFact",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("evidenceOrder ASC")
    private List<DisclosureFactEvidenceEntity> evidenceLinks = new ArrayList<>();

    DisclosureFactEntity(
            UUID id,
            Disclosure disclosure,
            DisclosureDocument disclosureDocument,
            String factKey,
            FactValueType valueType,
            String rawValue,
            String rawUnit,
            BigDecimal normalizedDecimalValue,
            LocalDate normalizedDateValue,
            String normalizedTextValue,
            String normalizedUnit,
            String currency,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate asOfDate,
            AccountingBasis accountingBasis,
            FactGenerationMethod generationMethod,
            FactAvailabilityStatus availabilityStatus,
            FactNormalizationStatus normalizationStatus,
            FactValidationStatus validationStatus,
            String sourceReceiptNo,
            String policyVersion
    ) {
        this.id = Objects.requireNonNull(id, "fact id는 필수입니다.");
        this.disclosure = Objects.requireNonNull(disclosure, "disclosure는 필수입니다.");
        this.disclosureDocument = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );
        this.factKey = requireText(factKey, "factKey");
        this.valueType = Objects.requireNonNull(valueType, "valueType은 필수입니다.");
        this.rawValue = normalizeOptional(rawValue);
        this.rawUnit = normalizeOptional(rawUnit);
        this.normalizedDecimalValue = normalizedDecimalValue;
        this.normalizedDateValue = normalizedDateValue;
        this.normalizedTextValue = normalizeOptional(normalizedTextValue);
        this.normalizedUnit = normalizeOptional(normalizedUnit);
        this.currency = normalizeOptional(currency);
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.asOfDate = asOfDate;
        this.accountingBasis = Objects.requireNonNull(accountingBasis, "accountingBasis는 필수입니다.");
        this.generationMethod = Objects.requireNonNull(generationMethod, "generationMethod는 필수입니다.");
        this.availabilityStatus = Objects.requireNonNull(availabilityStatus, "availabilityStatus는 필수입니다.");
        this.normalizationStatus = Objects.requireNonNull(normalizationStatus, "normalizationStatus는 필수입니다.");
        this.validationStatus = Objects.requireNonNull(validationStatus, "validationStatus는 필수입니다.");
        this.sourceReceiptNo = requireText(sourceReceiptNo, "sourceReceiptNo");
        this.policyVersion = normalizeOptional(policyVersion);
    }

    void addEvidence(DisclosureEvidenceEntity evidence, int evidenceOrder) {
        evidenceLinks.add(new DisclosureFactEvidenceEntity(
                this,
                evidence,
                disclosureDocument,
                evidenceOrder
        ));
    }

    public List<DisclosureFactEvidenceEntity> getEvidenceLinks() {
        return List.copyOf(evidenceLinks);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
