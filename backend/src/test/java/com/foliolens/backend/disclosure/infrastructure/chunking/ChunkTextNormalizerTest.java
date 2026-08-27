package com.foliolens.backend.disclosure.infrastructure.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkTextNormalizerTest {

    private final ChunkTextNormalizer normalizer =
            new ChunkTextNormalizer();

    @Test
    void normalizesParagraphWhilePreservingMeaningfulLineBreaks() {
        String result = normalizer.normalizeParagraph(
                "  첫째  줄\t내용  \r\n\r\n  둘째\u00A0줄  "
        );

        assertEquals("첫째 줄 내용\n둘째 줄", result);
        assertEquals("", normalizer.normalizeParagraph(" \r\n\t "));
        assertEquals("", normalizer.normalizeParagraph(null));
    }

    @Test
    void normalizesHeadingAndJoinsParagraphs() {
        assertEquals(
                "II. 사업의 내용 신규 투자",
                normalizer.normalizeHeading(
                        " II.  사업의\n내용\t신규 투자 "
                )
        );
        assertEquals(
                "첫 문단\n\n둘째 문단\n둘째 줄",
                normalizer.joinParagraphs(
                        List.of(
                                " 첫  문단 ",
                                " ",
                                "둘째 문단\r\n 둘째 줄"
                        )
                )
        );
    }

    @Test
    void buildsSearchTextWithSectionAndHeadingContext() {
        String searchText = normalizer.buildSearchText(
                " II.  사업의 내용 ",
                List.of(
                        " 신규  시설투자 ",
                        " ",
                        "투자 목적\n및 효과"
                ),
                " 투자금액은 5,000억원입니다. "
        );

        assertEquals(
                "[II. 사업의 내용]\n"
                        + "소제목: 신규 시설투자 > 투자 목적 및 효과\n"
                        + "투자금액은 5,000억원입니다.",
                searchText
        );
    }

    @Test
    void rejectsEmptyBodyWhenBuildingSearchText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.buildSearchText(
                        "사업의 내용",
                        List.of(),
                        "  "
                )
        );
    }
}
