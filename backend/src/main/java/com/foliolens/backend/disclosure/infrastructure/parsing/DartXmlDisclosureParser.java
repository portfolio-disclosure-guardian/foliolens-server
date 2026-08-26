package com.foliolens.backend.disclosure.infrastructure.parsing;

import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlInputFactoryProvider;
import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlSanitizingReader;
import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlSourceFileValidator;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XML 파일 하나를 처음부터 끝까지 읽으면서 다음과 같은 Java 객체 구조로 변환
 * DART XML 파일
 *   ↓
 * DartXmlSanitizingReader
 *   ↓
 * DartXmlDisclosureParser
 *   ↓
 * ParsedDisclosureDocument
 *   ├─ preambleBlocks
 *   └─ sections
 *       ├─ blocks
 *       └─ children
 */

/**
 * <예제 XML의 전체 처리 과정>
 * DOCUMENT 시작
 * → 별도 처리 없음
 *
 * DOCUMENT-NAME 시작
 * → documentNameCapture 생성
 *
 * "사업보고서" 문자
 * → documentNameCapture에 추가
 *
 * DOCUMENT-NAME 종료
 * → documentName = "사업보고서"
 *
 * SECTION-1 시작
 * → level=1인 SectionBuilder 생성
 * → sectionStack에 push
 *
 * TITLE 시작
 * → titleCapture 생성
 *
 * "II. 사업의 내용" 문자
 * → titleCapture에 추가
 *
 * TITLE 종료
 * → 현재 SECTION-1의 title로 설정
 *
 * P 시작
 * → paragraphCapture 생성
 *
 * "회사는 반도체를 생산합니다." 문자
 * → paragraphCapture에 추가
 *
 * P 종료
 * → PARAGRAPH 블록 생성
 * → 현재 SECTION-1의 blocks에 추가
 *
 * SECTION-1 종료
 * → SectionBuilder를 ParsedDisclosureSection으로 변환
 * → document.sections에 추가
 *
 * DOCUMENT 종료
 * → ParsedDisclosureDocument 반환
 */
@Component
public class DartXmlDisclosureParser {

    private final DartXmlInputFactoryProvider inputFactoryProvider;
    private final DartXmlSourceFileValidator sourceFileValidator;

    private static final Pattern SECTION_TAG_PATTERN =
            Pattern.compile("^SECTION-([1-9]\\d*)$");

    public DartXmlDisclosureParser(DartXmlInputFactoryProvider inputFactoryProvider, DartXmlSourceFileValidator sourceFileValidator) {
        this.inputFactoryProvider = inputFactoryProvider;
        this.sourceFileValidator = sourceFileValidator;
    }

    public ParsedDisclosureDocument parse(Path sourceFile) {
        // xml 파일 검증
        Path file = sourceFileValidator.validate(sourceFile);

        // 문서 전체 결과를 조립하는 builder
        // XML을 읽는 동안 결과가 조금씩 발견
        // 임시로 변경 가능한 DocumentBuilder에 결과를 모아둠
        DocumentBuilder documentBuilder = new DocumentBuilder(
                file.getFileName().toString()
        );

        // 현재 읽고 있는 장·절 구조를 기억
        // 스택 구조로 기억
        // sections.peek()은 현재 내용을 넣어야 할 가장 안쪽 섹션을 반환
        Deque<SectionBuilder> sectionStack = new ArrayDeque<>();

        // 현재 읽고 있는 표 구조를 기억
        // 스택구조로 기억
        Deque<TableBuilder> tableStack = new ArrayDeque<>();

        ImageBuilder imageBuilder = null;

        // 문자 수집기
        TextCapture documentNameCapture = null; // DOCUMENT-NAME 내용 수집
        TextCapture titleCapture = null;        // TITLE 내용 수집
        TextCapture paragraphCapture = null;    // P 태그 내용 수집

        XMLStreamReader xmlReader = null;

        try (
                // XML 보정 Reader
                DartXmlSanitizingReader sanitizingReader =
                        new DartXmlSanitizingReader(
                                Files.newBufferedReader(
                                        file,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            XMLInputFactory inputFactory = inputFactoryProvider.create();

            // StAX는 XML 파일을 앞에서부터 순서대로 읽는 스트리밍 파서
            // XML 전체를 메모리에 올리지 않고 이벤트를 하나씩 받는다
            // DOM처럼 전체 XML 트리를 메모리에 올리는 것보다 StAX가 안전
            xmlReader = inputFactory.createXMLStreamReader(sanitizingReader);

            // XML 이벤트 반복
            /*
               여기서 말하는 이벤트란
                DOCUMENT 시작
                DOCUMENT-NAME 시작
                문자열 발견
                DOCUMENT-NAME 종료
                SECTION-1 시작
                TITLE 시작
                문자열 발견
                TITLE 종료
                ...
             */
            while (xmlReader.hasNext()) {
                // 파서는 XML이 끝날 때까지 이벤트를 하나씩 읽음
                int event = xmlReader.next();

                // 중요한 이벤트는 3개
                /**
                 * 1. XMLStreamConstants.START_ELEMENT
                 *     태그가 시작됐다는 뜻 ex) <SECTION-1>
                 * 2. XMLStreamConstants.CHARACTERS
                 *     태그 내부의 문자열을 만났다는 뜻  ex) <P>회사는 반도체를 생산합니다.</P>
                 * 3. XMLStreamConstants.END_ELEMENT
                 *     태그가 끝났다는 뜻  ex) </SECTION-1>
                 */
                // 시작 태그를 만났을 때
                if (event == XMLStreamConstants.START_ELEMENT) {
                    // 시작 태그를 만나면 먼저 이름을 통일 -> 대문자로 통일
                    String tag = normalizeTag(xmlReader.getLocalName());

                    int line = currentLine(xmlReader);

                    // DOCUMENT-NAME만 문서명으로 사용
                    if ("DOCUMENT-NAME".equals(tag) && documentBuilder.documentName == null) {
                        documentNameCapture = new TextCapture(line);
                        continue;
                    }

                    // 섹션 태그인지 확인
                    Integer sectionLevel = parseSectionLevelOrNull(tag);

                    if (sectionLevel != null) {
                        SectionBuilder section =
                                new SectionBuilder(
                                        sectionLevel,
                                        documentBuilder.nextOrder(),
                                        line
                                );

                        sectionStack.push(section);
                        continue;
                    }

                    // 페이지 구분 처리
                    if ("PGBRK".equals(tag)) {

                        // 이미지 내부에서는 별도 페이지 구분으로 만들지 않음
                        if (imageBuilder != null) {
                            continue;
                        }

                        // 표 안에서 발견한 PGBRK 처리
                        if (!tableStack.isEmpty()) {
                            TableBuilder currentTable = tableStack.peek();

                            /*
                             * 현재 열린 TD 또는 TH가 있다면
                             * 셀 내부 줄바꿈으로만 보존한다.
                             */
                            if (currentTable.hasOpenCell()) {
                                currentTable.appendCellLineBreak();
                            }

                            // 셀 밖에서 발견했다면 레이아웃용 페이지 구분으로 보고 무시
                            continue;
                        }

                        /*
                         * 문단이나 제목 내부에 들어온 PGBRK라면
                         * 별도 블록으로 꺼내지 않고 문자 구분만 추가한다.
                         */
                        if (paragraphCapture != null) {
                            paragraphCapture.appendLineBreak();
                            continue;
                        }

                        if (titleCapture != null) {
                            titleCapture.append(" ");
                            continue;
                        }

                        // 일반 본문에 위치한 PGBRK는 PAGE_BREAK 블록으로 저장
                        addPageBreakBlock(
                                documentBuilder,
                                sectionStack,
                                line
                        );

                        continue;
                    }

                    // 이미지 태그를 만났을 때
                    if ("IMAGE".equals(tag)) {
                        if (imageBuilder != null) {
                            throw datasetException("IMAGE 태그가 중첩되었습니다. line=" + line + ", path=" + file);
                        }

                        /*
                         * 표 안의 이미지라면 반드시 열린 TH 또는 TD 안이어야 한다.
                         */
                        if (!tableStack.isEmpty()
                                && !tableStack.peek().hasOpenCell()) {
                            throw datasetException(
                                    "IMAGE가 표 셀 밖에서 시작됐습니다. "
                                            + "line=" + line
                                            + ", path=" + file
                            );
                        }

                        imageBuilder = new ImageBuilder(line);
                        continue;
                    }

                    if ("IMG".equals(tag) && imageBuilder != null) {
                        Integer width = parseNullablePositiveIntegerAttribute(xmlReader, "WIDTH");

                        Integer height = parseNullablePositiveIntegerAttribute(xmlReader, "HEIGHT");

                        String alignment = readNullableAttribute(xmlReader, "ALIGN");

                        imageBuilder.startFileName(
                                line,
                                width,
                                height,
                                alignment
                        );

                        continue;
                    }

                    if ("IMG-CAPTION".equals(tag) && imageBuilder != null) {
                        imageBuilder.startCaption(line);
                        continue;
                    }

                    // TABLE 태그를 만났을 때
                    if ("TABLE".equals(tag)) {

                        /*
                         * 이미 표를 읽는 중이라면 중첩 표
                         * 중첩 표는 부모 표의 현재 셀 안에 있어야 함
                         */
                        if (!tableStack.isEmpty() && !tableStack.peek().hasOpenCell()) { // 스택구조로 표 구조를 기억중임
                            throw datasetException(
                                    "중첩 TABLE이 셀 밖에서 시작됐습니다. "
                                            + "line=" + line
                                            + ", path=" + file
                            );
                        }

                        /*
                         * 중첩 표라면 부모 셀의 현재 텍스트 위치를 먼저 기록한다.
                         * 이후 부모 셀이 닫힐 때 이 위치를 기준으로 표 앞·뒤의
                         * 직접 텍스트 문맥을 계산한다.
                         */
                        if (!tableStack.isEmpty()) {
                            tableStack.peek().startNestedTable();
                        }

                        TableBuilder tableBuilder =
                                new TableBuilder(
                                        documentBuilder.nextTableOrder(),
                                        line
                                );

                        tableStack.push(tableBuilder);
                        continue;
                    }

                    /*
                     * 현재 표 안에서 발생한 태그 처리.
                     */
                    if (!tableStack.isEmpty()) {
                        TableBuilder currentTable = tableStack.peek();

                        // TR 태그를 만났을 때
                        if ("TR".equals(tag)) {
                            currentTable.startRow(line); // RowBuilder 생성
                            continue;
                        }

                        // TH 또는 TD 태그 만났을 때
                        if ("TH".equals(tag) || "TD".equals(tag)) {
                            // TH이면 HEADER, TD이면 DATA로 변환
                            ParsedDisclosureTableCellType cellType =
                                    ParsedDisclosureTableCellType.fromXmlTag(tag);

                            int rowSpan = parsePositiveIntegerAttribute(
                                            xmlReader,
                                            "ROWSPAN",
                                            1
                                    );

                            int colSpan = parsePositiveIntegerAttribute(
                                            xmlReader,
                                            "COLSPAN",
                                            1
                                    );

                            currentTable.startCell( // CellBuilder 실행
                                    cellType,
                                    rowSpan,
                                    colSpan,
                                    line
                            );

                            continue;
                        }

                        /*
                         * P 자체는 별도 PARAGRAPH 블록으로 만들지 않는다.
                         * 종료 시 셀 안에 줄바꿈만 추가한다.
                         */
                        if ("P".equals(tag)) {
                            continue;
                        }

                        /*
                         * 표 내부의 기타 태그는 구조 객체로 만들지 않는다.
                         * 내부 문자 데이터는 CHARACTERS 이벤트에서 수집한다.
                         */
                        continue;
                    }

                    // 제목 시작태그를 만났을 때
                    if ("TITLE".equals(tag)) {
                        titleCapture = new TextCapture(line);
                        continue;
                    }

                    // P 시작태그 만났을 때
                    if ("P".equals(tag)) {
                        paragraphCapture = new TextCapture(line);
                    }

                    continue;
                }

                // 문자 이벤트 처리
                if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {

                    String text = xmlReader.getText();

                    if (documentNameCapture != null) {
                        documentNameCapture.append(text);
                    }

                    if (imageBuilder != null) {
                        imageBuilder.appendText(text);
                        continue;
                    }

                    if (!tableStack.isEmpty()) {
                        TableBuilder currentTable = tableStack.peek();

                        /*
                         * TR 사이의 들여쓰기 공백은 무시한다.
                         * 현재 열린 TH 또는 TD가 있을 때만 문자를 수집한다.
                         */
                        if (currentTable.hasOpenCell()) {
                            currentTable.appendCellText(text);
                        }

                        continue;
                    }

                    if (titleCapture != null) {
                        titleCapture.append(text);
                    }

                    if (paragraphCapture != null) {
                        paragraphCapture.append(text);
                    }

                    continue;
                }

                // 끝 태그를 만났을 때
                if (event != XMLStreamConstants.END_ELEMENT) {
                    continue;
                }

                String tag = normalizeTag(xmlReader.getLocalName());

                int line = currentLine(xmlReader);

                // 문서명 종료
                if ("DOCUMENT-NAME".equals(tag) && documentNameCapture != null) {

                    // documentNameCapture에 쌓여있는 document 이름을 documentName에 저장
                    documentBuilder.documentName = normalizeText(documentNameCapture.text());

                    documentNameCapture = null;
                    continue;
                }

                if ("IMG".equals(tag) && imageBuilder != null) {
                    imageBuilder.closeFileName();
                    continue;
                }

                if ("IMG-CAPTION".equals(tag) && imageBuilder != null) {
                    imageBuilder.closeCaption();
                    continue;
                }

                if ("IMAGE".equals(tag) && imageBuilder != null) {
                    ParsedDisclosureImage image = imageBuilder.build(line);

                    /*
                     * 표 안에 있다면 현재 셀의 이미지로 저장한다.
                     */
                    if (!tableStack.isEmpty()) {
                        tableStack.peek().addImage(image);
                    } else {
                        /*
                         * 일반 본문 이미지라면 IMAGE 블록으로 저장한다.
                         */
                        addImageBlock(
                                documentBuilder,
                                sectionStack,
                                image
                        );
                    }

                    imageBuilder = null;
                    continue;
                }

                if (!tableStack.isEmpty()) {
                    TableBuilder currentTable = tableStack.peek();

                    // 셀 안에서 P 하나가 끝났으면 줄바꿈을 보존한다.
                    if ("P".equals(tag)) {
                        currentTable.appendCellLineBreak();
                        continue;
                    }

                    // TH 또는 TD 종료
                    if ("TH".equals(tag) || "TD".equals(tag)) {
                        currentTable.closeCell(line);
                        continue;
                    }

                    // TR 종료
                    if ("TR".equals(tag)) {
                        currentTable.closeRow(line);
                        continue;
                    }

                    // TABLE 종료
                    if ("TABLE".equals(tag)) {
                        TableBuilder completedBuilder =
                                tableStack.pop();

                        ParsedDisclosureTable completedTable =
                                completedBuilder.build(line);

                        // 부모 TABLE이 남아 있다면 중첩 표
                        if (!tableStack.isEmpty()) {
                            tableStack.peek()
                                    .addNestedTable(completedTable);

                            continue;
                        }

                        // 부모 TABLE이 없다면 일반 본문 표
                        addTableBlock(
                                documentBuilder,
                                sectionStack,
                                completedTable
                        );

                        continue;
                    }

                    // 표 내부에서 사용된 기타 종료 태그는 무시
                    continue;
                }

                // 제목 종료
                if ("TITLE".equals(tag) && titleCapture != null) {

                    String title = normalizeText(titleCapture.text());

                    if (title != null) {
                        addTitle(
                                documentBuilder,
                                sectionStack,
                                title,
                                titleCapture.startLine(),
                                line
                        );
                    }

                    titleCapture = null;
                    continue;
                }

                // 문단 종료
                // 수집한 문자열로 PARAGRAPH 블록을 만듦
                if ("P".equals(tag) && paragraphCapture != null) {

                    addTextBlock(
                            documentBuilder,
                            sectionStack,
                            ParsedDisclosureBlockType.PARAGRAPH,
                            paragraphCapture.text(),
                            paragraphCapture.startLine(),
                            line
                    );

                    paragraphCapture = null;
                    continue;
                }

                if (parseSectionLevelOrNull(tag) != null) {
                    closeSection(
                            documentBuilder,
                            sectionStack,
                            line
                    );
                }
            }

            if (!sectionStack.isEmpty()) {
                throw datasetException(
                        "종료되지 않은 SECTION 태그가 있습니다. path=" + file
                );
            }

            if (!tableStack.isEmpty()) {
                throw datasetException(
                        "종료되지 않은 TABLE 태그가 있습니다. path=" + file
                );
            }

            if (imageBuilder != null) {
                throw datasetException(
                        "종료되지 않은 IMAGE 태그가 있습니다. path=" + file
                );
            }

            return documentBuilder.build();
        } catch (IOException | XMLStreamException | IllegalStateException | IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "DART XML 원문을 파싱하지 못했습니다. path=" + file,
                    exception
            );
        } finally {
            closeQuietly(xmlReader);
        }
    }

    // 제목 처리
    // 현재 섹션의 첫 번째 제목: 섹션 대표 제목
    // 두 번째 이후 제목: HEADING 블록
    private void addTitle(
            DocumentBuilder document,
            Deque<SectionBuilder> sections,
            String title,
            int startLine,
            int endLine
    ) {
        /*
         * SECTION 안에서 처음 발견한 TITLE은 섹션 제목으로 사용한다.
         * 그 이후 TITLE은 본문 안의 추가 소제목으로 보존한다.
         */
        if (!sections.isEmpty()
                && sections.peek().title == null) {

            sections.peek().title = title;
            return;
        }

        addTextBlock(
                document,
                sections,
                ParsedDisclosureBlockType.HEADING,
                title,
                startLine,
                endLine
        );
    }

    /**
     * 텍스트 파싱 결과를 블록으로 만듦
     */
    private void addTextBlock(
            DocumentBuilder document,
            Deque<SectionBuilder> sections,
            ParsedDisclosureBlockType type,
            String content,
            int startLine,
            int endLine
    ) {
        if (type == ParsedDisclosureBlockType.TABLE) {
            throw new IllegalArgumentException(
                    "TABLE은 addTableBlock()으로 추가해야 합니다."
            );
        }

        String normalized = normalizeText(content);

        if (normalized == null) {
            return;
        }

        ParsedDisclosureBlock block =
                ParsedDisclosureBlock.text(
                        type,
                        document.nextOrder(),
                        normalized,
                        startLine,
                        endLine
                );

        attachBlock(
                document,
                sections,
                block
        );
    }

    /**
     * 표 파싱 결과를 블럭으로 만듦
     */
    private void addTableBlock(
            DocumentBuilder document,
            Deque<SectionBuilder> sections,
            ParsedDisclosureTable table
    ) {
        ParsedDisclosureBlock block =
                ParsedDisclosureBlock.table(
                        document.nextOrder(),
                        table
                );

        attachBlock(
                document,
                sections,
                block
        );
    }

    private void addImageBlock(
            DocumentBuilder document,
            Deque<SectionBuilder> sections,
            ParsedDisclosureImage image
    ) {
        ParsedDisclosureBlock block =
                ParsedDisclosureBlock.image(
                        document.nextOrder(),
                        image
                );

        attachBlock(
                document,
                sections,
                block
        );
    }

    private void addPageBreakBlock(
            DocumentBuilder document,
            Deque<SectionBuilder> sections,
            int sourceLine
    ) {
        // 연속된 PAGE_BREAK는 하나만 저장
        ParsedDisclosureBlock lastBlock = findLastBlock(document, sections);

        if (lastBlock != null && lastBlock.type() == ParsedDisclosureBlockType.PAGE_BREAK) {
            return;
        }

        ParsedDisclosureBlock block =
                ParsedDisclosureBlock.pageBreak(
                        document.nextOrder(),
                        sourceLine
                );

        attachBlock(
                document,
                sections,
                block
        );
    }

    // 연속 중복을 검사하는 메서드
    // 원문이
    // <PGBRK></PGBRK>
    // <PGBRK></PGBRK>
    // 다음과 같아도 결과에는 하나만 들어감
    private ParsedDisclosureBlock findLastBlock(
            DocumentBuilder document,
            Deque<SectionBuilder> sections
    ) {
        List<ParsedDisclosureBlock> blocks =
                sections.isEmpty()
                        ? document.preambleBlocks
                        : sections.peek().blocks;

        if (blocks.isEmpty()) {
            return null;
        }

        return blocks.get(blocks.size() - 1);
    }

    // block 처리 두가지
    // 1. section 안에 들어가 있는 블럭이 아니라면 document의 preambleBlocks에 추가
    // 2. section 안에 들어가 있다면 section의 block에 추가
    private void attachBlock(
            DocumentBuilder document,
            Deque<SectionBuilder> sections,
            ParsedDisclosureBlock block
    ) {
        if (sections.isEmpty()) {
            document.preambleBlocks.add(block);
            return;
        }

        sections.peek().blocks.add(block);
    }

    /**
     * XML 태그에서 특정 속성값을 찾아서 1 이상의 int로 변환하는 메서드
     * 표 셀의 다음 두 속성을 읽는 데 사용
     * <TH ROWSPAN="2">구분</TH>
     * <TH COLSPAN="4">연결대상회사수</TH>
     * 파싱 결과:
     *    ROWSPAN="2" → int 2
     *    COLSPAN="4" → int 4
     * 속성이 없는 경우에는 기본값을 반환
     */
    private int parsePositiveIntegerAttribute(
            XMLStreamReader reader,
            String attributeName, // COLSPAN 또는 ROWSPAN 넘어옴
            int defaultValue
    ) {
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            String currentName = normalizeTag(reader.getAttributeLocalName(index));

            if (!attributeName.equals(currentName)) {
                continue;
            }

            String rawValue = reader.getAttributeValue(index);

            try {
                int parsed = Integer.parseInt(rawValue.trim());

                if (parsed < 1) {
                    throw datasetException(
                            attributeName
                                    + "은 1 이상이어야 합니다. "
                                    + "value=" + rawValue
                                    + ", line=" + currentLine(reader)
                    );
                }

                return parsed;
            } catch (NumberFormatException exception) {
                throw datasetException(
                        attributeName
                                + "을 숫자로 변환할 수 없습니다. "
                                + "value=" + rawValue
                                + ", line=" + currentLine(reader)
                );
            }
        }

        return defaultValue;
    }

    private Integer parseNullablePositiveIntegerAttribute(
            XMLStreamReader reader,
            String attributeName
    ) {
        String rawValue =
                readNullableAttribute(reader, attributeName);

        if (rawValue == null) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(rawValue);

            if (parsed < 1) {
                throw datasetException(
                        attributeName
                                + "은 1 이상이어야 합니다. "
                                + "value=" + rawValue
                                + ", line=" + currentLine(reader)
                );
            }

            return parsed;
        } catch (NumberFormatException exception) {
            throw datasetException(
                    attributeName
                            + "을 숫자로 변환할 수 없습니다. "
                            + "value=" + rawValue
                            + ", line=" + currentLine(reader)
            );
        }
    }

    private String readNullableAttribute(
            XMLStreamReader reader,
            String attributeName
    ) {
        for (int index = 0;
             index < reader.getAttributeCount();
             index++) {

            String currentName =
                    normalizeTag(
                            reader.getAttributeLocalName(index)
                    );

            if (!attributeName.equals(currentName)) {
                continue;
            }

            String value = reader.getAttributeValue(index);

            if (value == null || value.isBlank()) {
                return null;
            }

            return value.trim();
        }

        return null;
    }

    /**
     * 섹션 종료 처리
     * 1. 상위 섹션이 존재한다면 - 현재 섹션의 children에 추가
     * 2. 상위 섹션이 존재하지 않는다면 - document 섹션에 추가
     */
    private void closeSection(
            DocumentBuilder document,
            Deque<SectionBuilder> sections,
            int endLine
    ) {
        if (sections.isEmpty()) {
            throw datasetException(
                    "시작 태그가 없는 SECTION 종료 태그가 있습니다."
            );
        }

        SectionBuilder completed = sections.pop();

        ParsedDisclosureSection section =
                completed.build(endLine);

        // 상위 섹션이 존재하지 않는다면
        if (sections.isEmpty()) {
            document.sections.add(section);
            return;
        }

        // 상위 섹션이 존재하지 않는 경우
        sections.peek().children.add(section);
    }

    /**
     * Section 레벨 태그 처리
     * SECTION-1 → 1
     * SECTION-4 → 4
     * SECTION-15 → 15
     * SECTION-0 → 섹션으로 인식하지 않음
     * SECTION-A → 섹션으로 인식하지 않음
     */
    private Integer parseSectionLevelOrNull(String tag) {
        Matcher matcher = SECTION_TAG_PATTERN.matcher(tag);

        if (!matcher.matches()) {
            return null;
        }

        return Integer.parseInt(matcher.group(1));
    }

    // 태그 이름 통일 -> 대문자로 통일
    private String normalizeTag(String tag) {
        return tag.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isEmpty() ? null : normalized;
    }

    private int currentLine(XMLStreamReader reader) {
        return reader.getLocation() == null
                ? -1
                : reader.getLocation().getLineNumber();
    }

    private void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }

        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // 원래 파싱 결과를 덮어쓰지 않는다.
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
     * 문자 수집기
     * <P>회사는 <B>반도체</B>를 생산합니다.</P>
     * 이러한 xml 이 있다고 했을 때
     * "회사는 "
     * "반도체"
     * "를 생산합니다."
     * 다음처럼 나눠서 전달할 수 있음
     * 그래서 stringBuilder로 계쏙 합쳐줘야함
     */
    private static final class TextCapture {

        private final int startLine;
        private final StringBuilder buffer = new StringBuilder();

        private TextCapture(int startLine) {
            this.startLine = startLine;
        }

        private void append(String text) {
            buffer.append(text);
        }

        private String text() {
            return buffer.toString();
        }

        private int startLine() {
            return startLine;
        }

        private void appendLineBreak() {
            if (buffer.isEmpty()) {
                return;
            }

            int lastIndex = buffer.length() - 1;

            if (buffer.charAt(lastIndex) != '\n') {
                buffer.append('\n');
            }
        }
    }

    /**
     * ParsedDisclosureDocument는 불변 객체이기 때문에 읽는 도중 계속 수정하기 어려움
     * 그래서 임시로 변경 가능한 DocumentBuilder에 결과를 모아둠
     */
    private static final class DocumentBuilder {

        private final String fileName;
        private final List<ParsedDisclosureBlock> preambleBlocks = new ArrayList<>();
        private final List<ParsedDisclosureSection> sections = new ArrayList<>();
        private String documentName;
        private int sequence;
        private int tableSequence;

        private DocumentBuilder(String fileName) {
            this.fileName = fileName;
        }

        private int nextOrder() {
            return ++sequence;
        }

        private int nextTableOrder() {
            return ++tableSequence;
        }

        private ParsedDisclosureDocument build() {
            return new ParsedDisclosureDocument(
                    fileName,
                    documentName,
                    preambleBlocks,
                    sections
            );
        }
    }

    private static final class SectionBuilder {

        private final int level;
        private final int order;
        private final int startLine;

        private String title;

        private final List<ParsedDisclosureBlock> blocks = new ArrayList<>();

        private final List<ParsedDisclosureSection> children = new ArrayList<>();

        private SectionBuilder(
                int level,
                int order,
                int startLine
        ) {
            this.level = level;
            this.order = order;
            this.startLine = startLine;
        }

        private ParsedDisclosureSection build(int endLine) {
            return new ParsedDisclosureSection(
                    level,
                    order,
                    title,
                    startLine,
                    endLine,
                    blocks,
                    children
            );
        }
    }

    private static final class TableBuilder {

        private final int order;
        private final int startLine;

        private final List<ParsedDisclosureTableRow> rows = new ArrayList<>();

        private RowBuilder currentRow;

        private TableBuilder(int order, int startLine) {
            this.order = order;
            this.startLine = startLine;
        }

        private void startRow(int line) {
            if (currentRow != null) {
                throw new IllegalStateException(
                        "이전 TR이 종료되기 전에 새로운 TR이 시작됐습니다. line=" + line
                );
            }

            currentRow = new RowBuilder(rows.size(), line);
        }

        private void startCell(
                ParsedDisclosureTableCellType type,
                int rowSpan,
                int colSpan,
                int line
        ) {
            if (currentRow == null) {
                throw new IllegalStateException(
                        "TR 밖에서 TH 또는 TD가 시작됐습니다. "
                                + "line=" + line
                );
            }

            currentRow.startCell(
                    type,
                    rowSpan,
                    colSpan,
                    line
            );
        }

        private boolean hasOpenCell() {
            return currentRow != null && currentRow.hasOpenCell();
        }

        private void appendCellText(String text) {
            if (currentRow != null) {
                currentRow.appendCellText(text);
            }
        }

        private void appendCellLineBreak() {
            if (currentRow != null) {
                currentRow.appendCellLineBreak();
            }
        }

        private void addNestedTable(ParsedDisclosureTable nestedTable) {
            if (!hasOpenCell()) {
                throw new IllegalStateException(
                        "중첩 표를 추가할 현재 셀이 없습니다."
                );
            }

            currentRow.addNestedTable(nestedTable);
        }

        private void startNestedTable() {
            if (!hasOpenCell()) {
                throw new IllegalStateException(
                        "중첩 표를 시작할 현재 셀이 없습니다."
                );
            }

            currentRow.startNestedTable();
        }

        private void addImage(ParsedDisclosureImage image) {
            if (!hasOpenCell()) {
                throw new IllegalStateException(
                        "이미지는 열린 표 셀 안에 있어야 합니다."
                );
            }

            currentRow.addImage(image);
        }

        private void closeCell(int endLine) {
            if (currentRow == null) {
                throw new IllegalStateException(
                        "종료할 셀이 있는 TR이 없습니다."
                );
            }

            currentRow.closeCell(endLine);
        }

        private void closeRow(int endLine) {
            if (currentRow == null) {
                throw new IllegalStateException(
                        "종료할 TR이 없습니다."
                );
            }

            if (currentRow.hasOpenCell()) {
                throw new IllegalStateException(
                        "열린 셀이 있는 상태에서 TR이 종료됐습니다. "
                                + "line=" + endLine
                );
            }

            rows.add(currentRow.build(endLine));

            currentRow = null;
        }

        private ParsedDisclosureTable build(int endLine) {
            if (currentRow != null) {
                throw new IllegalStateException(
                        "열린 TR이 있는 상태에서 TABLE이 종료됐습니다. "
                                + "line=" + endLine
                );
            }

            return new ParsedDisclosureTable(
                    order,
                    startLine,
                    endLine,
                    rows
            );
        }
    }

    private static final class RowBuilder {

        private final int rowIndex;
        private final int startLine;

        private final List<ParsedDisclosureTableCell> cells = new ArrayList<>();

        private CellBuilder currentCell;

        private RowBuilder(int rowIndex, int startLine) {
            this.rowIndex = rowIndex;
            this.startLine = startLine;
        }

        private void startCell(
                ParsedDisclosureTableCellType type,
                int rowSpan,
                int colSpan,
                int line
        ) {
            if (currentCell != null) {
                throw new IllegalStateException(
                        "이전 셀이 종료되기 전에 새로운 셀이 시작됐습니다. "
                                + "line=" + line
                );
            }

            currentCell = new CellBuilder(
                            cells.size(),
                            type,
                            rowSpan,
                            colSpan,
                            line
                    );
        }

        private boolean hasOpenCell() {
            return currentCell != null;
        }

        private void appendCellText(String text) {
            if (currentCell != null) {
                currentCell.appendText(text);
            }
        }

        private void appendCellLineBreak() {
            if (currentCell != null) {
                currentCell.appendLineBreak();
            }
        }

        private void addNestedTable(ParsedDisclosureTable nestedTable) {
            if (currentCell == null) {
                throw new IllegalStateException(
                        "중첩 표를 추가할 셀이 없습니다."
                );
            }

            currentCell.addNestedTable(nestedTable);
        }

        private void startNestedTable() {
            if (currentCell == null) {
                throw new IllegalStateException(
                        "중첩 표를 시작할 셀이 없습니다."
                );
            }

            currentCell.startNestedTable();
        }

        private void addImage(ParsedDisclosureImage image) {
            if (currentCell == null) {
                throw new IllegalStateException(
                        "이미지를 추가할 현재 셀이 없습니다."
                );
            }

            currentCell.addImage(image);
        }

        private void closeCell(int endLine) {
            if (currentCell == null) {
                throw new IllegalStateException(
                        "종료할 셀이 없습니다."
                );
            }

            cells.add(currentCell.build(endLine));

            currentCell = null;
        }

        private ParsedDisclosureTableRow build(int endLine) {
            return new ParsedDisclosureTableRow(
                    rowIndex,
                    startLine,
                    endLine,
                    cells
            );
        }
    }

    private static final class CellBuilder {

        private final int cellIndex;
        private final ParsedDisclosureTableCellType type;
        private final int rowSpan;
        private final int colSpan;
        private final int startLine;

        private final StringBuilder textBuffer = new StringBuilder();

        private final List<NestedTableCapture> nestedTableCaptures =
                new ArrayList<>();
        private final List<ParsedDisclosureImage> images = new ArrayList<>();

        /*
         * 직전에 끝난 중첩 표 이후부터 현재 위치까지가
         * 다음 중첩 표의 precedingText가 된다.
         */
        private int adjacentTextStartOffset;
        private NestedTableStart pendingNestedTable;

        private CellBuilder(
                int cellIndex,
                ParsedDisclosureTableCellType type,
                int rowSpan,
                int colSpan,
                int startLine
        ) {
            this.cellIndex = cellIndex;
            this.type = type;
            this.rowSpan = rowSpan;
            this.colSpan = colSpan;
            this.startLine = startLine;
        }

        private void appendText(String text) {
            if (text != null) {
                textBuffer.append(text);
            }
        }

        private void appendLineBreak() {
            if (textBuffer.isEmpty()) {
                return;
            }

            int lastIndex = textBuffer.length() - 1;

            if (textBuffer.charAt(lastIndex) != '\n') {
                textBuffer.append('\n');
            }
        }

        private void addNestedTable(ParsedDisclosureTable nestedTable) {
            if (pendingNestedTable == null) {
                throw new IllegalStateException(
                        "시작 위치가 기록되지 않은 중첩 표입니다."
                );
            }

            int followingTextStartOffset = textBuffer.length();

            nestedTableCaptures.add(
                    new NestedTableCapture(
                            Objects.requireNonNull(
                                    nestedTable,
                                    "nestedTable은 필수입니다."
                            ),
                            pendingNestedTable.precedingText(),
                            pendingNestedTable.textStartOffset(),
                            followingTextStartOffset
                    )
            );

            adjacentTextStartOffset = followingTextStartOffset;
            pendingNestedTable = null;
        }

        private void startNestedTable() {
            if (pendingNestedTable != null) {
                throw new IllegalStateException(
                        "이전 중첩 표가 종료되기 전에 새로운 중첩 표가 시작됐습니다."
                );
            }

            int textStartOffset = textBuffer.length();

            pendingNestedTable = new NestedTableStart(
                    textStartOffset,
                    normalizeTableCellText(
                            textBuffer.substring(
                                    adjacentTextStartOffset,
                                    textStartOffset
                            )
                    )
            );
        }

        private void addImage(ParsedDisclosureImage image) {
            images.add(
                    Objects.requireNonNull(
                            image,
                            "image는 필수입니다."
                    )
            );
        }

        private ParsedDisclosureTableCell build(int endLine) {
            if (pendingNestedTable != null) {
                throw new IllegalStateException(
                        "종료되지 않은 중첩 표가 있는 상태에서 셀이 종료됐습니다."
                );
            }

            return new ParsedDisclosureTableCell(
                    cellIndex,
                    type,
                    rowSpan,
                    colSpan,
                    normalizeTableCellText(
                            textBuffer.toString()
                    ),
                    startLine,
                    endLine,
                    buildNestedTablesWithContext(),
                    images
            );
        }

        private List<ParsedDisclosureTable> buildNestedTablesWithContext() {
            List<ParsedDisclosureTable> nestedTables = new ArrayList<>(
                    nestedTableCaptures.size()
            );

            for (int index = 0;
                 index < nestedTableCaptures.size();
                 index++) {
                NestedTableCapture capture = nestedTableCaptures.get(index);

                int followingTextEndOffset =
                        index + 1 < nestedTableCaptures.size()
                                ? nestedTableCaptures.get(index + 1)
                                        .textStartOffset()
                                : textBuffer.length();

                String followingText = normalizeTableCellText(
                        textBuffer.substring(
                                capture.followingTextStartOffset(),
                                followingTextEndOffset
                        )
                );

                nestedTables.add(
                        capture.table().withParentContext(
                                new ParsedDisclosureTableContext(
                                        capture.precedingText(),
                                        followingText
                                )
                        )
                );
            }

            return nestedTables;
        }

        private record NestedTableStart(
                int textStartOffset,
                String precedingText
        ) {
        }

        private record NestedTableCapture(
                ParsedDisclosureTable table,
                String precedingText,
                int textStartOffset,
                int followingTextStartOffset
        ) {
        }
    }

    private static String normalizeTableCellText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedNewLines =
                value.replace("\r\n", "\n")
                        .replace('\r', '\n');

        return normalizedNewLines
                .lines()
                /*
                 * 한 줄 내부의 연속 공백은 하나로 만든다.
                 */
                .map(line ->
                        line.replaceAll("[\\t\\f ]+", " ")
                                .trim()
                )
                /*
                 * XML 들여쓰기에서 생긴 빈 줄은 제거한다.
                 */
                .filter(line -> !line.isBlank())
                /*
                 * P 단위 줄바꿈은 유지한다.
                 */
                .reduce(
                        (left, right) ->
                                left + "\n" + right
                )
                .orElse(null);
    }

    public enum ImageTextTarget {
        NONE,
        FILE_NAME,
        CAPTION
    }

    private static final class ImageBuilder {

        private final int startLine;

        private TextCapture fileNameCapture;
        private TextCapture captionCapture;

        private Integer width;
        private Integer height;
        private String alignment;

        private ImageTextTarget textTarget = ImageTextTarget.NONE;

        private ImageBuilder(int startLine) {
            this.startLine = startLine;
        }

        private void startFileName(
                int line,
                Integer width,
                Integer height,
                String alignment
        ) {
            if (fileNameCapture != null) {
                throw new IllegalStateException(
                        "IMG 태그가 중복으로 시작됐습니다."
                );
            }

            this.fileNameCapture = new TextCapture(line);
            this.width = width;
            this.height = height;
            this.alignment = alignment;
            textTarget = ImageTextTarget.FILE_NAME;
        }

        private void appendFileName(String text) {
            if (fileNameCapture != null) {
                fileNameCapture.append(text);
            }
        }

        private void startCaption(int line) {
            if (captionCapture != null) {
                throw new IllegalStateException(
                        "IMG-CAPTION 태그가 중복으로 시작됐습니다."
                );
            }

            captionCapture = new TextCapture(line);
            textTarget = ImageTextTarget.CAPTION;
        }

        private void appendCaption(String text) {
            if (captionCapture != null) {
                captionCapture.append(text);
            }
        }

        private void appendText(String text) {
            switch (textTarget) {
                case FILE_NAME -> fileNameCapture.append(text);
                case CAPTION -> captionCapture.append(text);
                case NONE -> {
                    // IMAGE 내부 태그 사이의 들여쓰기 공백은 무시한다.
                }
            }
        }

        private void closeFileName() {
            /*
             * 문자 수집은 이미 끝났으므로 별도 작업은 필요하지 않다.
             * 캡처 객체는 IMAGE 종료 시 build()에서 사용한다.
             */
            textTarget = ImageTextTarget.NONE;
        }

        private void closeCaption() {
            // 위와 동일
            textTarget = ImageTextTarget.NONE;
        }

        private ParsedDisclosureImage build(int endLine) {
            String fileName = fileNameCapture == null ? null : fileNameCapture.text();

            String caption = captionCapture == null ? null : captionCapture.text();

            return new ParsedDisclosureImage(
                    fileName,
                    caption,
                    width,
                    height,
                    alignment,
                    startLine,
                    endLine
            );
        }
    }
}
