package com.foliolens.backend.disclosure.infrastructure.profiling;

import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlInputFactoryProvider;
import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlSanitizingReader;
import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlSourceFileValidator;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
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

    private static final Pattern SECTION_TAG_PATTERN =
            Pattern.compile(
                    "^SECTION-(\\d+)$"
            );
    // 이미지 후보 태그
    private static final Pattern IMAGE_CANDIDATE_PATTERN =
            Pattern.compile(
                    "^(IMG|IMAGE|PICTURE|FIGURE|GRAPHIC)"
                            + "([_-].*)?$"
            );
    // 각주·주석 후보 태그
    private static final Pattern NOTE_CANDIDATE_PATTERN =
            Pattern.compile(
                    "^(NOTE|FOOTNOTE|FOOT-NOTE|ANNOTATION)"
                            + "([_-].*)?$"
            );
    // 줄바꿈 후보
    private static final Set<String> LINE_BREAK_TAGS =
            Set.of(
                    "BR",
                    "LINEBREAK",
                    "LINE-BREAK"
            );

    private static final String DOCUMENT_NAME_TAG = "DOCUMENT-NAME";
    private final DartXmlInputFactoryProvider inputFactoryProvider;
    private final DartXmlSourceFileValidator sourceFileValidator;

    public XmlStructureProfiler(DartXmlInputFactoryProvider inputFactoryProvider, DartXmlSourceFileValidator sourceFileValidator) {
        this.inputFactoryProvider = inputFactoryProvider;
        this.sourceFileValidator = sourceFileValidator;
    }

    /**
     * XML 원문 파일 하나를 읽고 구조 요약을 반환한다.
     *
     * XML 전체 내용을 메모리에 올리지 않고
     * StAX를 이용해 앞에서부터 순서대로 읽는다.
     */
    public XmlStructureProfile profile(Path sourceFile) {
        Path normalizedFile = sourceFileValidator.validate(sourceFile);

        long fileSizeBytes = readFileSize(normalizedFile);

        Map<String, Long> tagCounts = new HashMap<>();

        Map<Integer, Long> sectionLevelCounts = new HashMap<>();

        Map<String, Long> imageCandidateTagCounts = new HashMap<>();

        Map<String, Long> noteCandidateTagCounts = new HashMap<>();

        int maxSectionLevel = 0;

        int currentTableDepth = 0;
        int maxTableDepth = 0;

        int currentTitleDepth = 0;

        long nestedTableCount = 0;
        long paragraphInsideTitleCount = 0;
        long paragraphInsideTableCount = 0;
        long titleInsideTableCount = 0;
        long lineBreakTagCount = 0;
        long xmlCommentCount = 0;

        long repairedAttributeQuoteCount = 0;

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
                DartXmlSanitizingReader xmlReader =
                        new DartXmlSanitizingReader(
                                Files.newBufferedReader(
                                        normalizedFile,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            XMLInputFactory inputFactory = inputFactoryProvider.create();

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

                    Matcher sectionMatcher = SECTION_TAG_PATTERN.matcher(tagName);

                    // SECTION 단계 조사
                    if (sectionMatcher.matches()) {
                        int sectionLevel =
                                Integer.parseInt(
                                        sectionMatcher.group(1)
                                );

                        sectionLevelCounts.merge(
                                sectionLevel,
                                1L,
                                Long::sum
                        );

                        maxSectionLevel =
                                Math.max(
                                        maxSectionLevel,
                                        sectionLevel
                                );
                    }

                    // 중첩 표 조사
                    if ("TABLE".equals(tagName)) {
                        if (currentTableDepth > 0) {
                            nestedTableCount++;
                        }

                        currentTableDepth++;

                        maxTableDepth =
                                Math.max(
                                        maxTableDepth,
                                        currentTableDepth
                                );
                    }

                    // TITLE 내부 구조 조사
                    if ("TITLE".equals(tagName)) {
                        if (currentTableDepth > 0) {
                            titleInsideTableCount++;
                        }

                        currentTitleDepth++;
                    }

                    if ("P".equals(tagName)) {
                        if (currentTitleDepth > 0) {
                            paragraphInsideTitleCount++;
                        }

                        if (currentTableDepth > 0) {
                            paragraphInsideTableCount++;
                        }
                    }

                    // 줄바꿈 태그 조사
                    if (LINE_BREAK_TAGS.contains(tagName)) {
                        lineBreakTagCount++;
                    }

                    // 이미지 후보 태그 조사
                    if (IMAGE_CANDIDATE_PATTERN
                            .matcher(tagName)
                            .matches()) {

                        imageCandidateTagCounts.merge(
                                tagName,
                                1L,
                                Long::sum
                        );
                    }

                    // 각주 후보 태그 조사
                    if (NOTE_CANDIDATE_PATTERN
                            .matcher(tagName)
                            .matches()) {

                        noteCandidateTagCounts.merge(
                                tagName,
                                1L,
                                Long::sum
                        );
                    }

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

                    if ("TITLE".equals(tagName)) {
                        currentTitleDepth--;

                        if (currentTitleDepth < 0) {
                            throw datasetException(
                                    "TITLE 깊이가 올바르지 않습니다. path="
                                            + normalizedFile
                            );
                        }
                    }

                    if ("TABLE".equals(tagName)) {
                        currentTableDepth--;

                        if (currentTableDepth < 0) {
                            throw datasetException(
                                    "TABLE 깊이가 올바르지 않습니다. path="
                                            + normalizedFile
                            );
                        }
                    }

                    currentDepth--;
                }

                if (eventType == XMLStreamConstants.COMMENT) {
                    xmlCommentCount++;
                    continue;
                }
            }

            if (currentTitleDepth != 0) {
                throw datasetException(
                        "종료되지 않은 TITLE이 있습니다. path="
                                + normalizedFile
                );
            }

            if (currentTableDepth != 0) {
                throw datasetException(
                        "종료되지 않은 TABLE이 있습니다. path="
                                + normalizedFile
                );
            }


            repairedAmpersandCount = xmlReader.getRepairedAmpersandCount();

            if (repairedAmpersandCount > 0) {
                log.warn(
                        "XML 문법에 맞지 않는 단독 &를 읽기 과정에서 보정했습니다. "
                                + "path={}, repairedCount={}",
                        normalizedFile,
                        repairedAmpersandCount
                );
            }

            repairedLessThanCount = xmlReader.getRepairedLessThanCount();

            if (repairedLessThanCount > 0) {
                log.warn(
                        "XML 문법에 맞지 않는 단독 <를 읽기 과정에서 보정했습니다. "
                                + "path={}, repairedCount={}",
                        normalizedFile,
                        repairedLessThanCount
                );
            }

            repairedAttributeQuoteCount = xmlReader.getRepairedAttributeQuoteCount();

            if (repairedAttributeQuoteCount > 0) {
                log.warn(
                        "XML 속성 따옴표를 읽기 과정에서 보정했습니다. "
                                + "path={}, repairedCount={}",
                        normalizedFile,
                        repairedAttributeQuoteCount
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

        XmlAdditionalStructureProfile additionalStructure =
                new XmlAdditionalStructureProfile(
                        sectionLevelCounts,
                        maxSectionLevel,
                        nestedTableCount,
                        maxTableDepth,
                        paragraphInsideTitleCount,
                        paragraphInsideTableCount,
                        titleInsideTableCount,
                        lineBreakTagCount,
                        xmlCommentCount,
                        imageCandidateTagCounts,
                        noteCandidateTagCounts
                );

        return new XmlStructureProfile(
                normalizedFile
                        .getFileName()
                        .toString(),
                rootElementName,
                documentName,
                fileSizeBytes,
                maxDepth,
                tagCounts,
                additionalStructure,
                repairedAmpersandCount,
                repairedLessThanCount,
                repairedAttributeQuoteCount
        );
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
}
