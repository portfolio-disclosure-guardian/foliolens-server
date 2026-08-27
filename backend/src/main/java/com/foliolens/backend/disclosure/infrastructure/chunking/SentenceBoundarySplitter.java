package com.foliolens.backend.disclosure.infrastructure.chunking;

import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 긴 문단을 가능한 한 문장 경계를 유지하면서 여러 문자열로 나누는 클래스
 *
 * 긴 PARAGRAPH
 * → SentenceBoundarySplitter
 * → 문장 단위 문자열 여러 개
 * → TextChunker가 청크로 변환
 */
@Component
public class SentenceBoundarySplitter {

    /**
     * 실행 흐름:
     * 입력값 검증
     * → 짧으면 그대로 반환
     * → 문장 경계 추출 (문장 단위로 나눔 -> extractSentences)
     * → 절대 최대보다 긴 문장 추가 분할 (splitOversizedUnit)
     * → 작은 문장들을 목표 크기에 맞게 묶음 (packUnits)
     * → 모든 결과가 절대 최대 이하인지 검증
     * → 반환
     *
     * preferredMaxChars를 목표로 문장을 묶는다.
     * 어떤 결과도 absoluteMaxChars를 넘지 않게 한다.
     */
    public List<String> split(
            String normalizedText, // 분할할 문단
            int preferredMaxChars, // 청크를 가급적 이 길이 이하로 만들고 싶다는 목표
            int absoluteMaxChars // 어떤 결과도 넘어서는 안 되는 절대 최대 길이
    ) {
        Objects.requireNonNull(
                normalizedText,
                "normalizedText는 필수입니다."
        );

        if (normalizedText.isBlank()) {
            return List.of();
        }

        if (preferredMaxChars < 1) {
            throw new IllegalArgumentException(
                    "preferredMaxChars는 1 이상이어야 합니다."
            );
        }

        if (absoluteMaxChars < preferredMaxChars) {
            throw new IllegalArgumentException(
                    "absoluteMaxChars는 preferredMaxChars 이상이어야 합니다."
            );
        }

        String text = normalizedText.strip();

        // 짧은 문단의 빠른 반환
        // text 길이가 preferredMaxChars보다 작으면 반환
        if (text.length() <= preferredMaxChars) {
            return List.of(text);
        }

        // 문장 추출
        // 긴 문단을 우선 문장 단위로 나눔
        List<String> sentenceUnits = extractSentences(text);
        List<String> safeUnits = new ArrayList<>();

        for (String sentence : sentenceUnits) {

            // 지나치게 긴 단일 문장 처리
            // 한 문장이 absoluteMaxChars를 넘지 않는다면 safeUnits에 추가
            if (sentence.length() <= absoluteMaxChars) {
                safeUnits.add(sentence);
                continue;
            }

            /*
             * 한 문장 자체가 절대 최대를 넘는 경우에만
             * 공백·구두점 경계로 추가 분리
             */
            safeUnits.addAll(
                    splitOversizedUnit(
                            sentence,
                            preferredMaxChars
                    )
            );
        }

        List<String> result = packUnits(
                safeUnits,
                preferredMaxChars
        );

        validateAbsoluteMax(
                result,
                absoluteMaxChars
        );

        return List.copyOf(result);
    }

    private List<String> extractSentences(String text) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.KOREAN);

        iterator.setText(text);

        List<String> result = new ArrayList<>();

        int start = iterator.first();

        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text
                    .substring(start, end)
                    .strip();

            if (!sentence.isBlank()) {
                result.add(sentence);
            }
        }

        if (result.isEmpty()) {
            return List.of(text);
        }

        return result;
    }

    /**
     * 문장들을 preferredMaxChars 이하에 가깝도록 묶는다.
     *
     * 한 문장 자체가 preferredMaxChars보다 크지만
     * absoluteMaxChars 이하라면 단독 결과로 유지한다.
     */
    private List<String> packUnits(
            List<String> units,
            int preferredMaxChars
    ) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String unit : units) {
            if (current.isEmpty()) {
                current.append(unit);
                continue;
            }

            int combinedLength = current.length() + 1 + unit.length();

            if (combinedLength <= preferredMaxChars) {
                current.append(' ').append(unit);
                continue;
            }

            result.add(current.toString().strip());
            current.setLength(0);
            current.append(unit);
        }

        if (!current.isEmpty()) {
            result.add(current.toString().strip());
        }

        return result;
    }

    /**
     * 하나의 문장이 절대 최대보다 긴 경우 사용하는 최후 수단이다.
     *
     * 가능한 한 공백이나 구두점에서 나누고,
     * 적절한 경계를 찾지 못하면 글자 수로 자른다.
     */
    private List<String> splitOversizedUnit(
            String value,
            int maxChars
    ) {
        List<String> result = new ArrayList<>();

        String remaining = value.strip();

        while (remaining.length() > maxChars) {
            int splitPosition =
                    findBreakPosition(
                            remaining,
                            maxChars
                    );

            String part = remaining
                    .substring(0, splitPosition)
                    .strip();

            if (!part.isBlank()) {
                result.add(part);
            }

            remaining = remaining
                    .substring(splitPosition)
                    .stripLeading();
        }

        if (!remaining.isBlank()) {
            result.add(remaining);
        }

        return result;
    }

    private int findBreakPosition(String value, int maxChars) {
        int upper = Math.min(maxChars - 1, value.length() - 1);

        /*
         * 너무 앞에서 자르는 것을 막기 위해
         * maxChars의 절반까지만 뒤로 탐색한다.
         */
        int lower = Math.max(1, maxChars / 2);

        for (int index = upper; index >= lower; index--) {
            char current = value.charAt(index);

            if (Character.isWhitespace(current)) {
                return safeSurrogateBoundary(
                        value,
                        index
                );
            }

            if (isPreferredPunctuation(current)) {
                return safeSurrogateBoundary(
                        value,
                        index + 1
                );
            }
        }

        return safeSurrogateBoundary(
                value,
                maxChars
        );
    }

    private boolean isPreferredPunctuation(char value) {
        return value == ','
                || value == ';'
                || value == ':'
                || value == ')'
                || value == ']'
                || value == '，'
                || value == '；';
    }

    /**
     * 이모지 등 surrogate pair 중간을 자르지 않게 한다.
     */
    private int safeSurrogateBoundary(
            String value,
            int position
    ) {
        int safePosition = Math.min(
                position,
                value.length()
        );

        if (
                safePosition > 0
                        && safePosition < value.length()
                        && Character.isHighSurrogate(
                        value.charAt(safePosition - 1)
                )
                        && Character.isLowSurrogate(
                        value.charAt(safePosition)
                )
        ) {
            return safePosition - 1;
        }

        return safePosition;
    }

    private void validateAbsoluteMax(
            List<String> values,
            int absoluteMaxChars
    ) {
        for (String value : values) {
            if (value.length() > absoluteMaxChars) {
                throw new IllegalStateException(
                        "문장 분리 결과가 절대 최대를 초과했습니다."
                                + " length=" + value.length()
                                + ", absoluteMaxChars="
                                + absoluteMaxChars
                );
            }
        }
    }
}
