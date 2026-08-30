package com.foliolens.backend.disclosure.domain;

import com.foliolens.backend.global.basetime.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "disclosure_chunk_sources",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_disclosure_chunk_sources_chunk_order",
                        columnNames = {
                                "disclosure_chunk_id",
                                "source_order"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureChunkSource extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "disclosure_chunk_id",
            nullable = false
    )
    private DisclosureChunk disclosureChunk;

    /*
     * V7의 복합 FK에서 청크와 원본 Block이
     * 같은 문서인지 보장하기 위해 필요하다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "disclosure_document_id",
            nullable = false
    )
    private DisclosureDocument disclosureDocument;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "content_block_id",
            nullable = false
    )
    private DisclosureContentBlock contentBlock;

    @Column(name = "source_order", nullable = false)
    private int sourceOrder;

    @Column(name = "block_sequence_no", nullable = false)
    private int blockSequenceNo;

    @Column(name = "source_line_start", nullable = false)
    private int sourceLineStart;

    @Column(name = "source_line_end", nullable = false)
    private int sourceLineEnd;

    @Column(name = "table_nesting_path")
    private String tableNestingPath;

    @Column(name = "table_row_index_start")
    private Integer tableRowIndexStart;

    @Column(name = "table_row_index_end")
    private Integer tableRowIndexEnd;

    private DisclosureChunkSource(
            DisclosureChunk disclosureChunk,
            DisclosureContentBlock contentBlock,
            int sourceOrder,
            int blockSequenceNo,
            int sourceLineStart,
            int sourceLineEnd,
            String tableNestingPath,
            Integer tableRowIndexStart,
            Integer tableRowIndexEnd
    ) {
        this.disclosureChunk = Objects.requireNonNull(
                disclosureChunk,
                "disclosureChunk는 필수입니다."
        );

        this.disclosureDocument =
                Objects.requireNonNull(
                        disclosureChunk.getDisclosureDocument(),
                        "청크의 DisclosureDocument는 필수입니다."
                );

        this.contentBlock = Objects.requireNonNull(
                contentBlock,
                "contentBlock은 필수입니다."
        );

        validateBlockOwner(
                disclosureDocument,
                contentBlock
        );

        if (sourceOrder < 1) {
            throw new IllegalArgumentException(
                    "sourceOrder는 1 이상이어야 합니다."
            );
        }

        if (blockSequenceNo < 1) {
            throw new IllegalArgumentException(
                    "blockSequenceNo는 1 이상이어야 합니다."
            );
        }

        if (blockSequenceNo != contentBlock.getSequenceNo()) {
            throw new IllegalArgumentException(
                    "blockSequenceNo가 ContentBlock의 순서와 다릅니다."
            );
        }

        validateSourceLines(
                sourceLineStart,
                sourceLineEnd
        );

        validateTableLocation(
                tableNestingPath,
                tableRowIndexStart,
                tableRowIndexEnd
        );

        this.sourceOrder = sourceOrder;
        this.blockSequenceNo = blockSequenceNo;
        this.sourceLineStart = sourceLineStart;
        this.sourceLineEnd = sourceLineEnd;
        this.tableNestingPath =
                normalizeNullable(tableNestingPath);
        this.tableRowIndexStart = tableRowIndexStart;
        this.tableRowIndexEnd = tableRowIndexEnd;
    }

    static DisclosureChunkSource create(
            DisclosureChunk disclosureChunk,
            DisclosureContentBlock contentBlock,
            int sourceOrder,
            int blockSequenceNo,
            int sourceLineStart,
            int sourceLineEnd,
            String tableNestingPath,
            Integer tableRowIndexStart,
            Integer tableRowIndexEnd
    ) {
        return new DisclosureChunkSource(
                disclosureChunk,
                contentBlock,
                sourceOrder,
                blockSequenceNo,
                sourceLineStart,
                sourceLineEnd,
                tableNestingPath,
                tableRowIndexStart,
                tableRowIndexEnd
        );
    }

    private static void validateBlockOwner(
            DisclosureDocument document,
            DisclosureContentBlock contentBlock
    ) {
        UUID documentId = Objects.requireNonNull(
                document.getId(),
                "저장되지 않은 DisclosureDocument입니다."
        );

        DisclosureDocument blockDocument =
                Objects.requireNonNull(
                        contentBlock.getDisclosureDocument(),
                        "ContentBlock의 DisclosureDocument는 필수입니다."
                );

        UUID blockDocumentId = Objects.requireNonNull(
                blockDocument.getId(),
                "ContentBlock의 DisclosureDocument가 저장되지 않았습니다."
        );

        if (!documentId.equals(blockDocumentId)) {
            throw new IllegalArgumentException(
                    "다른 문서의 ContentBlock을 출처로 연결할 수 없습니다."
            );
        }
    }

    private static void validateSourceLines(
            int start,
            int end
    ) {
        if (start < -1 || end < -1) {
            throw new IllegalArgumentException(
                    "원문 행 번호는 -1 이상이어야 합니다."
            );
        }

        if (start != -1 && end != -1 && end < start) {
            throw new IllegalArgumentException(
                    "원문 종료 행은 시작 행보다 앞설 수 없습니다."
            );
        }
    }

    private static void validateTableLocation(
            String nestingPath,
            Integer rowStart,
            Integer rowEnd
    ) {
        if ((rowStart == null) != (rowEnd == null)) {
            throw new IllegalArgumentException(
                    "표 시작 행과 종료 행은 함께 존재해야 합니다."
            );
        }

        if (nestingPath != null
                && !nestingPath.isBlank()
                && rowStart == null) {
            throw new IllegalArgumentException(
                    "중첩 표 경로가 있으면 표 행 범위도 필요합니다."
            );
        }

        if (rowStart == null) {
            return;
        }

        if (rowStart < 0 || rowEnd < rowStart) {
            throw new IllegalArgumentException(
                    "표 행 범위가 올바르지 않습니다."
            );
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }
}