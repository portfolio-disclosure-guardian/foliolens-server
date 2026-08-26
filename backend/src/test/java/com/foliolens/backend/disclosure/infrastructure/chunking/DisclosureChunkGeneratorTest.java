package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureChunkGeneratorTest {

    private static final UUID DOCUMENT_ID = new UUID(10, 10);

    private DisclosureDocument document;
    private TableChunkGenerator tableChunkGenerator;
    private DisclosureChunkGenerator generator;

    @BeforeEach
    void setUp() {
        document = document(DOCUMENT_ID);

        DisclosureChunkingPolicy policy =
                DisclosureChunkingPolicy.dartXmlV1();
        ChunkTextNormalizer normalizer = new ChunkTextNormalizer();
        tableChunkGenerator = mock(TableChunkGenerator.class);

        when(tableChunkGenerator.generate(
                any(UUID.class),
                nullable(UUID.class),
                anyString(),
                anyList(),
                any(DisclosureContentBlock.class)
        )).thenReturn(List.of());

        generator = new DisclosureChunkGenerator(
                policy,
                new SectionPathResolver(),
                new TextChunkGenerator(
                        policy,
                        normalizer,
                        new SentenceBoundarySplitter()
                ),
                tableChunkGenerator
        );
    }

    @Test
    void generatesPreambleAndNestedSectionChunksInDocumentOrder() {
        DisclosureSection root = section(
                2,
                1,
                "II. 사업의 내용",
                null,
                document
        );
        DisclosureSection child = section(
                5,
                2,
                "신규 시설투자",
                root,
                document
        );

        DisclosureContentBlock preamble = block(
                1,
                DisclosureContentBlockType.PARAGRAPH,
                "문서 서두입니다.",
                null,
                document
        );
        DisclosureContentBlock rootBeforeChild = block(
                3,
                DisclosureContentBlockType.PARAGRAPH,
                "자식 Section 앞의 부모 본문입니다.",
                root,
                document
        );
        DisclosureContentBlock tableBoundary = block(
                4,
                DisclosureContentBlockType.TABLE,
                null,
                root,
                document
        );
        DisclosureContentBlock childHeading = block(
                6,
                DisclosureContentBlockType.HEADING,
                "투자 목적",
                child,
                document
        );
        DisclosureContentBlock childParagraph = block(
                7,
                DisclosureContentBlockType.PARAGRAPH,
                "생산능력 확대를 위한 투자입니다.",
                child,
                document
        );
        DisclosureContentBlock rootAfterChild = block(
                8,
                DisclosureContentBlockType.PARAGRAPH,
                "자식 Section 뒤의 부모 본문입니다.",
                root,
                document
        );

        List<GeneratedDisclosureChunk> result =
                generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(child, root),
                        List.of(
                                rootAfterChild,
                                childParagraph,
                                tableBoundary,
                                preamble,
                                childHeading,
                                rootBeforeChild
                        )
                );

        assertEquals(4, result.size());
        assertEquals(
                List.of(1, 2, 3, 4),
                result.stream()
                        .map(GeneratedDisclosureChunk::chunkSequenceNo)
                        .toList()
        );
        assertEquals(
                List.of(1, 3, 6, 8),
                result.stream()
                        .map(GeneratedDisclosureChunk::firstSourceSequenceNo)
                        .toList()
        );
        assertEquals(
                List.of(
                        "문서 서두",
                        "II. 사업의 내용",
                        "II. 사업의 내용 > 신규 시설투자",
                        "II. 사업의 내용"
                ),
                result.stream()
                        .map(GeneratedDisclosureChunk::sectionPath)
                        .toList()
        );
        assertEquals(null, result.get(0).sectionId());
        assertEquals(root.getId(), result.get(1).sectionId());
        assertEquals(child.getId(), result.get(2).sectionId());
        assertEquals(root.getId(), result.get(3).sectionId());
        assertEquals(
                "[II. 사업의 내용 > 신규 시설투자]\n"
                        + "소제목: 투자 목적\n"
                        + "생산능력 확대를 위한 투자입니다.",
                result.get(2).searchText()
        );
        assertEquals(
                List.of(6, 7),
                result.get(2).sources().stream()
                        .map(GeneratedChunkSource::blockSequenceNo)
                        .toList()
        );
        assertEquals(
                "dart-xml-chunk-v1",
                result.getFirst().generatorVersion()
        );
    }

    @Test
    void doesNotMergeParentParagraphsAcrossEmptyChildSection() {
        DisclosureSection root = section(
                1,
                1,
                "부모 Section",
                null,
                document
        );
        DisclosureSection emptyChild = section(
                3,
                2,
                "본문이 없는 자식 Section",
                root,
                document
        );

        List<GeneratedDisclosureChunk> result =
                generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(root, emptyChild),
                        List.of(
                                block(
                                        2,
                                        DisclosureContentBlockType.PARAGRAPH,
                                        "자식 Section 앞의 문단",
                                        root,
                                        document
                                ),
                                block(
                                        4,
                                        DisclosureContentBlockType.PARAGRAPH,
                                        "자식 Section 뒤의 문단",
                                        root,
                                        document
                                )
                        )
                );

        assertEquals(2, result.size());
        assertEquals(
                "자식 Section 앞의 문단",
                result.get(0).bodyText()
        );
        assertEquals(
                "자식 Section 뒤의 문단",
                result.get(1).bodyText()
        );
    }

    @Test
    void returnsEmptyWhenDocumentHasOnlyImageAndPageBreak() {
        DisclosureSection section = section(
                1,
                1,
                "검색 가능한 내용이 없는 Section",
                null,
                document
        );

        List<GeneratedDisclosureChunk> result =
                generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(section),
                        List.of(
                                block(
                                        2,
                                        DisclosureContentBlockType.IMAGE,
                                        null,
                                        section,
                                        document
                                ),
                                block(
                                        3,
                                        DisclosureContentBlockType.PAGE_BREAK,
                                        null,
                                        section,
                                        document
                                )
                        )
                );

        assertEquals(List.of(), result);
    }

    @Test
    void combinesTextAndTableChunksInDocumentOrderWithHeadingContext() {
        DisclosureSection section = section(
                1,
                1,
                "II. 사업의 내용",
                null,
                document
        );
        DisclosureContentBlock heading = block(
                2,
                DisclosureContentBlockType.HEADING,
                "주요 실적",
                section,
                document
        );
        DisclosureContentBlock beforeTable = block(
                3,
                DisclosureContentBlockType.PARAGRAPH,
                "표 앞의 본문입니다.",
                section,
                document
        );
        DisclosureContentBlock table = block(
                4,
                DisclosureContentBlockType.TABLE,
                null,
                section,
                document
        );
        DisclosureContentBlock afterTable = block(
                5,
                DisclosureContentBlockType.PARAGRAPH,
                "표 뒤의 본문입니다.",
                section,
                document
        );
        UUID sectionId = section.getId();

        GeneratedChunkDraft tableDraft = new GeneratedChunkDraft(
                DOCUMENT_ID,
                sectionId,
                "II. 사업의 내용",
                DisclosureChunkType.TABLE,
                table.getSequenceNo(),
                0,
                "구분 | 금액\n매출 | 100억원",
                "[II. 사업의 내용]\n"
                        + "소제목: 주요 실적\n"
                        + "구분 | 금액\n매출 | 100억원",
                List.of(
                        GeneratedChunkSource.tableRows(
                                table.getId(),
                                table.getSequenceNo(),
                                table.getSourceLineStart(),
                                table.getSourceLineEnd(),
                                null,
                                0,
                                1
                        )
                )
        );

        when(tableChunkGenerator.generate(
                eq(DOCUMENT_ID),
                eq(sectionId),
                eq("II. 사업의 내용"),
                eq(List.of("주요 실적")),
                same(table)
        )).thenReturn(List.of(tableDraft));

        List<GeneratedDisclosureChunk> result =
                generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(section),
                        List.of(
                                afterTable,
                                table,
                                heading,
                                beforeTable
                        )
                );

        assertEquals(3, result.size());
        assertEquals(
                List.of(
                        DisclosureChunkType.TEXT,
                        DisclosureChunkType.TABLE,
                        DisclosureChunkType.TEXT
                ),
                result.stream()
                        .map(GeneratedDisclosureChunk::chunkType)
                        .toList()
        );
        assertEquals(
                List.of(1, 2, 3),
                result.stream()
                        .map(GeneratedDisclosureChunk::chunkSequenceNo)
                        .toList()
        );
        assertEquals("표 앞의 본문입니다.", result.get(0).bodyText());
        assertEquals(
                "구분 | 금액\n매출 | 100억원",
                result.get(1).bodyText()
        );
        assertEquals("표 뒤의 본문입니다.", result.get(2).bodyText());

        verify(tableChunkGenerator).generate(
                DOCUMENT_ID,
                sectionId,
                "II. 사업의 내용",
                List.of("주요 실적"),
                table
        );
    }

    @Test
    void rejectsSectionOrBlockFromAnotherDocument() {
        DisclosureDocument otherDocument = document(new UUID(99, 99));
        DisclosureSection otherSection = section(
                1,
                1,
                "다른 문서 Section",
                null,
                otherDocument
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(otherSection),
                        List.of()
                )
        );

        DisclosureContentBlock otherBlock = block(
                1,
                DisclosureContentBlockType.PARAGRAPH,
                "다른 문서 본문",
                null,
                otherDocument
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(),
                        List.of(otherBlock)
                )
        );
    }

    @Test
    void rejectsUnknownSectionAndDuplicateGlobalSequence() {
        DisclosureSection listedSection = section(
                1,
                1,
                "입력 Section",
                null,
                document
        );
        DisclosureSection unknownSection = section(
                2,
                2,
                "누락된 Section",
                null,
                document
        );
        DisclosureContentBlock unknownSectionBlock = block(
                3,
                DisclosureContentBlockType.PARAGRAPH,
                "누락된 Section의 본문",
                unknownSection,
                document
        );

        assertThrows(
                IllegalStateException.class,
                () -> generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(listedSection),
                        List.of(unknownSectionBlock)
                )
        );

        DisclosureContentBlock duplicateSequenceBlock = block(
                1,
                DisclosureContentBlockType.PARAGRAPH,
                "Section 시작과 순서가 중복된 본문",
                listedSection,
                document
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generateChunks(
                        DOCUMENT_ID,
                        List.of(listedSection),
                        List.of(duplicateSequenceBlock)
                )
        );
    }

    private DisclosureDocument document(UUID id) {
        DisclosureDocument result = mock(DisclosureDocument.class);
        when(result.getId()).thenReturn(id);
        return result;
    }

    private DisclosureSection section(
            int sequenceNo,
            long idSuffix,
            String title,
            DisclosureSection parent,
            DisclosureDocument owner
    ) {
        DisclosureSection result = mock(DisclosureSection.class);
        when(result.getId()).thenReturn(new UUID(20, idSuffix));
        when(result.getDisclosureDocument()).thenReturn(owner);
        when(result.getParentSection()).thenReturn(parent);
        when(result.getSequenceNo()).thenReturn(sequenceNo);
        when(result.getTitle()).thenReturn(title);
        return result;
    }

    private DisclosureContentBlock block(
            int sequenceNo,
            DisclosureContentBlockType type,
            String text,
            DisclosureSection ownerSection,
            DisclosureDocument ownerDocument
    ) {
        DisclosureContentBlock result =
                mock(DisclosureContentBlock.class);
        when(result.getId()).thenReturn(new UUID(30, sequenceNo));
        when(result.getDisclosureDocument()).thenReturn(ownerDocument);
        when(result.getSection()).thenReturn(ownerSection);
        when(result.getBlockType()).thenReturn(type);
        when(result.getSequenceNo()).thenReturn(sequenceNo);
        when(result.getTextContent()).thenReturn(text);
        when(result.getSourceLineStart()).thenReturn(sequenceNo * 10);
        when(result.getSourceLineEnd()).thenReturn(sequenceNo * 10 + 1);
        return result;
    }
}
