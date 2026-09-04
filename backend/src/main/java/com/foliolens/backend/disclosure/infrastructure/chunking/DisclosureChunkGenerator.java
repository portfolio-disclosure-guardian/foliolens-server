package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 문서 하나의 Section과 ContentBlock을 읽어
 * TEXT와 TABLE 검색 청크를 문서 전역 순서로 조립한다.
 *
 * 각 전용 Generator가 GeneratedChunkDraft를 생성하고,
 * 이 클래스가 원문 순서 정렬과 최종 chunkSequenceNo 확정을 담당한다.
 */
@Component
public class DisclosureChunkGenerator {

    // 한글·영문 등 Unicode 문자 또는 숫자가 하나라도 있으면 보존한다.
    // searchText의 섹션명/머리글이 아니라 청크 본문 전체만 판별한다.
    private static final Pattern BODY_LETTER_OR_NUMBER = Pattern.compile("[\\p{L}\\p{N}]");

    private static final Comparator<GeneratedChunkDraft> DRAFT_ORDER =
            Comparator.comparingInt(GeneratedChunkDraft::anchorBlockSequenceNo)
                    .thenComparingInt(GeneratedChunkDraft::anchorPartIndex)
                    .thenComparing(GeneratedChunkDraft::chunkType);

    private final DisclosureChunkingPolicy policy;
    private final SectionPathResolver sectionPathResolver;
    private final TextChunkGenerator textChunkGenerator;
    private final TableChunkGenerator tableChunkGenerator;

    public DisclosureChunkGenerator(
            DisclosureChunkingPolicy policy,
            SectionPathResolver sectionPathResolver,
            TextChunkGenerator textChunkGenerator,
            TableChunkGenerator tableChunkGenerator
    ) {
        this.policy = Objects.requireNonNull(
                policy,
                "policy는 필수입니다."
        );
        this.sectionPathResolver = Objects.requireNonNull(
                sectionPathResolver,
                "sectionPathResolver는 필수입니다."
        );
        this.textChunkGenerator = Objects.requireNonNull(
                textChunkGenerator,
                "textChunkGenerator는 필수입니다."
        );
        this.tableChunkGenerator = Objects.requireNonNull(
                tableChunkGenerator,
                "tableChunkGenerator는 필수입니다."
        );
    }

    /**
     * 문서의 Section과 Block으로 TEXT와 TABLE 청크를 생성한다.
     *
     * 입력 목록의 정렬 여부에 의존하지 않고 sequenceNo로 원문 순서를 복원한다.
     * Section 시작은 그 자체로 하드 경계이므로, 부모 Section의
     * 문단 사이에 자식 Section이 끼어 있어도 서로 합쳐지지 않는다.
     */
    public List<GeneratedDisclosureChunk> generateChunks(
            UUID documentId,
            List<DisclosureSection> sections,
            List<DisclosureContentBlock> blocks
    ) {
        Objects.requireNonNull(documentId, "documentId는 필수입니다.");
        Objects.requireNonNull(sections, "sections는 필수입니다.");
        Objects.requireNonNull(blocks, "blocks는 필수입니다.");

        validateSectionOwnership(documentId, sections);

        Map<UUID, String> sectionPaths = sectionPathResolver.resolveAll(sections);

        List<SourceEvent> sourceEvents = buildSourceEvents(
                documentId,
                sections,
                blocks,
                sectionPaths
        );

        List<List<DisclosureContentBlock>> blockSegments = splitIntoBlockSegments(sourceEvents);

        List<GeneratedChunkDraft> drafts = new ArrayList<>();

        for (List<DisclosureContentBlock> segment : blockSegments) {
            UUID sectionId = sectionIdOf(segment.getFirst());
            String sectionPath = sectionId == null
                    ? sectionPathResolver.preamblePath()
                    : sectionPaths.get(sectionId);

            drafts.addAll(
                    generateSegmentDrafts(
                            documentId,
                            sectionId,
                            sectionPath,
                            segment
                    )
            );
        }

        // 일반 표 안의 '-' 셀이나 의미 있는 청크의 일부는 수정하지 않는다.
        // 기호·공백뿐인 청크만 제외한 다음 연속된 최종 순번을 부여한다.
        drafts.removeIf(draft -> !BODY_LETTER_OR_NUMBER.matcher(draft.bodyText()).find());
        drafts.sort(DRAFT_ORDER);

        List<GeneratedDisclosureChunk> completed =
                new ArrayList<>(drafts.size());

        for (int index = 0; index < drafts.size(); index++) {
            completed.add(
                    drafts.get(index).complete(
                            index + 1,
                            policy
                    )
            );
        }

        return List.copyOf(completed);
    }

    /**
     * 같은 Section 구간에서 TEXT와 TABLE 청크 후보를 각각 생성한다.
     * 최종 원문 순서와 전역 청크 순번은 상위 generateChunks()에서 확정한다.
     */
    private List<GeneratedChunkDraft> generateSegmentDrafts(
            UUID documentId,
            UUID sectionId,
            String sectionPath,
            List<DisclosureContentBlock> blocks
    ) {
        List<GeneratedChunkDraft> result = new ArrayList<>();

        result.addAll(
                textChunkGenerator.generate(
                        documentId,
                        sectionId,
                        sectionPath,
                        blocks
                )
        );

        result.addAll(
                generateTableDrafts(
                        documentId,
                        sectionId,
                        sectionPath,
                        blocks
                )
        );

        return List.copyOf(result);
    }

    /**
     * TABLE 블록을 만났을 때 그 시점의 HEADING 문맥과 함께
     * TableChunkGenerator에 전달한다.
     *
     * HEADING 상태 전환은 TextChunkGenerator와 같은 규칙을 사용한다.
     */
    private List<GeneratedChunkDraft> generateTableDrafts(
            UUID documentId,
            UUID sectionId,
            String sectionPath,
            List<DisclosureContentBlock> blocks
    ) {
        List<GeneratedChunkDraft> result = new ArrayList<>();
        List<String> activeHeadings = new ArrayList<>();

        boolean headingContextUsed = false;
        boolean nonTextBoundaryAfterHeading = false;

        for (DisclosureContentBlock block : blocks) {
            switch (block.getBlockType()) {
                case HEADING -> {
                    if (headingContextUsed
                            || nonTextBoundaryAfterHeading) {
                        activeHeadings.clear();
                    }

                    String heading = block.getTextContent();

                    if (heading != null && !heading.isBlank()) {
                        activeHeadings.add(heading);
                    }

                    headingContextUsed = false;
                    nonTextBoundaryAfterHeading = false;
                }

                case PARAGRAPH -> {
                    if (block.getTextContent() != null
                            && !block.getTextContent().isBlank()) {
                        headingContextUsed = true;
                    }
                }

                case TABLE -> {
                    result.addAll(
                            tableChunkGenerator.generate(
                                    documentId,
                                    sectionId,
                                    sectionPath,
                                    List.copyOf(activeHeadings),
                                    block
                            )
                    );

                    nonTextBoundaryAfterHeading = true;
                }

                case IMAGE ->
                        nonTextBoundaryAfterHeading = true;

                case PAGE_BREAK -> {
                    // PAGE_BREAK는 제목 문맥을 변경하지 않는다.
                }
            }
        }

        return List.copyOf(result);
    }

    private void validateSectionOwnership(
            UUID documentId,
            List<DisclosureSection> sections
    ) {
        for (DisclosureSection section : sections) {
            Objects.requireNonNull(
                    section,
                    "Section 목록에는 null이 들어갈 수 없습니다."
            );

            UUID sectionId = Objects.requireNonNull(
                    section.getId(),
                    "저장되지 않은 Section은 청크로 만들 수 없습니다."
            );

            DisclosureDocument actualDocument = Objects.requireNonNull(
                    section.getDisclosureDocument(),
                    "Section의 DisclosureDocument는 필수입니다."
            );
            UUID actualDocumentId = Objects.requireNonNull(
                    actualDocument.getId(),
                    "Section의 DisclosureDocument가 저장되지 않았습니다."
            );

            if (!documentId.equals(actualDocumentId)) {
                throw new IllegalArgumentException(
                        "다른 문서의 Section이 포함되어 있습니다."
                                + " sectionId=" + sectionId
                );
            }

            if (section.getSequenceNo() < 1) {
                throw new IllegalArgumentException(
                        "Section sequenceNo는 1 이상이어야 합니다."
                                + " sectionId=" + sectionId
                );
            }
        }
    }

    private List<SourceEvent> buildSourceEvents(
            UUID documentId,
            List<DisclosureSection> sections,
            List<DisclosureContentBlock> blocks,
            Map<UUID, String> sectionPaths
    ) {
        List<SourceEvent> events = new ArrayList<>(sections.size() + blocks.size());
        Map<Integer, String> sequenceOwners = new HashMap<>();

        for (DisclosureSection section : sections) {
            registerSequence(
                    sequenceOwners,
                    section.getSequenceNo(),
                    "Section " + section.getId()
            );
            events.add(SourceEvent.sectionStart(section));
        }

        for (DisclosureContentBlock block : blocks) {
            validateBlock(
                    documentId,
                    block,
                    sectionPaths
            );
            registerSequence(
                    sequenceOwners,
                    block.getSequenceNo(),
                    "Block " + block.getId()
            );
            events.add(SourceEvent.block(block));
        }

        events.sort(Comparator.comparingInt(SourceEvent::sequenceNo));
        return List.copyOf(events);
    }

    private void validateBlock(
            UUID documentId,
            DisclosureContentBlock block,
            Map<UUID, String> sectionPaths
    ) {
        Objects.requireNonNull(
                block,
                "Block 목록에는 null이 들어갈 수 없습니다."
        );

        UUID blockId = Objects.requireNonNull(
                block.getId(),
                "저장되지 않은 Block은 청크로 만들 수 없습니다."
        );
        DisclosureDocument actualDocument = Objects.requireNonNull(
                block.getDisclosureDocument(),
                "Block의 DisclosureDocument는 필수입니다."
        );
        UUID actualDocumentId = Objects.requireNonNull(
                actualDocument.getId(),
                "Block의 DisclosureDocument가 저장되지 않았습니다."
        );

        if (!documentId.equals(actualDocumentId)) {
            throw new IllegalArgumentException(
                    "다른 문서의 Block이 포함되어 있습니다."
                            + " blockId=" + blockId
            );
        }

        if (block.getSequenceNo() < 1) {
            throw new IllegalArgumentException(
                    "Block sequenceNo는 1 이상이어야 합니다."
                            + " blockId=" + blockId
            );
        }

        UUID sectionId = sectionIdOf(block);

        if (sectionId != null && !sectionPaths.containsKey(sectionId)) {
            throw new IllegalStateException(
                    "Block이 입력 목록에 없는 Section을 참조합니다."
                            + " blockId=" + blockId
                            + ", sectionId=" + sectionId
            );
        }
    }

    private void registerSequence(
            Map<Integer, String> sequenceOwners,
            int sequenceNo,
            String owner
    ) {
        String previous = sequenceOwners.put(sequenceNo, owner);

        if (previous != null) {
            throw new IllegalArgumentException(
                    "문서 원문 sequenceNo가 중복되었습니다."
                            + " sequenceNo=" + sequenceNo
                            + ", first=" + previous
                            + ", second=" + owner
            );
        }
    }

    private List<List<DisclosureContentBlock>> splitIntoBlockSegments(
            List<SourceEvent> sourceEvents
    ) {
        List<List<DisclosureContentBlock>> result = new ArrayList<>();
        List<DisclosureContentBlock> current = new ArrayList<>();

        for (SourceEvent event : sourceEvents) {
            if (event.isSectionStart()) {
                flushSegment(current, result);
                continue;
            }

            DisclosureContentBlock block = event.block();

            if (!current.isEmpty()
                    && !Objects.equals(
                    sectionIdOf(current.getFirst()),
                    sectionIdOf(block)
            )) {
                flushSegment(current, result);
            }

            current.add(block);
        }

        flushSegment(current, result);
        return List.copyOf(result);
    }

    private void flushSegment(
            List<DisclosureContentBlock> current,
            List<List<DisclosureContentBlock>> result
    ) {
        if (current.isEmpty()) {
            return;
        }

        result.add(List.copyOf(current));
        current.clear();
    }

    private UUID sectionIdOf(DisclosureContentBlock block) {
        DisclosureSection section = block.getSection();

        if (section == null) {
            return null;
        }

        return Objects.requireNonNull(
                section.getId(),
                "저장되지 않은 Section을 참조하는 Block입니다."
        );
    }

    private record SourceEvent(
            int sequenceNo,
            DisclosureSection section,
            DisclosureContentBlock block
    ) {

        private static SourceEvent sectionStart(DisclosureSection section) {
            return new SourceEvent(
                    section.getSequenceNo(),
                    section,
                    null
            );
        }

        private static SourceEvent block(DisclosureContentBlock block) {
            return new SourceEvent(
                    block.getSequenceNo(),
                    null,
                    block
            );
        }

        private boolean isSectionStart() {
            return section != null;
        }
    }
}
