package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "disclosure_fact_evidences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureFactEvidenceEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disclosure_fact_id", nullable = false)
    private DisclosureFactEntity disclosureFact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disclosure_evidence_id", nullable = false)
    private DisclosureEvidenceEntity disclosureEvidence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disclosure_document_id", nullable = false)
    private DisclosureDocument disclosureDocument;

    @Column(name = "evidence_order", nullable = false)
    private int evidenceOrder;

    DisclosureFactEvidenceEntity(
            DisclosureFactEntity disclosureFact,
            DisclosureEvidenceEntity disclosureEvidence,
            DisclosureDocument disclosureDocument,
            int evidenceOrder
    ) {
        this.disclosureFact = Objects.requireNonNull(
                disclosureFact,
                "disclosureFact는 필수입니다."
        );
        this.disclosureEvidence = Objects.requireNonNull(
                disclosureEvidence,
                "disclosureEvidence는 필수입니다."
        );
        this.disclosureDocument = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );
        if (evidenceOrder < 1) {
            throw new IllegalArgumentException("evidenceOrder는 1 이상이어야 합니다.");
        }
        this.evidenceOrder = evidenceOrder;
    }
}
