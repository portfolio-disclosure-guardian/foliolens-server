package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextChunkGeneratorTest {

    private static final UUID DOCUMENT_ID = new UUID(1, 1);
    private static final UUID SECTION_ID = new UUID(2, 2);

    private DisclosureDocument document;
    private DisclosureSection section;

    @BeforeEach
    void setUp() {
        document = mock(DisclosureDocument.class);
        section = mock(DisclosureSection.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(section.getId()).thenReturn(SECTION_ID);
    }

    @Test
    void combinesHeadingAndParagraphsWithSearchContextAndSources() {
        TextChunkGenerator generator = generator(20, 40, 50, 60);
        DisclosureContentBlock heading = block(
                1,
                DisclosureContentBlockType.HEADING,
                " 신규  시설투자 ",
                10,
                10
        );
        DisclosureContentBlock firstParagraph = block(
                2,
                DisclosureContentBlockType.PARAGRAPH,
                "투자금액은 5,000억원입니다.",
                11,
                12
        );
        DisclosureContentBlock secondParagraph = block(
                3,
                DisclosureContentBlockType.PARAGRAPH,
                "완료 예정일은 2028년입니다.",
                13,
                14
        );

        List<GeneratedChunkDraft> result = generator.generate(
                DOCUMENT_ID,
                SECTION_ID,
                "II. 사업의 내용",
                List.of(heading, firstParagraph, secondParagraph)
        );

        assertEquals(1, result.size());
        GeneratedChunkDraft draft = result.getFirst();
        assertEquals(
                "투자금액은 5,000억원입니다.\n\n"
                        + "완료 예정일은 2028년입니다.",
                draft.bodyText()
        );
        assertEquals(
                "[II. 사업의 내용]\n"
                        + "소제목: 신규 시설투자\n"
                        + "투자금액은 5,000억원입니다.\n"
                        + "완료 예정일은 2028년입니다.",
                draft.searchText()
        );
        assertEquals(2, draft.anchorBlockSequenceNo());
        assertEquals(0, draft.anchorPartIndex());
        assertEquals(
                List.of(1, 2, 3),
                draft.sources().stream()
                        .map(GeneratedChunkSource::blockSequenceNo)
                        .toList()
        );
    }

    @Test
    void flushesTextAtTableAndImageBoundaries() {
        TextChunkGenerator generator = generator(10, 30, 40, 50);

        List<GeneratedChunkDraft> result = generator.generate(
                DOCUMENT_ID,
                SECTION_ID,
                "사업의 내용",
                List.of(
                        block(1, DisclosureContentBlockType.PARAGRAPH,
                                "표 앞 문단", 1, 1),
                        block(2, DisclosureContentBlockType.TABLE,
                                null, 2, 5),
                        block(3, DisclosureContentBlockType.PARAGRAPH,
                                "표 뒤 문단", 6, 6),
                        block(4, DisclosureContentBlockType.IMAGE,
                                null, 7, 8),
                        block(5, DisclosureContentBlockType.PARAGRAPH,
                                "이미지 뒤 문단", 9, 9)
                )
        );

        assertEquals(3, result.size());
        assertEquals("표 앞 문단", result.get(0).bodyText());
        assertEquals("표 뒤 문단", result.get(1).bodyText());
        assertEquals("이미지 뒤 문단", result.get(2).bodyText());
        assertEquals(
                List.of(1, 3, 5),
                result.stream()
                        .map(GeneratedChunkDraft::anchorBlockSequenceNo)
                        .toList()
        );
    }

    @Test
    void treatsPageBreakAsSoftBoundary() {
        TextChunkGenerator generator = generator(10, 20, 25, 30);

        List<GeneratedChunkDraft> result = generator.generate(
                DOCUMENT_ID,
                SECTION_ID,
                "",
                List.of(
                        block(1, DisclosureContentBlockType.PARAGRAPH,
                                "1234567890", 1, 1),
                        block(2, DisclosureContentBlockType.PAGE_BREAK,
                                null, 2, 2),
                        block(3, DisclosureContentBlockType.PARAGRAPH,
                                "abc", 3, 3),
                        block(4, DisclosureContentBlockType.PAGE_BREAK,
                                null, 4, 4),
                        block(5, DisclosureContentBlockType.PARAGRAPH,
                                "def", 5, 5)
                )
        );

        assertEquals(2, result.size());
        assertEquals("1234567890", result.get(0).bodyText());
        assertEquals("abc\n\ndef", result.get(1).bodyText());
    }

    @Test
    void fillsShortChunkUpToNormalMaximum() {
        TextChunkGenerator generator = generator(10, 12, 20, 30);

        List<GeneratedChunkDraft> result = generator.generate(
                DOCUMENT_ID,
                SECTION_ID,
                "",
                List.of(
                        block(1, DisclosureContentBlockType.PARAGRAPH,
                                "12345", 1, 1),
                        block(2, DisclosureContentBlockType.PARAGRAPH,
                                "1234567", 2, 2)
                )
        );

        assertEquals(1, result.size());
        assertEquals("12345\n\n1234567", result.getFirst().bodyText());
        assertTrue(result.getFirst().bodyText().length() > 12);
        assertTrue(result.getFirst().bodyText().length() <= 20);
    }

    @Test
    void splitsOversizedParagraphAndKeepsPartOrder() {
        TextChunkGenerator generator = generator(10, 15, 20, 30);
        String longParagraph = "가".repeat(65);

        List<GeneratedChunkDraft> result = generator.generate(
                DOCUMENT_ID,
                SECTION_ID,
                "",
                List.of(
                        block(7, DisclosureContentBlockType.PARAGRAPH,
                                longParagraph, 100, 120)
                )
        );

        assertEquals(4, result.size());
        assertEquals(
                longParagraph,
                result.stream()
                        .map(GeneratedChunkDraft::bodyText)
                        .reduce("", String::concat)
        );
        assertEquals(
                List.of(0, 1, 2, 3),
                result.stream()
                        .map(GeneratedChunkDraft::anchorPartIndex)
                        .toList()
        );
        assertTrue(
                result.stream()
                        .allMatch(draft -> draft.bodyText().length() <= 30)
        );
        assertTrue(
                result.stream()
                        .allMatch(
                                draft -> draft.sources().getFirst()
                                        .blockSequenceNo() == 7
                        )
        );
    }

    @Test
    void returnsNoChunkForEmptyOrHeadingOnlyInput() {
        TextChunkGenerator generator = generator(10, 20, 25, 30);

        assertEquals(
                List.of(),
                generator.generate(DOCUMENT_ID, SECTION_ID, "", List.of())
        );
        assertEquals(
                List.of(),
                generator.generate(
                        DOCUMENT_ID,
                        SECTION_ID,
                        "",
                        List.of(
                                block(1, DisclosureContentBlockType.HEADING,
                                        "제목만 있음", 1, 1)
                        )
                )
        );
    }

    @Test
    void rejectsBlocksFromOtherDocumentSectionOrWrongOrder() {
        TextChunkGenerator generator = generator(10, 20, 25, 30);
        DisclosureContentBlock valid = block(
                2,
                DisclosureContentBlockType.PARAGRAPH,
                "정상 문단",
                2,
                2
        );
        DisclosureContentBlock earlier = block(
                1,
                DisclosureContentBlockType.PARAGRAPH,
                "앞 문단",
                1,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                        DOCUMENT_ID,
                        SECTION_ID,
                        "",
                        List.of(valid, earlier)
                )
        );

        DisclosureContentBlock otherDocument = block(
                1,
                DisclosureContentBlockType.PARAGRAPH,
                "다른 문서",
                1,
                1
        );
        DisclosureDocument differentDocument =
                mock(DisclosureDocument.class);
        when(differentDocument.getId()).thenReturn(new UUID(9, 9));
        when(otherDocument.getDisclosureDocument())
                .thenReturn(differentDocument);

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                        DOCUMENT_ID,
                        SECTION_ID,
                        "",
                        List.of(otherDocument)
                )
        );

        DisclosureContentBlock otherSection = block(
                1,
                DisclosureContentBlockType.PARAGRAPH,
                "다른 섹션",
                1,
                1
        );
        DisclosureSection differentSection = mock(DisclosureSection.class);
        when(differentSection.getId()).thenReturn(new UUID(8, 8));
        when(otherSection.getSection()).thenReturn(differentSection);

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                        DOCUMENT_ID,
                        SECTION_ID,
                        "",
                        List.of(otherSection)
                )
        );
    }

    private TextChunkGenerator generator(
            int targetMin,
            int targetMax,
            int normalMax,
            int absoluteMax
    ) {
        DisclosureChunkingPolicy.ChunkSizePolicy sizePolicy =
                new DisclosureChunkingPolicy.ChunkSizePolicy(
                        targetMin,
                        targetMax,
                        normalMax,
                        absoluteMax
                );
        DisclosureChunkingPolicy policy =
                new DisclosureChunkingPolicy(
                        "test-generator",
                        "test-v1",
                        sizePolicy,
                        sizePolicy
                );

        return new TextChunkGenerator(
                policy,
                new ChunkTextNormalizer(),
                new SentenceBoundarySplitter()
        );
    }

    private DisclosureContentBlock block(
            int sequenceNo,
            DisclosureContentBlockType type,
            String text,
            int sourceLineStart,
            int sourceLineEnd
    ) {
        DisclosureContentBlock block = mock(DisclosureContentBlock.class);
        when(block.getId()).thenReturn(new UUID(3, sequenceNo));
        when(block.getDisclosureDocument()).thenReturn(document);
        when(block.getSection()).thenReturn(section);
        when(block.getBlockType()).thenReturn(type);
        when(block.getSequenceNo()).thenReturn(sequenceNo);
        when(block.getTextContent()).thenReturn(text);
        when(block.getSourceLineStart()).thenReturn(sourceLineStart);
        when(block.getSourceLineEnd()).thenReturn(sourceLineEnd);
        return block;
    }
}
