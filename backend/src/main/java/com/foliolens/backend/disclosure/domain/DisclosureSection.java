package com.foliolens.backend.disclosure.domain;

import com.foliolens.backend.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "disclosure_sections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_disclosure_sections_document_sequence",
                        columnNames = {
                                "disclosure_document_id",
                                "sequence_no"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureSection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "disclosure_document_id",
            nullable = false
    )
    private DisclosureDocument disclosureDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_section_id")
    private DisclosureSection parentSection;

    @Column(name = "section_level", nullable = false)
    private int sectionLevel;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "source_line_start", nullable = false)
    private int sourceLineStart;

    @Column(name = "source_line_end", nullable = false)
    private int sourceLineEnd;

    private DisclosureSection(
            DisclosureDocument disclosureDocument,
            DisclosureSection parentSection,
            int sectionLevel,
            int sequenceNo,
            String title,
            int sourceLineStart,
            int sourceLineEnd
    ) {
        this.disclosureDocument = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );

        if (sectionLevel < 1) {
            throw new IllegalArgumentException(
                    "sectionLevel은 1 이상이어야 합니다."
            );
        }

        if (sequenceNo < 1) {
            throw new IllegalArgumentException(
                    "sequenceNo는 1 이상이어야 합니다."
            );
        }

        validateSourceLines(
                sourceLineStart,
                sourceLineEnd
        );

        this.parentSection = parentSection;
        this.sectionLevel = sectionLevel;
        this.sequenceNo = sequenceNo;
        this.title = normalizeNullable(title);
        this.sourceLineStart = sourceLineStart;
        this.sourceLineEnd = sourceLineEnd;
    }

    public static DisclosureSection create(
            DisclosureDocument disclosureDocument,
            DisclosureSection parentSection,
            int sectionLevel,
            int sequenceNo,
            String title,
            int sourceLineStart,
            int sourceLineEnd
    ) {
        return new DisclosureSection(
                disclosureDocument,
                parentSection,
                sectionLevel,
                sequenceNo,
                title,
                sourceLineStart,
                sourceLineEnd
        );
    }

    private static void validateSourceLines(
            int start,
            int end
    ) {
        if (start < -1 || end < -1) {
            throw new IllegalArgumentException(
                    "원문 행은 -1 이상이어야 합니다."
            );
        }

        if (start != -1 && end != -1 && end < start) {
            throw new IllegalArgumentException(
                    "원문 종료 행은 시작 행보다 앞설 수 없습니다."
            );
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
