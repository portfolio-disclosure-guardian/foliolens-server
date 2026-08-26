package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedDisclosureChunkTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Test
    void calculatesSourceAndCharacterMetadata() {
        GeneratedChunkSource first = GeneratedChunkSource.block(
                UUID.randomUUID(),
                2,
                -1,
                -1
        );
        GeneratedChunkSource second = GeneratedChunkSource.block(
                UUID.randomUUID(),
                4,
                20,
                25
        );
        GeneratedChunkSource third = GeneratedChunkSource.block(
                UUID.randomUUID(),
                5,
                18,
                30
        );
        List<GeneratedChunkSource> mutableSources =
                new ArrayList<>(List.of(first, second, third));

        GeneratedDisclosureChunk chunk = new GeneratedDisclosureChunk(
                DOCUMENT_ID,
                null,
                "  사업의 내용  ",
                DisclosureChunkType.TEXT,
                1,
                "  본문  ",
                "  검색 본문  ",
                mutableSources,
                " test-generator ",
                " v1 "
        );
        mutableSources.clear();

        assertEquals("사업의 내용", chunk.sectionPath());
        assertEquals("본문", chunk.bodyText());
        assertEquals("검색 본문", chunk.searchText());
        assertEquals(2, chunk.bodyCharacterCount());
        assertEquals(5, chunk.searchCharacterCount());
        assertEquals(2, chunk.firstSourceSequenceNo());
        assertEquals(5, chunk.lastSourceSequenceNo());
        assertEquals(18, chunk.sourceLineStart());
        assertEquals(30, chunk.sourceLineEnd());
        assertEquals(3, chunk.sources().size());
        assertEquals("test-generator", chunk.generatorName());
        assertEquals("v1", chunk.generatorVersion());
    }

    @Test
    void completesDraftWithPolicyMetadata() {
        GeneratedChunkDraft draft = new GeneratedChunkDraft(
                DOCUMENT_ID,
                null,
                SectionPathResolver.PREAMBLE_PATH,
                DisclosureChunkType.TEXT,
                7,
                1,
                "본문",
                "검색 본문",
                List.of(
                        GeneratedChunkSource.block(
                                UUID.randomUUID(),
                                7,
                                10,
                                11
                        )
                )
        );

        GeneratedDisclosureChunk completed = draft.complete(
                3,
                DisclosureChunkingPolicy.dartXmlV1()
        );

        assertEquals(3, completed.chunkSequenceNo());
        assertEquals(draft.bodyText(), completed.bodyText());
        assertEquals(draft.sources(), completed.sources());
        assertEquals(
                "dart-xml-chunk-v1",
                completed.generatorVersion()
        );
    }

    @Test
    void rejectsUnorderedOrEmptySources() {
        GeneratedChunkSource later = GeneratedChunkSource.block(
                UUID.randomUUID(),
                2,
                2,
                2
        );
        GeneratedChunkSource earlier = GeneratedChunkSource.block(
                UUID.randomUUID(),
                1,
                1,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkWithSources(List.of(later, earlier))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> chunkWithSources(List.of())
        );
    }

    private GeneratedDisclosureChunk chunkWithSources(
            List<GeneratedChunkSource> sources
    ) {
        return new GeneratedDisclosureChunk(
                DOCUMENT_ID,
                null,
                "",
                DisclosureChunkType.TEXT,
                1,
                "본문",
                "검색 본문",
                sources,
                "generator",
                "v1"
        );
    }
}
