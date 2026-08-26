package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureChunk;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.infrastructure.chunking.GeneratedChunkSource;
import com.foliolens.backend.disclosure.infrastructure.chunking.GeneratedDisclosureChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class GeneratedDisclosureChunkEntityMapper {

    public List<DisclosureChunk> toEntities(
            DisclosureDocument document,
            List<DisclosureSection> sections,
            List<DisclosureContentBlock> blocks,
            List<GeneratedDisclosureChunk> generatedChunks
    ) {
        Objects.requireNonNull(
                document,
                "document는 필수입니다."
        );
        Objects.requireNonNull(
                sections,
                "sections는 필수입니다."
        );
        Objects.requireNonNull(
                blocks,
                "blocks는 필수입니다."
        );
        Objects.requireNonNull(
                generatedChunks,
                "generatedChunks는 필수입니다."
        );

        UUID documentId = Objects.requireNonNull(
                document.getId(),
                "저장되지 않은 DisclosureDocument입니다."
        );

        Map<UUID, DisclosureSection> sectionsById =
                indexSections(documentId, sections);

        Map<UUID, DisclosureContentBlock> blocksById =
                indexBlocks(documentId, blocks);

        validateChunkSequence(generatedChunks);

        List<DisclosureChunk> result =
                new ArrayList<>(generatedChunks.size());

        for (GeneratedDisclosureChunk generatedChunk
                : generatedChunks) {

            result.add(
                    toEntity(
                            document,
                            documentId,
                            sectionsById,
                            blocksById,
                            generatedChunk
                    )
            );
        }

        return List.copyOf(result);
    }

    private DisclosureChunk toEntity(
            DisclosureDocument document,
            UUID documentId,
            Map<UUID, DisclosureSection> sectionsById,
            Map<UUID, DisclosureContentBlock> blocksById,
            GeneratedDisclosureChunk generatedChunk
    ) {
        Objects.requireNonNull(
                generatedChunk,
                "generatedChunks에는 null이 들어갈 수 없습니다."
        );

        if (!documentId.equals(generatedChunk.documentId())) {
            throw new IllegalArgumentException(
                    "다른 문서의 GeneratedChunk가 포함되어 있습니다."
                            + " chunkSequenceNo="
                            + generatedChunk.chunkSequenceNo()
            );
        }

        DisclosureSection section = resolveSection(
                sectionsById,
                generatedChunk.sectionId()
        );

        DisclosureChunk chunk = DisclosureChunk.create(
                document,
                section,
                generatedChunk.chunkType(),
                generatedChunk.chunkSequenceNo(),
                generatedChunk.sectionPath(),
                generatedChunk.bodyText(),
                generatedChunk.searchText(),
                generatedChunk.generatorName(),
                generatedChunk.generatorVersion()
        );

        addSources(
                chunk,
                blocksById,
                generatedChunk.sources()
        );

        return chunk;
    }

    private void addSources(
            DisclosureChunk chunk,
            Map<UUID, DisclosureContentBlock> blocksById,
            List<GeneratedChunkSource> generatedSources
    ) {
        for (int index = 0; index < generatedSources.size(); index++) {

            GeneratedChunkSource generatedSource = generatedSources.get(index);

            DisclosureContentBlock contentBlock =
                    blocksById.get(generatedSource.contentBlockId());

            if (contentBlock == null) {
                throw new IllegalStateException(
                        "청크 출처가 입력 목록에 없는 ContentBlock을 참조합니다."
                                + " contentBlockId="
                                + generatedSource.contentBlockId()
                );
            }

            /*
             * GeneratedChunkSource에는 sourceOrder가 없으므로
             * 목록 순서를 기준으로 1부터 부여한다.
             */
            chunk.addSource(
                    contentBlock,
                    index + 1,
                    generatedSource.blockSequenceNo(),
                    generatedSource.sourceLineStart(),
                    generatedSource.sourceLineEnd(),
                    generatedSource.tableNestingPath(),
                    generatedSource.tableRowIndexStart(),
                    generatedSource.tableRowIndexEnd()
            );
        }
    }

    private DisclosureSection resolveSection(
            Map<UUID, DisclosureSection> sectionsById,
            UUID sectionId
    ) {
        /*
         * sectionId가 null이면 문서 서두 청크다.
         */
        if (sectionId == null) {
            return null;
        }

        DisclosureSection section = sectionsById.get(sectionId);

        if (section == null) {
            throw new IllegalStateException(
                    "청크가 입력 목록에 없는 Section을 참조합니다."
                            + " sectionId=" + sectionId
            );
        }

        return section;
    }

    private Map<UUID, DisclosureSection> indexSections(
            UUID documentId,
            List<DisclosureSection> sections
    ) {
        Map<UUID, DisclosureSection> result = new HashMap<>();

        for (DisclosureSection section : sections) {
            Objects.requireNonNull(
                    section,
                    "sections에는 null이 들어갈 수 없습니다."
            );

            UUID sectionId = Objects.requireNonNull(
                    section.getId(),
                    "저장되지 않은 Section입니다."
            );

            UUID actualDocumentId = Objects.requireNonNull(
                    section.getDisclosureDocument(),
                    "Section의 DisclosureDocument는 필수입니다."
            ).getId();

            if (!documentId.equals(actualDocumentId)) {
                throw new IllegalArgumentException(
                        "다른 문서의 Section이 포함되어 있습니다."
                                + " sectionId=" + sectionId
                );
            }

            DisclosureSection previous = result.put(sectionId, section);

            if (previous != null) {
                throw new IllegalArgumentException(
                        "중복 Section ID가 존재합니다."
                                + " sectionId=" + sectionId
                );
            }
        }

        return Map.copyOf(result);
    }

    private Map<UUID, DisclosureContentBlock> indexBlocks(
            UUID documentId,
            List<DisclosureContentBlock> blocks
    ) {
        Map<UUID, DisclosureContentBlock> result = new HashMap<>();

        for (DisclosureContentBlock block : blocks) {
            Objects.requireNonNull(
                    block,
                    "blocks에는 null이 들어갈 수 없습니다."
            );

            UUID blockId = Objects.requireNonNull(
                    block.getId(),
                    "저장되지 않은 ContentBlock입니다."
            );

            UUID actualDocumentId = Objects.requireNonNull(
                    block.getDisclosureDocument(),
                    "ContentBlock의 DisclosureDocument는 필수입니다."
            ).getId();

            if (!documentId.equals(actualDocumentId)) {
                throw new IllegalArgumentException(
                        "다른 문서의 ContentBlock이 포함되어 있습니다."
                                + " blockId=" + blockId
                );
            }

            DisclosureContentBlock previous = result.put(blockId, block);

            if (previous != null) {
                throw new IllegalArgumentException(
                        "중복 ContentBlock ID가 존재합니다."
                                + " blockId=" + blockId
                );
            }
        }

        return Map.copyOf(result);
    }

    private void validateChunkSequence(
            List<GeneratedDisclosureChunk> generatedChunks
    ) {
        for (int index = 0; index < generatedChunks.size(); index++) {

            GeneratedDisclosureChunk chunk =
                    Objects.requireNonNull(
                            generatedChunks.get(index),
                            "generatedChunks에는 null이 들어갈 수 없습니다."
                    );

            int expectedSequence = index + 1;

            if (chunk.chunkSequenceNo()
                    != expectedSequence) {
                throw new IllegalArgumentException(
                        "청크 순번은 1부터 연속되어야 합니다."
                                + " expected=" + expectedSequence
                                + ", actual="
                                + chunk.chunkSequenceNo()
                );
            }
        }
    }
}