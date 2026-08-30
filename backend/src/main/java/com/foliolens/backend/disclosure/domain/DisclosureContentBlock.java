package com.foliolens.backend.disclosure.domain;

import com.foliolens.backend.global.basetime.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "disclosure_content_blocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name =
                                "uq_disclosure_content_blocks_document_sequence",
                        columnNames = {
                                "disclosure_document_id",
                                "sequence_no"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureContentBlock extends BaseTimeEntity {

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
    @JoinColumn(name = "section_id")
    private DisclosureSection section;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "block_type",
            nullable = false,
            length = 20
    )
    private DisclosureContentBlockType blockType;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "text_content", columnDefinition = "text")
    private String textContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "structured_content",
            columnDefinition = "jsonb"
    )
    private JsonNode structuredContent;

    @Column(name = "source_line_start", nullable = false)
    private int sourceLineStart;

    @Column(name = "source_line_end", nullable = false)
    private int sourceLineEnd;

    private DisclosureContentBlock(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            DisclosureContentBlockType blockType,
            int sequenceNo,
            String textContent,
            JsonNode structuredContent,
            int sourceLineStart,
            int sourceLineEnd
    ) {
        this.disclosureDocument = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );

        this.section = section;
        this.blockType = Objects.requireNonNull(
                blockType,
                "blockType은 필수입니다."
        );

        if (sequenceNo < 1) {
            throw new IllegalArgumentException(
                    "sequenceNo는 1 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);
        validatePayload(
                blockType,
                textContent,
                structuredContent
        );

        this.sequenceNo = sequenceNo;
        this.textContent = normalizeNullable(textContent);
        this.structuredContent = structuredContent;
        this.sourceLineStart = sourceLineStart;
        this.sourceLineEnd = sourceLineEnd;
    }

    public static DisclosureContentBlock text(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            DisclosureContentBlockType blockType,
            int sequenceNo,
            String textContent,
            int sourceLineStart,
            int sourceLineEnd
    ) {
        if (
                blockType != DisclosureContentBlockType.HEADING
                        && blockType
                        != DisclosureContentBlockType.PARAGRAPH
        ) {
            throw new IllegalArgumentException(
                    "text()는 HEADING 또는 PARAGRAPH만 지원합니다."
            );
        }

        return new DisclosureContentBlock(
                disclosureDocument,
                section,
                blockType,
                sequenceNo,
                textContent,
                null,
                sourceLineStart,
                sourceLineEnd
        );
    }

    public static DisclosureContentBlock structured(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            DisclosureContentBlockType blockType,
            int sequenceNo,
            JsonNode structuredContent,
            int sourceLineStart,
            int sourceLineEnd
    ) {
        if (
                blockType != DisclosureContentBlockType.TABLE
                        && blockType
                        != DisclosureContentBlockType.IMAGE
        ) {
            throw new IllegalArgumentException(
                    "structured()는 TABLE 또는 IMAGE만 지원합니다."
            );
        }

        return new DisclosureContentBlock(
                disclosureDocument,
                section,
                blockType,
                sequenceNo,
                null,
                structuredContent,
                sourceLineStart,
                sourceLineEnd
        );
    }

    public static DisclosureContentBlock pageBreak(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            int sequenceNo,
            int sourceLine
    ) {
        return new DisclosureContentBlock(
                disclosureDocument,
                section,
                DisclosureContentBlockType.PAGE_BREAK,
                sequenceNo,
                null,
                null,
                sourceLine,
                sourceLine
        );
    }

    private static void validatePayload(
            DisclosureContentBlockType type,
            String textContent,
            JsonNode structuredContent
    ) {
        switch (type) {
            case HEADING, PARAGRAPH -> {
                if (textContent == null || textContent.isBlank()) {
                    throw new IllegalArgumentException(
                            "텍스트 블록에는 textContent가 필수입니다."
                    );
                }

                if (structuredContent != null) {
                    throw new IllegalArgumentException(
                            "텍스트 블록은 JSONB 데이터를 가질 수 없습니다."
                    );
                }
            }

            case TABLE, IMAGE -> {
                if (structuredContent == null
                        || !structuredContent.isObject()) {
                    throw new IllegalArgumentException(
                            "구조 블록에는 JSON 객체가 필수입니다."
                    );
                }

                if (textContent != null) {
                    throw new IllegalArgumentException(
                            "구조 블록은 textContent를 가질 수 없습니다."
                    );
                }
            }

            case PAGE_BREAK -> {
                if (textContent != null || structuredContent != null) {
                    throw new IllegalArgumentException(
                            "PAGE_BREAK는 별도 내용을 가질 수 없습니다."
                    );
                }
            }
        }
    }

    private static void validateSourceLines(int start, int end) {
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
