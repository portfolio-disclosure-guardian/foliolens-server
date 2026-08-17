package com.foliolens.backend.disclosure.infrastructure.xml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DartXmlSanitizingReader extends Reader{

    /**
     * XML이 기본으로 허용하는 엔티티와 숫자 엔티티는 유지한다.
     */
    private static final Pattern BARE_AMPERSAND_PATTERN =
            Pattern.compile(
                    "&(?!(?:"
                            + "amp"
                            + "|lt"
                            + "|gt"
                            + "|quot"
                            + "|apos"
                            + "|#[0-9]+"
                            + "|#x[0-9A-Fa-f]+"
                            + ");)"
            );

    /**
     * 실제 XML 태그, 종료 태그, 처리 명령과 선언이 아닌 <를 찾는다.
     *
     * DART 구조 태그는 ASCII 영문자 또는 밑줄로 시작하므로
     * "< TV 시장점유율 추이 >"나
     * "<이사ㆍ감사 전체의 보수현황>" 같은 표현은 일반 텍스트로 본다.
     */
    private static final Pattern BARE_LESS_THAN_PATTERN =
            Pattern.compile(
                    "<(?!(?:"
                            + "[!?]"
                            + "|/?[A-Za-z_][A-Za-z0-9_.:-]*(?:"
                            + "\\s*/?>"
                            + "|\\s+[A-Za-z_][A-Za-z0-9_.:-]*\\s*="
                            + "|\\s*$"
                            + ")"
                            + "))"
            );

    /**
     * <CG>, </CG>처럼 속성이 없는 단순 태그 형태를 찾는다.
     */
    private static final Pattern SIMPLE_ANGLE_TAG_PATTERN =
            Pattern.compile(
                    "<(/?)([A-Za-z_][A-Za-z0-9_.:-]*)\\s*>"
            );

    /**
     * XML 구조 태그가 아니라 본문에 사용된 약어·작품명이다.
     *
     * 원문은 변경하지 않고 읽는 과정에서만 일반 텍스트로 변환한다.
     */
    private static final Set<String>
            NON_STRUCTURAL_ANGLE_TAG_NAMES = Set.of(
            "STS",
            "CG",
            "BGMI",
            "DREAM",
            "MANIFESTO",
            "DATABADA",
            "GRANDATA",
            "SIT",
            "IIT",
            "SHEESH"
    );


    private final BufferedReader delegate;

    private String currentBuffer = "";
    private int currentPosition;
    private boolean endOfInput;
    private long repairedAmpersandCount;
    private long repairedLessThanCount;
    private long repairedAttributeQuoteCount;


    public DartXmlSanitizingReader(Reader delegate) {
        this.delegate =
                new BufferedReader(
                        Objects.requireNonNull(
                                delegate,
                                "delegate는 필수입니다."
                        )
                );
    }

    @Override
    public int read(char[] target, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, target.length);

        if (length == 0) {
            return 0;
        }

        int writtenLength = 0;

        while (writtenLength < length) {
            if (currentPosition >= currentBuffer.length()) {
                if (!loadNextLine()) {
                    break;
                }
            }

            int availableLength = currentBuffer.length() - currentPosition;

            int copyLength =
                    Math.min(
                            length - writtenLength,
                            availableLength
                    );

            currentBuffer.getChars(
                    currentPosition,
                    currentPosition + copyLength,
                    target,
                    offset + writtenLength
            );

            currentPosition += copyLength;
            writtenLength += copyLength;
        }

        if (writtenLength == 0 && endOfInput) {
            return -1;
        }

        return writtenLength;
    }

    private boolean loadNextLine()
            throws IOException {
        if (endOfInput) {
            return false;
        }

        String sourceLine = delegate.readLine();

        if (sourceLine == null) {
            endOfInput = true;
            currentBuffer = "";
            currentPosition = 0;

            return false;
        }

        currentBuffer =
                escapeMalformedCharacters(sourceLine)
                        + "\n";

        currentPosition = 0;

        return true;
    }

    private String escapeMalformedCharacters(
            String sourceLine
    ) {
        String repaired = escapeBareAmpersands(sourceLine);

        /*
         * XML 속성값 내부의 잘못된 따옴표를 보정한다.
         */
        repaired = repairMalformedAttributeQuotes(repaired);

        /*
         * <CG>, <MANIFESTO>처럼 본문에 사용된 표현을
         * 일반 문자열로 변환한다.
         */
        repaired = escapeNonStructuralAngleTags(repaired);

        return escapeBareLessThanCharacters(repaired);
    }

    private String escapeNonStructuralAngleTags(
            String sourceLine
    ) {
        Matcher matcher =
                SIMPLE_ANGLE_TAG_PATTERN.matcher(sourceLine);

        StringBuffer result =
                new StringBuffer(sourceLine.length());

        while (matcher.find()) {
            String tagName = matcher
                    .group(2)
                    .toUpperCase(Locale.ROOT);

            /*
             * 실제 실패 원인으로 확인된 본문 표현이 아니면
             * 기존 XML 태그를 그대로 유지한다.
             */
            if (!NON_STRUCTURAL_ANGLE_TAG_NAMES.contains(tagName)) {
                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(
                                matcher.group()
                        )
                );

                continue;
            }

            repairedLessThanCount++;

            String originalToken = matcher.group();

            String escapedToken =
                    "&lt;"
                            + originalToken.substring(
                            1,
                            originalToken.length() - 1
                    )
                            + "&gt;";

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(escapedToken)
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    private String escapeBareLessThanCharacters(
            String sourceLine
    ) {
        Matcher matcher =
                BARE_LESS_THAN_PATTERN.matcher(sourceLine);

        StringBuffer result =
                new StringBuffer(sourceLine.length());

        while (matcher.find()) {
            repairedLessThanCount++;

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement("&lt;")
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    private String escapeBareAmpersands(
            String sourceLine
    ) {
        Matcher matcher =
                BARE_AMPERSAND_PATTERN.matcher(
                        sourceLine
                );

        StringBuffer result =
                new StringBuffer(
                        sourceLine.length()
                );

        while (matcher.find()) {
            repairedAmpersandCount++;

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            "&amp;"
                    )
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    private String repairMalformedAttributeQuotes(String sourceLine) {
        StringBuilder result = new StringBuilder(sourceLine.length());

        boolean insideTag = false;
        boolean insideAttributeValue = false;

        int attributeValueLength = 0;

        for (int index = 0; index < sourceLine.length(); index++) {
            char current = sourceLine.charAt(index);

            /*
             * 아직 XML 태그 안이 아닌 경우
             */
            if (!insideTag) {
                if (current == '<') {
                    insideTag = true;
                }

                result.append(current);
                continue;
            }

            /*
             * 태그 안이지만 속성값 안은 아닌 경우
             */
            if (!insideAttributeValue) {
                result.append(current);

                if (current == '>') {
                    insideTag = false;
                    continue;
                }

                if (current == '"' && isAttributeOpeningQuote(sourceLine, index)) {
                    insideAttributeValue = true;
                    attributeValueLength = 0;
                }

                continue;
            }

            /*
             * 속성값 안에서 따옴표가 아닌 문자는 그대로 사용한다.
             */
            if (current != '"') {
                result.append(current);
                attributeValueLength++;
                continue;
            }

            /*
             * 정상적인 속성 종료 따옴표인 경우
             */
            if (isAttributeClosingQuote(sourceLine, index)) {
                result.append(current);
                insideAttributeValue = false;
                attributeValueLength = 0;
                continue;
            }

            repairedAttributeQuoteCount++;

            /*
             * ENG=""Snow Corporation"처럼
             * 속성 시작 직후 따옴표가 중복된 경우에는 제거한다.
             */
            if (attributeValueLength == 0) {
                continue;
            }

            /*
             * 속성값 중간의 따옴표는 XML 엔티티로 변환한다.
             */
            result.append("&quot;");
            attributeValueLength++;
        }

        return result.toString();
    }

    private boolean isAttributeOpeningQuote(String sourceLine, int quoteIndex) {
        int previousIndex = quoteIndex - 1;

        while (
                previousIndex >= 0
                        && Character.isWhitespace(sourceLine.charAt(previousIndex))
        ) {
            previousIndex--;
        }

        return previousIndex >= 0
                && sourceLine.charAt(previousIndex) == '=';
    }

    private boolean isAttributeClosingQuote(
            String sourceLine,
            int quoteIndex
    ) {
        int nextIndex = skipWhitespace(sourceLine, quoteIndex + 1);

        /*
         * 행 마지막의 따옴표는 닫는 따옴표로 판단한다.
         */
        if (nextIndex >= sourceLine.length()) {
            return true;
        }

        char next = sourceLine.charAt(nextIndex);

        /*
         * ATTR="value">
         */
        if (next == '>') {
            return true;
        }

        /*
         * ATTR="value"/>
         */
        if (
                next == '/'
                        && nextIndex + 1 < sourceLine.length()
                        && sourceLine.charAt(nextIndex + 1) == '>'
        ) {
            return true;
        }

        /*
         * XML 선언의 encoding="UTF-8"?>
         */
        if (
                next == '?'
                        && nextIndex + 1 < sourceLine.length()
                        && sourceLine.charAt(nextIndex + 1) == '>'
        ) {
            return true;
        }

        /*
         * ATTR1="value" ATTR2="value"
         */
        if (!isXmlNameStart(next)) {
            return false;
        }

        int cursor = nextIndex + 1;

        while (
                cursor < sourceLine.length()
                        && isXmlNamePart(
                        sourceLine.charAt(cursor)
                )
        ) {
            cursor++;
        }

        cursor = skipWhitespace(sourceLine, cursor);

        return cursor < sourceLine.length()
                && sourceLine.charAt(cursor) == '=';
    }

    private int skipWhitespace(
            String sourceLine,
            int startIndex
    ) {
        int index = startIndex;

        while (
                index < sourceLine.length()
                        && Character.isWhitespace(
                        sourceLine.charAt(index)
                )
        ) {
            index++;
        }

        return index;
    }

    private boolean isXmlNameStart(char value) {
        return Character.isLetter(value)
                || value == '_'
                || value == ':';
    }

    private boolean isXmlNamePart(char value) {
        return isXmlNameStart(value)
                || Character.isDigit(value)
                || value == '-'
                || value == '.';
    }


    public long getRepairedAmpersandCount() {
        return repairedAmpersandCount;
    }

    public long getRepairedLessThanCount() {
        return repairedLessThanCount;
    }

    public long getRepairedAttributeQuoteCount() {
        return repairedAttributeQuoteCount;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
