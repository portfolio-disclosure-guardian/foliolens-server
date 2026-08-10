package com.foliolens.backend.disclosure.infrastructure.profiling;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class XmlStructureProfiler {

    private static final String DOCUMENT_NAME_TAG = "DOCUMENT-NAME";

    /**
     * XML 원문 파일 하나를 읽고 구조 요약을 반환한다.
     *
     * XML 전체 내용을 메모리에 올리지 않고
     * StAX를 이용해 앞에서부터 순서대로 읽는다.
     */
    public XmlStructureProfile profile(Path sourceFile) {
        Path normalizedFile = validateSourceFile(sourceFile);

        long fileSizeBytes = readFileSize(normalizedFile);

        Map<String, Long> tagCounts = new HashMap<>();

        String rootElementName = null;
        String documentName = null;

        int currentDepth = 0;
        int maxDepth = 0;

        int documentNameDepth = -1;
        StringBuilder documentNameBuffer = null;

        long repairedAmpersandCount = 0;
        long repairedLessThanCount = 0;

        XMLStreamReader reader = null;

        try (
                BareAmpersandEscapingReader xmlReader =
                        new BareAmpersandEscapingReader(
                                Files.newBufferedReader(
                                        normalizedFile,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            XMLInputFactory inputFactory = createSecureInputFactory();

            reader = inputFactory.createXMLStreamReader(xmlReader);

            while (reader.hasNext()) {
                int eventType = reader.next();

                // XML을 읽다가 <태그>의 시작을 만났다는 뜻
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    currentDepth++;

                    String tagName = normalizeTagName(reader.getLocalName());

                    /*
                     * START_ELEMENT가 등장할 때마다 (태그의 시작을 만날 때마다)
                     * 태그별 개수를 하나씩 증가시킨다.
                     * tagCounts는 map임 -> 각자의 태그 이름별로 개수가 매핑되어 있음
                     */
                    tagCounts.merge(tagName, 1L, Long::sum);

                    /*
                     * 가장 먼저 발견한 시작 태그가
                     * XML 문서의 최상위 태그다.
                     */
                    if (rootElementName == null) {
                        rootElementName = tagName;
                    }

                    maxDepth = Math.max(maxDepth, currentDepth);

                    /*
                     * 첫 번째 DOCUMENT-NAME의 문자 내용을 모은다.
                     */
                    if (
                            documentName == null
                                    && documentNameBuffer == null
                                    && DOCUMENT_NAME_TAG.equals(
                                    tagName
                            )
                    ) {
                        documentNameDepth = currentDepth;

                        documentNameBuffer = new StringBuilder();
                    }

                    continue;
                }

                if (
                        eventType == XMLStreamConstants.CHARACTERS // 줄바꿈과 공백이거나
                                || eventType == XMLStreamConstants.CDATA // CDATA 문자열<![CDATA[...]]> 인 경우
                ) {
                    if (documentNameBuffer != null) {
                        documentNameBuffer.append(reader.getText());
                    }

                    continue;
                }

                // 종료 태그 인 경우 </>
                if (eventType == XMLStreamConstants.END_ELEMENT) {
                    String tagName = normalizeTagName(reader.getLocalName());

                    /*
                     * DOCUMENT-NAME 종료 태그까지 읽었으면
                     * 모아 둔 문자열을 문서명으로 확정한다.
                     */
                    if (
                            documentNameBuffer != null
                                    && currentDepth == documentNameDepth
                                    && DOCUMENT_NAME_TAG.equals(tagName)
                    ) {
                        documentName = normalizeText(documentNameBuffer.toString());

                        documentNameBuffer = null;
                        documentNameDepth = -1;
                    }

                    currentDepth--;
                }
            }

            repairedAmpersandCount =
                    xmlReader.getRepairedAmpersandCount();

            if (repairedAmpersandCount > 0) {
                log.warn(
                        "XML 문법에 맞지 않는 단독 &를 읽기 과정에서 보정했습니다. "
                                + "path={}, repairedCount={}",
                        normalizedFile,
                        repairedAmpersandCount
                );
            }

            repairedLessThanCount =
                    xmlReader.getRepairedLessThanCount();

            if (repairedLessThanCount > 0) {
                log.warn(
                        "XML 문법에 맞지 않는 단독 <를 읽기 과정에서 보정했습니다. "
                                + "path={}, repairedCount={}",
                        normalizedFile,
                        repairedLessThanCount
                );
            }
        } catch (
                IOException
                | XMLStreamException exception
        ) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "XML 원문 구조를 조사하지 못했습니다. "
                            + "path=" + normalizedFile,
                    exception
            );
        } finally {
            closeQuietly(reader);
        }

        if (rootElementName == null) {
            throw datasetException(
                    "XML 원문에서 최상위 태그를 찾지 못했습니다. "
                            + "path=" + normalizedFile
            );
        }

        return new XmlStructureProfile(
                normalizedFile
                        .getFileName()
                        .toString(),
                rootElementName,
                documentName,
                fileSizeBytes,
                maxDepth,
                tagCounts,
                repairedAmpersandCount,
                repairedLessThanCount
        );
    }

    /**
     * 외부 DTD나 외부 엔티티를 읽지 못하도록 설정한다.
     *
     * 공시 XML 안에 외부 파일이나 URL을 가리키는 내용이 있더라도
     * 구조 조사 과정에서 외부 리소스에 접근하지 않게 한다.
     */
    private XMLInputFactory createSecureInputFactory() {
        XMLInputFactory inputFactory =
                XMLInputFactory.newFactory();

        setRequiredProperty(
                inputFactory,
                XMLInputFactory.SUPPORT_DTD,
                false
        );

        setRequiredProperty(
                inputFactory,
                "javax.xml.stream.isSupportingExternalEntities",
                false
        );

        setRequiredProperty(
                inputFactory,
                XMLInputFactory.IS_NAMESPACE_AWARE,
                true
        );

        setRequiredProperty(
                inputFactory,
                XMLInputFactory.IS_COALESCING,
                true
        );

        inputFactory.setXMLResolver(
                (
                        publicId,
                        systemId,
                        baseUri,
                        namespace
                ) -> {
                    throw new XMLStreamException(
                            "외부 XML 리소스 접근은 허용되지 않습니다. "
                                    + "systemId=" + systemId
                    );
                }
        );

        return inputFactory;
    }

    private void setRequiredProperty(
            XMLInputFactory inputFactory,
            String propertyName,
            Object value
    ) {
        try {
            inputFactory.setProperty(
                    propertyName,
                    value
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "현재 XML 파서가 필수 보안 설정을 지원하지 않습니다. "
                            + "property=" + propertyName,
                    exception
            );
        }
    }

    private Path validateSourceFile(Path sourceFile) {
        Objects.requireNonNull(
                sourceFile,
                "sourceFile은 필수입니다."
        );

        Path normalizedFile =
                sourceFile
                        .toAbsolutePath()
                        .normalize();

        if (
                !Files.exists(
                        normalizedFile,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw datasetException(
                    "XML 원문 파일이 존재하지 않습니다. "
                            + "path=" + normalizedFile
            );
        }

        if (Files.isSymbolicLink(normalizedFile)) {
            throw datasetException(
                    "XML 원문 파일은 심볼릭 링크일 수 없습니다. "
                            + "path=" + normalizedFile
            );
        }

        if (
                !Files.isRegularFile(
                        normalizedFile,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw datasetException(
                    "XML 원문 경로가 일반 파일이 아닙니다. "
                            + "path=" + normalizedFile
            );
        }

        if (!Files.isReadable(normalizedFile)) {
            throw datasetException(
                    "XML 원문 파일을 읽을 수 없습니다. "
                            + "path=" + normalizedFile
            );
        }

        String fileName =
                normalizedFile
                        .getFileName()
                        .toString()
                        .toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".xml")) {
            throw datasetException(
                    "XmlStructureProfiler는 XML 파일만 지원합니다. "
                            + "path=" + normalizedFile
            );
        }

        return normalizedFile;
    }

    private long readFileSize(Path sourceFile) {
        try {
            return Files.size(sourceFile);
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "XML 원문 파일 크기를 확인하지 못했습니다. "
                            + "path=" + sourceFile,
                    exception
            );
        }
    }

    /**
     * 태그 이름을 대문자로 통일한다.
     *
     * TABLE과 table처럼 대소문자만 다른 태그가
     * 서로 다른 태그로 집계되지 않게 한다.
     */
    private String normalizeTagName(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            throw datasetException(
                    "XML 태그 이름이 비어 있습니다."
            );
        }

        return tagName
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    /**
     * 줄바꿈과 여러 개의 공백을 하나의 공백으로 정리한다.
     */
    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    private void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }

        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            /*
             * InputStream은 try-with-resources로 닫힌다.
             * XMLStreamReader 종료 실패는 원래 분석 결과나
             * 원래 발생한 예외를 덮어쓰지 않게 한다.
             */
        }
    }

    private BusinessException datasetException(
            String message
    ) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message
        );
    }

    /**
     * XML 문법에 맞지 않는 단독 &를 읽는 동안에만 &amp;로 보정한다.
     *
     * 원본 파일과 DB 데이터는 변경하지 않는다.
     */
    private static final class BareAmpersandEscapingReader extends Reader {

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


        private BareAmpersandEscapingReader(Reader delegate) {
            this.delegate =
                    new BufferedReader(
                            Objects.requireNonNull(
                                    delegate,
                                    "delegate는 필수입니다."
                            )
                    );
        }

        @Override
        public int read(
                char[] target,
                int offset,
                int length
        ) throws IOException {
            Objects.checkFromIndexSize(
                    offset,
                    length,
                    target.length
            );

            if (length == 0) {
                return 0;
            }

            int writtenLength = 0;

            while (writtenLength < length) {
                if (
                        currentPosition
                                >= currentBuffer.length()
                ) {
                    if (!loadNextLine()) {
                        break;
                    }
                }

                int availableLength =
                        currentBuffer.length()
                                - currentPosition;

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


        private long getRepairedAmpersandCount() {
            return repairedAmpersandCount;
        }

        private long getRepairedLessThanCount() {
            return repairedLessThanCount;
        }

        private long getRepairedAttributeQuoteCount() {
            return repairedAttributeQuoteCount;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
