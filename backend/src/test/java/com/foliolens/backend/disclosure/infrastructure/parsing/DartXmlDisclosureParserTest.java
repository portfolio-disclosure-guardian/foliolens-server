package com.foliolens.backend.disclosure.infrastructure.parsing;

import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlInputFactoryProvider;
import com.foliolens.backend.disclosure.infrastructure.xml.DartXmlSourceFileValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DartXmlDisclosureParserTest {

    private final DartXmlDisclosureParser parser =
            new DartXmlDisclosureParser(
                    new DartXmlInputFactoryProvider(),
                    new DartXmlSourceFileValidator()
            );

    @Test
    void parsesDocumentNameAndNestedSections(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <DOCUMENT-NAME>사업보고서</DOCUMENT-NAME>
                            <BODY>
                                <P>섹션 앞 문단</P>
                                <SECTION-1>
                                    <TITLE>사업의 내용</TITLE>
                                    <P>회사는 해운업을 영위합니다.</P>
                                    <SECTION-2>
                                        <TITLE>주요 제품 및 서비스</TITLE>
                                        <P>컨테이너 운송 서비스를 제공합니다.</P>
                                    </SECTION-2>
                                </SECTION-1>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        assertEquals("sample.xml", document.fileName());
        assertEquals("사업보고서", document.documentName());
        assertEquals(1, document.preambleBlocks().size());
        assertEquals("섹션 앞 문단", document.preambleBlocks().getFirst().content());

        ParsedDisclosureSection section = document.sections().getFirst();

        assertEquals(1, section.level());
        assertEquals("사업의 내용", section.title());
        assertEquals("회사는 해운업을 영위합니다.", section.blocks().getFirst().content());
        assertEquals(1, section.children().size());
        assertEquals(2, section.children().getFirst().level());
        assertEquals("주요 제품 및 서비스", section.children().getFirst().title());
    }

    @Test
    void parsesTableSpansAndNestedTable(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <BODY>
                                <TABLE>
                                    <TR>
                                        <TH ROWSPAN="2" COLSPAN="3">구분</TH>
                                        <TD>
                                            <P>표 앞 첫 문장</P>
                                            <P>표 앞 둘째 문장</P>
                                            <TABLE>
                                                <TR><TD>중첩 셀</TD></TR>
                                            </TABLE>
                                            <P>표 뒤 첫 문장</P>
                                            <P>표 뒤 둘째 문장</P>
                                        </TD>
                                    </TR>
                                </TABLE>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        ParsedDisclosureBlock block = document.preambleBlocks().getFirst();
        ParsedDisclosureTable table = block.table();
        ParsedDisclosureTableCell headerCell = table.rows().getFirst().cells().getFirst();
        ParsedDisclosureTableCell parentCell = table.rows().getFirst().cells().get(1);

        assertEquals(ParsedDisclosureBlockType.TABLE, block.type());
        assertEquals(2, headerCell.rowSpan());
        assertEquals(3, headerCell.colSpan());
        assertEquals(ParsedDisclosureTableCellType.HEADER, headerCell.type());
        assertEquals(
                "표 앞 첫 문장\n표 앞 둘째 문장\n표 뒤 첫 문장\n표 뒤 둘째 문장",
                parentCell.text()
        );
        assertTrue(parentCell.hasNestedTables());
        assertNull(table.parentContext());

        ParsedDisclosureTable nestedTable =
                parentCell.nestedTables().getFirst();

        assertTrue(nestedTable.hasParentContext());
        assertEquals(
                "표 앞 첫 문장\n표 앞 둘째 문장",
                nestedTable.parentContext().precedingText()
        );
        assertEquals(
                "표 뒤 첫 문장\n표 뒤 둘째 문장",
                nestedTable.parentContext().followingText()
        );
        assertEquals(
                "중첩 셀",
                nestedTable
                        .rows()
                        .getFirst()
                        .cells()
                        .getFirst()
                        .text()
        );
    }

    @Test
    void keepsAdjacentContextSeparateForMultipleNestedTables(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <BODY>
                                <TABLE>
                                    <TR>
                                        <TD>
                                            시작 문맥
                                            <TABLE>
                                                <TR><TD>첫 번째 중첩 표</TD></TR>
                                            </TABLE>
                                            중간 문맥
                                            <TABLE>
                                                <TR><TD>두 번째 중첩 표</TD></TR>
                                            </TABLE>
                                            끝 문맥
                                        </TD>
                                    </TR>
                                </TABLE>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        ParsedDisclosureTable rootTable =
                document.preambleBlocks().getFirst().table();
        ParsedDisclosureTableCell parentCell =
                rootTable.rows().getFirst().cells().getFirst();
        List<ParsedDisclosureTable> nestedTables =
                parentCell.nestedTables();

        assertEquals(2, nestedTables.size());
        assertNull(rootTable.parentContext());
        assertEquals(
                "시작 문맥",
                nestedTables.get(0).parentContext().precedingText()
        );
        assertEquals(
                "중간 문맥",
                nestedTables.get(0).parentContext().followingText()
        );
        assertEquals(
                "중간 문맥",
                nestedTables.get(1).parentContext().precedingText()
        );
        assertEquals(
                "끝 문맥",
                nestedTables.get(1).parentContext().followingText()
        );
    }

    @Test
    void parsesImageMetadataOutsideTableWithoutImageFile(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <BODY>
                                <IMAGE>
                                    <IMG WIDTH="615" HEIGHT="834" ALIGN="center">1.jpg</IMG>
                                    <IMG-CAPTION ATOC="N">내부회계관리제도 운영실태보고서</IMG-CAPTION>
                                </IMAGE>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        ParsedDisclosureBlock block = document.preambleBlocks().getFirst();
        ParsedDisclosureImage image = block.image();

        assertEquals(ParsedDisclosureBlockType.IMAGE, block.type());
        assertEquals("1.jpg", image.fileName());
        assertEquals("내부회계관리제도 운영실태보고서", image.caption());
        assertEquals(615, image.width());
        assertEquals(834, image.height());
        assertEquals("CENTER", image.alignment());
        assertEquals(image.caption(), image.displayText());
        assertTrue(image.hasDimensions());
        assertFalse(Files.exists(tempDirectory.resolve("1.jpg")));
    }

    @Test
    void storesTableImageSeparatelyFromCellText(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <BODY>
                                <TABLE>
                                    <TR>
                                        <TD>
                                            <IMAGE>
                                                <IMG>chart.jpg</IMG>
                                                <IMG-CAPTION>.</IMG-CAPTION>
                                            </IMAGE>
                                        </TD>
                                    </TR>
                                </TABLE>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        ParsedDisclosureTableCell cell = document.preambleBlocks()
                .getFirst()
                .table()
                .rows()
                .getFirst()
                .cells()
                .getFirst();

        assertNull(cell.text());
        assertEquals(1, cell.images().size());
        assertEquals("chart.jpg", cell.images().getFirst().fileName());
        assertNull(cell.images().getFirst().caption());
        assertNull(cell.images().getFirst().width());
        assertNull(cell.images().getFirst().height());
    }

    @Test
    void storesPageBreakInDocumentOrderAndCollapsesConsecutiveBreaks(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <BODY>
                                <P>첫 번째 문단</P>
                                <PGBRK></PGBRK>
                                <PGBRK></PGBRK>
                                <P>두 번째 문단</P>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        List<ParsedDisclosureBlock> blocks = document.preambleBlocks();

        assertEquals(3, blocks.size());
        assertEquals(ParsedDisclosureBlockType.PARAGRAPH, blocks.get(0).type());
        assertEquals(ParsedDisclosureBlockType.PAGE_BREAK, blocks.get(1).type());
        assertEquals(ParsedDisclosureBlockType.PARAGRAPH, blocks.get(2).type());
        assertTrue(blocks.get(0).order() < blocks.get(1).order());
        assertTrue(blocks.get(1).order() < blocks.get(2).order());
        assertNull(blocks.get(1).content());
        assertNull(blocks.get(1).table());
        assertNull(blocks.get(1).image());
    }

    @Test
    void convertsPageBreakInsideTableCellToLineBreak(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <BODY>
                                <TABLE>
                                    <TR>
                                        <TD>앞부분<PGBRK></PGBRK>뒷부분</TD>
                                    </TR>
                                </TABLE>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        ParsedDisclosureBlock tableBlock = document.preambleBlocks().getFirst();
        ParsedDisclosureTableCell cell = tableBlock.table()
                .rows()
                .getFirst()
                .cells()
                .getFirst();

        assertEquals("앞부분\n뒷부분", cell.text());
        assertEquals(1, document.preambleBlocks().size());
        assertEquals(ParsedDisclosureBlockType.TABLE, tableBlock.type());
    }

    @Test
    void sanitizesBareAngleBracketsWhileParsingParagraph(
            @TempDir Path tempDirectory
    ) throws IOException {
        ParsedDisclosureDocument document = parse(
                tempDirectory,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <BODY>
                                <P>출처: IFPI (2023), <Global Music Report 2023></P>
                            </BODY>
                        </DOCUMENT>
                        """
        );

        assertEquals(
                "출처: IFPI (2023), <Global Music Report 2023>",
                document.preambleBlocks().getFirst().content()
        );
    }

    private ParsedDisclosureDocument parse(
            Path tempDirectory,
            String xml
    ) throws IOException {
        Path sourceFile = tempDirectory.resolve("sample.xml");

        Files.writeString(
                sourceFile,
                xml,
                UTF_8
        );

        return parser.parse(sourceFile);
    }
}
