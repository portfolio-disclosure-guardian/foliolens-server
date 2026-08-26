package com.foliolens.backend.disclosure.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DisclosureDocumentChunkStatusTest {

    private static final Instant ATTEMPTED_AT =
            Instant.parse("2026-08-25T06:00:00Z");

    @Test
    void startsWithPendingChunkStatus() {
        DisclosureDocument document = createDocument();

        assertAll(
                () -> assertEquals(
                        DisclosureDocumentChunkStatus.PENDING,
                        document.getChunkStatus()
                ),
                () -> assertNull(document.getChunkGeneratorName()),
                () -> assertNull(document.getChunkGeneratorVersion()),
                () -> assertNull(document.getChunkErrorMessage()),
                () -> assertNull(document.getChunkedAt())
        );
    }

    @Test
    void marksChunkingCompleted() {
        DisclosureDocument document = createDocument();

        document.markChunkingCompleted(
                " DartXmlDisclosureChunkGenerator ",
                " dart-xml-chunk-v1 ",
                ATTEMPTED_AT
        );

        assertAll(
                () -> assertEquals(
                        DisclosureDocumentChunkStatus.COMPLETED,
                        document.getChunkStatus()
                ),
                () -> assertEquals(
                        "DartXmlDisclosureChunkGenerator",
                        document.getChunkGeneratorName()
                ),
                () -> assertEquals(
                        "dart-xml-chunk-v1",
                        document.getChunkGeneratorVersion()
                ),
                () -> assertNull(document.getChunkErrorMessage()),
                () -> assertEquals(ATTEMPTED_AT, document.getChunkedAt())
        );
    }

    @Test
    void marksChunkingFailed() {
        DisclosureDocument document = createDocument();

        document.markChunkingFailed(
                "DartXmlDisclosureChunkGenerator",
                "dart-xml-chunk-v1",
                " TABLE payload 오류 ",
                ATTEMPTED_AT
        );

        assertAll(
                () -> assertEquals(
                        DisclosureDocumentChunkStatus.FAILED,
                        document.getChunkStatus()
                ),
                () -> assertEquals(
                        "TABLE payload 오류",
                        document.getChunkErrorMessage()
                ),
                () -> assertEquals(ATTEMPTED_AT, document.getChunkedAt())
        );
    }

    @Test
    void resetsChunkingWhenParsingResultChanges() {
        DisclosureDocument document = createDocument();
        document.markChunkingCompleted(
                "DartXmlDisclosureChunkGenerator",
                "dart-xml-chunk-v1",
                ATTEMPTED_AT
        );

        document.markCompleted(
                "DartXmlDisclosureParser",
                "dart-xml-parser-v2",
                ATTEMPTED_AT.plusSeconds(60)
        );

        assertAll(
                () -> assertEquals(
                        DisclosureDocumentChunkStatus.PENDING,
                        document.getChunkStatus()
                ),
                () -> assertNull(document.getChunkGeneratorName()),
                () -> assertNull(document.getChunkGeneratorVersion()),
                () -> assertNull(document.getChunkErrorMessage()),
                () -> assertNull(document.getChunkedAt())
        );
    }

    @Test
    void rejectsInvalidChunkingResultMetadata() {
        DisclosureDocument document = createDocument();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> document.markChunkingCompleted(
                                " ",
                                "dart-xml-chunk-v1",
                                ATTEMPTED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> document.markChunkingFailed(
                                "DartXmlDisclosureChunkGenerator",
                                "dart-xml-chunk-v1",
                                " ",
                                ATTEMPTED_AT
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> document.markChunkingCompleted(
                                "DartXmlDisclosureChunkGenerator",
                                "dart-xml-chunk-v1",
                                null
                        )
                )
        );
    }

    private DisclosureDocument createDocument() {
        return DisclosureDocument.create(
                mock(Disclosure.class),
                "reports/sample.xml",
                "reports/sample.xml",
                "sample.xml",
                "xml",
                DisclosureDocumentRole.MAIN,
                "사업보고서",
                DisclosureDocumentContentFormat.DART_XML,
                1_024L,
                "a".repeat(64)
        );
    }
}
