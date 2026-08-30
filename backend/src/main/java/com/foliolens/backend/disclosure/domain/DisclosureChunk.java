package com.foliolens.backend.disclosure.domain;

import com.foliolens.backend.global.basetime.BaseTimeEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "disclosure_chunks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_disclosure_chunks_document_sequence",
                        columnNames = {
                                "disclosure_document_id",
                                "chunk_sequence_no"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureChunk extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "disclosure_document_id",
            nullable = false
    )
    private DisclosureDocument disclosureDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private DisclosureSection section;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            name = "chunk_type",
            nullable = false,
            length = 30
    )
    private DisclosureChunkType chunkType;

    @Column(name = "chunk_sequence_no", nullable = false)
    private int chunkSequenceNo;

    @NotNull
    @Column(name = "section_path", nullable = false)
    private String sectionPath;

    @NotBlank
    @Column(
            name = "body_text",
            nullable = false,
            columnDefinition = "text"
    )
    private String bodyText;

    @NotBlank
    @Column(
            name = "search_text",
            nullable = false,
            columnDefinition = "text"
    )
    private String searchText;

    /*
     * PostgreSQL GENERATED ALWAYS 컬럼이므로
     * JPA는 값을 INSERT·UPDATE하지 않고 조회만 한다.
     */
    @Column(
            name = "body_character_count",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Integer bodyCharacterCount;

    @Column(
            name = "search_character_count",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Integer searchCharacterCount;

    @NotBlank
    @Size(max = 100)
    @Column(
            name = "generator_name",
            nullable = false,
            length = 100
    )
    private String generatorName;

    @NotBlank
    @Size(max = 100)
    @Column(
            name = "generator_version",
            nullable = false,
            length = 100
    )
    private String generatorVersion;

    @Getter(AccessLevel.NONE)
    @OneToMany(
            mappedBy = "disclosureChunk",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("sourceOrder ASC")
    private List<DisclosureChunkSource> sources = new ArrayList<>();

    private DisclosureChunk(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            DisclosureChunkType chunkType,
            int chunkSequenceNo,
            String sectionPath,
            String bodyText,
            String searchText,
            String generatorName,
            String generatorVersion
    ) {
        this.disclosureDocument = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );

        validateSectionOwner(disclosureDocument, section);

        this.section = section;
        this.chunkType = Objects.requireNonNull(
                chunkType,
                "chunkType은 필수입니다."
        );

        if (chunkSequenceNo < 1) {
            throw new IllegalArgumentException(
                    "chunkSequenceNo는 1 이상이어야 합니다."
            );
        }

        this.chunkSequenceNo = chunkSequenceNo;
        this.sectionPath = normalizePath(sectionPath);
        this.bodyText = requireText(bodyText, "bodyText");
        this.searchText = requireText(searchText, "searchText");
        this.generatorName = requireLimitedText(
                generatorName,
                "generatorName",
                100
        );
        this.generatorVersion = requireLimitedText(
                generatorVersion,
                "generatorVersion",
                100
        );
    }

    public static DisclosureChunk create(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            DisclosureChunkType chunkType,
            int chunkSequenceNo,
            String sectionPath,
            String bodyText,
            String searchText,
            String generatorName,
            String generatorVersion
    ) {
        return new DisclosureChunk(
                disclosureDocument,
                section,
                chunkType,
                chunkSequenceNo,
                sectionPath,
                bodyText,
                searchText,
                generatorName,
                generatorVersion
        );
    }

    public void addSource(
            DisclosureContentBlock contentBlock,
            int sourceOrder,
            int blockSequenceNo,
            int sourceLineStart,
            int sourceLineEnd,
            String tableNestingPath,
            Integer tableRowIndexStart,
            Integer tableRowIndexEnd
    ) {
        int expectedOrder = sources.size() + 1;

        if (sourceOrder != expectedOrder) {
            throw new IllegalArgumentException(
                    "sourceOrder는 1부터 연속되어야 합니다."
                            + " expected=" + expectedOrder
                            + ", actual=" + sourceOrder
            );
        }

        sources.add(
                DisclosureChunkSource.create(
                        this,
                        contentBlock,
                        sourceOrder,
                        blockSequenceNo,
                        sourceLineStart,
                        sourceLineEnd,
                        tableNestingPath,
                        tableRowIndexStart,
                        tableRowIndexEnd
                )
        );
    }

    public List<DisclosureChunkSource> getSources() {
        return List.copyOf(sources);
    }

    @PrePersist
    @PreUpdate
    private void validateSources() {
        if (sources.isEmpty()) {
            throw new IllegalStateException(
                    "청크에는 원본 출처가 하나 이상 필요합니다."
            );
        }
    }

    private static void validateSectionOwner(
            DisclosureDocument document,
            DisclosureSection section
    ) {
        if (section == null) {
            return;
        }

        UUID documentId = Objects.requireNonNull(
                document.getId(),
                "저장되지 않은 DisclosureDocument입니다."
        );

        DisclosureDocument sectionDocument =
                Objects.requireNonNull(
                        section.getDisclosureDocument(),
                        "Section의 DisclosureDocument는 필수입니다."
                );

        UUID sectionDocumentId = Objects.requireNonNull(
                sectionDocument.getId(),
                "Section의 DisclosureDocument가 저장되지 않았습니다."
        );

        if (!documentId.equals(sectionDocumentId)) {
            throw new IllegalArgumentException(
                    "다른 문서의 Section을 청크에 연결할 수 없습니다."
            );
        }
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.strip();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }

        return value.strip();
    }

    private static String requireLimitedText(
            String value,
            String fieldName,
            int maxLength
    ) {
        String normalized = requireText(value, fieldName);

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은 " + maxLength
                            + "자를 초과할 수 없습니다."
            );
        }

        return normalized;
    }
}