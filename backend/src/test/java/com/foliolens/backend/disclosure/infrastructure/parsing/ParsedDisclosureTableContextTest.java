package com.foliolens.backend.disclosure.infrastructure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedDisclosureTableContextTest {

    @Test
    void normalizesLineEndingsAndPreservesTextBoundaries() {
        ParsedDisclosureTableContext context =
                new ParsedDisclosureTableContext(
                        "  첫 문장\r\n둘째 문장\r셋째 문장  ",
                        "  표 이후 설명  "
                );

        assertAll(
                () -> assertEquals(
                        "첫 문장\n둘째 문장\n셋째 문장",
                        context.precedingText()
                ),
                () -> assertEquals(
                        "표 이후 설명",
                        context.followingText()
                ),
                () -> assertTrue(context.hasPrecedingText()),
                () -> assertTrue(context.hasFollowingText()),
                () -> assertFalse(context.isEmpty())
        );
    }

    @Test
    void convertsBlankContextToNull() {
        ParsedDisclosureTableContext context =
                new ParsedDisclosureTableContext(
                        " \t\r\n ",
                        null
                );

        assertAll(
                () -> assertNull(context.precedingText()),
                () -> assertNull(context.followingText()),
                () -> assertFalse(context.hasPrecedingText()),
                () -> assertFalse(context.hasFollowingText()),
                () -> assertTrue(context.isEmpty())
        );
    }
}
