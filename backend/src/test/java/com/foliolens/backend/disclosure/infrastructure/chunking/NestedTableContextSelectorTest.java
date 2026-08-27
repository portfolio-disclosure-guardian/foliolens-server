package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedTableContextSelectorTest {

    private final NestedTableContextSelector selector =
            new NestedTableContextSelector(
                    new ChunkTextNormalizer(),
                    new SentenceBoundarySplitter()
            );

    @Test
    void returnsEmptyContextForMissingOrEmptyInput() {
        ParsedDisclosureTableContext missing = selector.select(null);
        ParsedDisclosureTableContext empty = selector.select(
                new ParsedDisclosureTableContext("  ", null)
        );

        assertAll(
                () -> assertTrue(missing.isEmpty()),
                () -> assertTrue(empty.isEmpty())
        );
    }

    @Test
    void keepsBothShortAdjacentContextsAndNormalizesWhitespace() {
        ParsedDisclosureTableContext result = selector.select(
                new ParsedDisclosureTableContext(
                        "  표 앞의   첫 문장입니다.\r\n표 바로 앞 문장입니다.  ",
                        "  표 바로 뒤 문장입니다.\n표 뒤의 둘째 문장입니다.  "
                )
        );

        assertAll(
                () -> assertEquals(
                        "표 앞의 첫 문장입니다. 표 바로 앞 문장입니다.",
                        result.precedingText()
                ),
                () -> assertEquals(
                        "표 바로 뒤 문장입니다. 표 뒤의 둘째 문장입니다.",
                        result.followingText()
                )
        );
    }

    @Test
    void keepsNearestEndOfLongPrecedingContext() {
        String farMarker = "멀리있는앞쪽표식";
        String nearestMarker = "표바로앞표식";
        String precedingText = farMarker
                + " 문장입니다. "
                + "중간 설명 문장입니다. ".repeat(40)
                + nearestMarker
                + " 문장입니다.";

        ParsedDisclosureTableContext result = selector.select(
                new ParsedDisclosureTableContext(
                        precedingText,
                        null
                )
        );

        assertAll(
                () -> assertTrue(
                        result.precedingText().contains(nearestMarker)
                ),
                () -> assertFalse(
                        result.precedingText().contains(farMarker)
                ),
                () -> assertTrue(
                        result.precedingText().length()
                                <= NestedTableContextSelector
                                .MAX_SELECTED_CONTEXT_CHARS
                ),
                () -> assertNull(result.followingText())
        );
    }

    @Test
    void keepsNearestStartOfLongFollowingContext() {
        String nearestMarker = "표바로뒤표식";
        String farMarker = "멀리있는뒤쪽표식";
        String followingText = nearestMarker
                + " 문장입니다. "
                + "중간 설명 문장입니다. ".repeat(40)
                + farMarker
                + " 문장입니다.";

        ParsedDisclosureTableContext result = selector.select(
                new ParsedDisclosureTableContext(
                        null,
                        followingText
                )
        );

        assertAll(
                () -> assertTrue(
                        result.followingText().contains(nearestMarker)
                ),
                () -> assertFalse(
                        result.followingText().contains(farMarker)
                ),
                () -> assertTrue(
                        result.followingText().length()
                                <= NestedTableContextSelector
                                .MAX_SELECTED_CONTEXT_CHARS
                ),
                () -> assertNull(result.precedingText())
        );
    }

    @Test
    void sharesTotalBudgetBetweenBothSides() {
        ParsedDisclosureTableContext result = selector.select(
                new ParsedDisclosureTableContext(
                        "표 앞 설명 문장입니다. ".repeat(40),
                        "표 뒤 설명 문장입니다. ".repeat(40)
                )
        );

        int selectedLength = result.precedingText().length()
                + 1
                + result.followingText().length();

        assertAll(
                () -> assertTrue(result.hasPrecedingText()),
                () -> assertTrue(result.hasFollowingText()),
                () -> assertTrue(
                        selectedLength
                                <= NestedTableContextSelector
                                .MAX_SELECTED_CONTEXT_CHARS
                ),
                () -> assertTrue(
                        result.precedingText().endsWith("문장입니다.")
                ),
                () -> assertTrue(
                        result.followingText().endsWith("문장입니다.")
                )
        );
    }
}
