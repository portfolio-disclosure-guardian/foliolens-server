package com.foliolens.backend.disclosure.infrastructure.profiling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlStructureProfilerTest {

    private final XmlStructureProfiler profiler =
            new XmlStructureProfiler();

    @Test
    void profilesXmlWhenEnglishTitleUsesBareAngleBrackets(
            @TempDir Path tempDirectory
    ) throws IOException {
        Path sourceFile = tempDirectory.resolve("sample.xml");

        Files.writeString(
                sourceFile,
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <DOCUMENT>
                            <DOCUMENT-NAME>사업보고서</DOCUMENT-NAME>
                            <BODY>
                                <P USERMARK="F-10">출처: IFPI (2023), <Global Music Report 2023></P>
                            </BODY>
                        </DOCUMENT>
                        """,
                UTF_8
        );

        XmlStructureProfile result = profiler.profile(sourceFile);

        assertEquals("DOCUMENT", result.rootElementName());
        assertEquals("사업보고서", result.documentName());
        assertEquals(1L, result.countOf("P"));
        assertEquals(0L, result.countOf("GLOBAL"));
    }
}
