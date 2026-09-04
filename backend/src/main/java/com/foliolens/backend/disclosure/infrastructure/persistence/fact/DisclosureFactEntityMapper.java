package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.domain.fact.CodeFactValue;
import com.foliolens.backend.disclosure.domain.fact.DateFactValue;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceLocation;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValue;
import com.foliolens.backend.disclosure.domain.fact.TextFactValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class DisclosureFactEntityMapper {

    public DisclosureEvidenceEntity toEntity(
            DisclosureEvidence source,
            DisclosureDocument document,
            DisclosureSection section,
            DisclosureContentBlock contentBlock
    ) {
        Objects.requireNonNull(source, "source Evidence는 필수입니다.");
        Disclosure disclosure = requireDocumentOwner(document);
        validateEvidenceOwner(source, disclosure, document, section, contentBlock);

        if (source.status() != EvidenceStatus.VERIFIED) {
            throw new IllegalArgumentException(
                    "VERIFIED Evidence만 영속화할 수 있습니다."
            );
        }

        EventDocumentRole eventRole = Objects.requireNonNull(
                source.eventDocumentRole(),
                "영속화할 Evidence에는 eventDocumentRole이 필요합니다."
        );

        DisclosureEvidenceLocation location = source.location();
        DisclosureEvidenceValue value = source.value();
        return new DisclosureEvidenceEntity(
                source.evidenceId(),
                disclosure,
                document,
                source.receiptNo(),
                source.documentName(),
                source.documentFileRole(),
                eventRole,
                section,
                source.sectionPath(),
                contentBlock,
                source.blockType(),
                source.tableIndexOrName(),
                location.sourceLineStart(),
                location.sourceLineEnd(),
                location.tableNestingPath(),
                location.tableRowIndex(),
                location.tableCellIndex(),
                value.sourceText(),
                value.rowLabel(),
                value.columnLabel(),
                value.rawValue(),
                value.rawUnit(),
                value.noteText(),
                source.status()
        );
    }

    public DisclosureFactEntity toEntity(
            DisclosureFact source,
            DisclosureDocument document,
            Map<UUID, DisclosureEvidenceEntity> evidencesById
    ) {
        Objects.requireNonNull(source, "source Fact는 필수입니다.");
        Objects.requireNonNull(evidencesById, "evidencesById는 필수입니다.");
        Disclosure disclosure = requireDocumentOwner(document);
        validateFactOwner(source, disclosure, document);

        if (source.validationStatus() != FactValidationStatus.VERIFIED) {
            throw new IllegalArgumentException(
                    "VERIFIED Fact만 영속화할 수 있습니다."
            );
        }

        BigDecimal decimalValue = null;
        LocalDate dateValue = null;
        String textValue = null;
        FactValue normalizedValue = source.normalizedValue();
        if (normalizedValue instanceof DecimalFactValue decimal) {
            decimalValue = decimal.value();
        } else if (normalizedValue instanceof DateFactValue date) {
            dateValue = date.value();
        } else if (normalizedValue instanceof TextFactValue text) {
            textValue = text.value();
        } else if (normalizedValue instanceof CodeFactValue code) {
            textValue = code.value();
        }

        DisclosureFactEntity entity = new DisclosureFactEntity(
                source.factId(),
                disclosure,
                document,
                source.factKey(),
                source.valueType(),
                source.rawValue(),
                source.rawUnit(),
                decimalValue,
                dateValue,
                textValue,
                source.normalizedUnit(),
                source.currency(),
                source.periodStart(),
                source.periodEnd(),
                source.asOfDate(),
                source.accountingBasis(),
                source.generationMethod(),
                source.availabilityStatus(),
                source.normalizationStatus(),
                source.validationStatus(),
                source.sourceReceiptNo(),
                source.policyVersion()
        );

        int order = 1;
        for (UUID evidenceId : source.evidenceIds()) {
            DisclosureEvidenceEntity evidence = evidencesById.get(evidenceId);
            if (evidence == null) {
                throw new IllegalArgumentException(
                        "Fact가 참조하는 VERIFIED Evidence가 없습니다: " + evidenceId
                );
            }
            if (!document.getId().equals(
                    evidence.getDisclosureDocument().getId()
            )) {
                throw new IllegalArgumentException(
                        "다른 문서의 Evidence를 Fact에 연결할 수 없습니다: " + evidenceId
                );
            }
            entity.addEvidence(evidence, order++);
        }
        return entity;
    }

    public DisclosureEvidence toDomain(DisclosureEvidenceEntity source) {
        Objects.requireNonNull(source, "source Evidence Entity는 필수입니다.");
        return new DisclosureEvidence(
                source.getId(),
                source.getDisclosure().getId(),
                source.getDisclosureDocument().getId(),
                source.getReceiptNo(),
                source.getDocumentName(),
                source.getDocumentFileRole(),
                source.getEventDocumentRole(),
                source.getSection() == null ? null : source.getSection().getId(),
                source.getSectionPath(),
                source.getContentBlock() == null
                        ? null
                        : source.getContentBlock().getId(),
                source.getBlockType(),
                source.getTableIndexOrName(),
                new DisclosureEvidenceLocation(
                        source.getSourceLineStart(),
                        source.getSourceLineEnd(),
                        source.getTableNestingPath(),
                        source.getTableRowIndex(),
                        source.getTableCellIndex()
                ),
                new DisclosureEvidenceValue(
                        source.getSourceText(),
                        source.getRowLabel(),
                        source.getColumnLabel(),
                        source.getRawValue(),
                        source.getRawUnit(),
                        source.getNoteText()
                ),
                source.getStatus()
        );
    }

    public DisclosureFact toDomain(DisclosureFactEntity source) {
        Objects.requireNonNull(source, "source Fact Entity는 필수입니다.");
        FactValue normalizedValue = switch (source.getValueType()) {
            case DECIMAL -> source.getNormalizedDecimalValue() == null
                    ? null
                    : new DecimalFactValue(source.getNormalizedDecimalValue());
            case DATE -> source.getNormalizedDateValue() == null
                    ? null
                    : new DateFactValue(source.getNormalizedDateValue());
            case TEXT -> source.getNormalizedTextValue() == null
                    ? null
                    : new TextFactValue(source.getNormalizedTextValue());
            case CODE -> source.getNormalizedTextValue() == null
                    ? null
                    : new CodeFactValue(source.getNormalizedTextValue());
            default -> throw new IllegalStateException(
                    "현재 영속 계층에서 지원하지 않는 Fact 타입입니다: "
                            + source.getValueType()
            );
        };

        return new DisclosureFact(
                source.getId(),
                source.getDisclosure().getId(),
                source.getDisclosureDocument().getId(),
                source.getFactKey(),
                source.getValueType(),
                source.getRawValue(),
                source.getRawUnit(),
                normalizedValue,
                source.getNormalizedUnit(),
                source.getCurrency(),
                source.getPeriodStart(),
                source.getPeriodEnd(),
                source.getAsOfDate(),
                source.getAccountingBasis(),
                source.getGenerationMethod(),
                source.getAvailabilityStatus(),
                source.getNormalizationStatus(),
                source.getValidationStatus(),
                source.getSourceReceiptNo(),
                source.getPolicyVersion(),
                source.getEvidenceLinks().stream()
                        .map(link -> link.getDisclosureEvidence().getId())
                        .toList()
        );
    }

    private static Disclosure requireDocumentOwner(DisclosureDocument document) {
        Objects.requireNonNull(document, "document는 필수입니다.");
        Objects.requireNonNull(document.getId(), "저장된 document가 필요합니다.");
        Disclosure disclosure = Objects.requireNonNull(
                document.getDisclosure(),
                "document의 disclosure는 필수입니다."
        );
        Objects.requireNonNull(disclosure.getId(), "저장된 disclosure가 필요합니다.");
        return disclosure;
    }

    private static void validateEvidenceOwner(
            DisclosureEvidence source,
            Disclosure disclosure,
            DisclosureDocument document,
            DisclosureSection section,
            DisclosureContentBlock contentBlock
    ) {
        if (!source.disclosureId().equals(disclosure.getId())
                || !source.disclosureDocumentId().equals(document.getId())
                || !source.receiptNo().equals(disclosure.getReceiptNo())) {
            throw new IllegalArgumentException(
                    "Evidence와 공시·문서·접수번호 관계가 다릅니다."
            );
        }
        UUID sectionId = section == null ? null : section.getId();
        UUID blockId = contentBlock == null ? null : contentBlock.getId();
        if (!Objects.equals(source.sectionId(), sectionId)
                || !Objects.equals(source.contentBlockId(), blockId)) {
            throw new IllegalArgumentException(
                    "Evidence와 Section·ContentBlock 관계가 다릅니다."
            );
        }
    }

    private static void validateFactOwner(
            DisclosureFact source,
            Disclosure disclosure,
            DisclosureDocument document
    ) {
        if (!source.disclosureId().equals(disclosure.getId())
                || !source.disclosureDocumentId().equals(document.getId())
                || !source.sourceReceiptNo().equals(disclosure.getReceiptNo())) {
            throw new IllegalArgumentException(
                    "Fact와 공시·문서·접수번호 관계가 다릅니다."
            );
        }
    }
}
