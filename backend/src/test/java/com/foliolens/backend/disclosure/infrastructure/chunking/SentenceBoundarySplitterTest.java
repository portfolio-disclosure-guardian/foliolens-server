package com.foliolens.backend.disclosure.infrastructure.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceBoundarySplitterTest {

    private final SentenceBoundarySplitter splitter =
            new SentenceBoundarySplitter();

    @Test
    void returnsBlankAsEmptyAndShortTextAsSinglePart() {
        assertEquals(List.of(), splitter.split("  ", 10, 20));
        assertEquals(
                List.of("짧은 문장입니다."),
                splitter.split("  짧은 문장입니다.  ", 20, 30)
        );
    }

    @Test
    void preservesKoreanSentenceOrderWhilePackingParts() {
        String text =
                "첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다.";

        List<String> result = splitter.split(text, 24, 30);

        assertEquals(text, String.join(" ", result));
        assertTrue(result.size() >= 2);
        assertTrue(
                result.stream().allMatch(value -> value.length() <= 30)
        );
    }

    @Test
    void hardSplitsSingleOversizedSentenceWithinAbsoluteMaximum() {
        String text = "가".repeat(55);

        List<String> result = splitter.split(text, 20, 30);

        assertEquals(text, String.join("", result));
        assertEquals(List.of(20, 20, 15), lengths(result));
        assertTrue(
                result.stream().allMatch(value -> value.length() <= 30)
        );
    }

    @Test
    void doesNotBreakSurrogatePairDuringHardSplit() {
        String text = "가".repeat(9) + "😀" + "나".repeat(12);

        List<String> result = splitter.split(text, 10, 10);

        assertEquals(text, String.join("", result));
        assertTrue(
                result.stream().allMatch(value -> value.length() <= 10)
        );
        assertFalse(result.stream().anyMatch(this::hasUnpairedSurrogate));
    }

    @Test
    void rejectsInvalidSizeArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.split("본문", 0, 10)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.split("본문", 11, 10)
        );
        assertThrows(
                NullPointerException.class,
                () -> splitter.split(null, 10, 20)
        );
    }

    private List<Integer> lengths(List<String> values) {
        return values.stream()
                .map(String::length)
                .toList();
    }

    private boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);

            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(index + 1)
                )) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }

        return false;
    }
}
