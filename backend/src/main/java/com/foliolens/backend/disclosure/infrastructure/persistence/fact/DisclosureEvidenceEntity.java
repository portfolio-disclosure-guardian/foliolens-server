package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "disclosure_evidences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureEvidenceEntity extends BaseTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disclosure_id", nullable = false)
    private Disclosure disclosure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disclosure_document_id", nullable = false)
    private DisclosureDocument disclosureDocument;

    @Column(name = "receipt_no", nullable = false, length = 14)
    private String receiptNo;

    @Column(name = "document_name", nullable = false, length = 500)
    private String documentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_file_role", nullable = false, length = 30)
    private DisclosureDocumentRole documentFileRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_document_role", nullable = false, length = 30)
    private EventDocumentRole eventDocumentRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private DisclosureSection section;

    @Column(name = "section_path", nullable = false, columnDefinition = "text")
    private String sectionPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_block_id")
    private DisclosureContentBlock contentBlock;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 30)
    private EvidenceBlockType blockType;

    @Column(name = "table_index_or_name", columnDefinition = "text")
    private String tableIndexOrName;

    @Column(name = "source_line_start", nullable = false)
    private int sourceLineStart;

    @Column(name = "source_line_end", nullable = false)
    private int sourceLineEnd;

    @Column(name = "table_nesting_path", columnDefinition = "text")
    private String tableNestingPath;

    @Column(name = "table_row_index")
    private Integer tableRowIndex;

    @Column(name = "table_cell_index")
    private Integer tableCellIndex;

    @Column(name = "source_text", nullable = false, columnDefinition = "text")
    private String sourceText;

    @Column(name = "row_label", columnDefinition = "text")
    private String rowLabel;

    @Column(name = "column_label", columnDefinition = "text")
    private String columnLabel;

    @Column(name = "raw_value", columnDefinition = "text")
    private String rawValue;

    @Column(name = "raw_unit", columnDefinition = "text")
    private String rawUnit;

    @Column(name = "note_text", columnDefinition = "text")
    private String noteText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EvidenceStatus status;

    DisclosureEvidenceEntity(
            UUID id,
            Disclosure disclosure,
            DisclosureDocument disclosureDocument,
            String receiptNo,
            String documentName,
            DisclosureDocumentRole documentFileRole,
            EventDocumentRole eventDocumentRole,
            DisclosureSection section,
            String sectionPath,
            DisclosureContentBlock contentBlock,
            EvidenceBlockType blockType,
            String tableIndexOrName,
            int sourceLineStart,
            int sourceLineEnd,
            String tableNestingPath,
            Integer tableRowIndex,
            Integer tableCellIndex,
            String sourceText,
            String rowLabel,
            String columnLabel,
            String rawValue,
            String rawUnit,
            String noteText,
            EvidenceStatus status
    ) {
        this.id = Objects.requireNonNull(id, "evidence id는 필수입니다.");
        this.disclosure = Objects.requireNonNull(disclosure, "disclosure는 필수입니다.");
        this.disclosureDocument = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );
        this.receiptNo = requireText(receiptNo, "receiptNo");
        this.documentName = requireText(documentName, "documentName");
        this.documentFileRole = Objects.requireNonNull(
                documentFileRole,
                "documentFileRole은 필수입니다."
        );
        this.eventDocumentRole = Objects.requireNonNull(
                eventDocumentRole,
                "eventDocumentRole은 필수입니다."
        );
        this.section = section;
        this.sectionPath = sectionPath == null ? "" : sectionPath.strip();
        this.contentBlock = contentBlock;
        this.blockType = Objects.requireNonNull(blockType, "blockType은 필수입니다.");
        this.tableIndexOrName = normalizeOptional(tableIndexOrName);
        this.sourceLineStart = sourceLineStart;
        this.sourceLineEnd = sourceLineEnd;
        this.tableNestingPath = normalizeOptional(tableNestingPath);
        this.tableRowIndex = tableRowIndex;
        this.tableCellIndex = tableCellIndex;
        this.sourceText = requireText(sourceText, "sourceText");
        this.rowLabel = normalizeOptional(rowLabel);
        this.columnLabel = normalizeOptional(columnLabel);
        this.rawValue = normalizeOptional(rawValue);
        this.rawUnit = normalizeOptional(rawUnit);
        this.noteText = normalizeOptional(noteText);
        this.status = Objects.requireNonNull(status, "status는 필수입니다.");
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
