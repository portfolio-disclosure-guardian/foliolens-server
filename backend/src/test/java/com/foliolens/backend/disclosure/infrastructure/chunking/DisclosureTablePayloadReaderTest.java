package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisclosureTablePayloadReaderTest {

    private ObjectMapper objectMapper;
    private DisclosureTablePayloadReader reader;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        reader = new DisclosureTablePayloadReader(objectMapper);
    }

    @Test
    void readsVersionOnePayloadWithoutParentContext() {
        JsonNode payload = json("""
                {
                  "schemaVersion": 1,
                  "table": {
                    "order": 3,
                    "sourceLineStart": 100,
                    "sourceLineEnd": 120,
                    "rows": [
                      {
                        "rowIndex": 0,
                        "sourceLineStart": 101,
                        "sourceLineEnd": 102,
                        "cells": [
                          {
                            "cellIndex": 0,
                            "type": "HEADER",
                            "rowSpan": 1,
                            "colSpan": 1,
                            "text": "구분",
                            "sourceLineStart": 101,
                            "sourceLineEnd": 101,
                            "nestedTables": [],
                            "images": []
                          },
                          {
                            "cellIndex": 1,
                            "type": "DATA",
                            "rowSpan": 1,
                            "colSpan": 1,
                            "text": "5,000억원",
                            "sourceLineStart": 102,
                            "sourceLineEnd": 102,
                            "nestedTables": [],
                            "images": []
                          }
                        ]
                      }
                    ]
                  }
                }
                """);

        DisclosureContentBlock block = mock(DisclosureContentBlock.class);
        when(block.getBlockType()).thenReturn(DisclosureContentBlockType.TABLE);
        when(block.getStructuredContent()).thenReturn(payload);

        ParsedDisclosureTable result = reader.read(block);

        assertAll(
                () -> assertEquals(3, result.order()),
                () -> assertEquals(100, result.sourceLineStart()),
                () -> assertEquals(120, result.sourceLineEnd()),
                () -> assertNull(result.parentContext()),
                () -> assertEquals(1, result.rowCount()),
                () -> assertEquals(2, result.cellCount()),
                () -> assertEquals(
                        ParsedDisclosureTableCellType.HEADER,
                        result.rows().getFirst().cells().getFirst().type()
                ),
                () -> assertEquals(
                        "5,000억원",
                        result.rows().getFirst().cells().get(1).text()
                )
        );
    }

    @Test
    void readsVersionTwoPayloadWithNestedTableContext() {
        JsonNode payload = json("""
                {
                  "schemaVersion": 2,
                  "table": {
                    "order": 1,
                    "sourceLineStart": 10,
                    "sourceLineEnd": 30,
                    "parentContext": null,
                    "rows": [
                      {
                        "rowIndex": 0,
                        "sourceLineStart": 11,
                        "sourceLineEnd": 29,
                        "cells": [
                          {
                            "cellIndex": 0,
                            "type": "DATA",
                            "rowSpan": 1,
                            "colSpan": 1,
                            "text": "표 앞 문맥\\n표 뒤 문맥",
                            "sourceLineStart": 12,
                            "sourceLineEnd": 28,
                            "nestedTables": [
                              {
                                "order": 2,
                                "sourceLineStart": 15,
                                "sourceLineEnd": 20,
                                "parentContext": {
                                  "precedingText": "표 앞 문맥",
                                  "followingText": "표 뒤 문맥"
                                },
                                "rows": []
                              }
                            ],
                            "images": []
                          }
                        ]
                      }
                    ]
                  }
                }
                """);

        ParsedDisclosureTable result = reader.read(payload);
        ParsedDisclosureTable nestedTable = result.rows()
                .getFirst()
                .cells()
                .getFirst()
                .nestedTables()
                .getFirst();

        assertAll(
                () -> assertNull(result.parentContext()),
                () -> assertTrue(nestedTable.hasParentContext()),
                () -> assertEquals(
                        "표 앞 문맥",
                        nestedTable.parentContext().precedingText()
                ),
                () -> assertEquals(
                        "표 뒤 문맥",
                        nestedTable.parentContext().followingText()
                )
        );
    }

    @Test
    void rejectsNullArrayAndInvalidSchemaVersions() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> reader.read((JsonNode) null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> reader.read(json("[]"))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> reader.read(json("""
                                {"table": {}}
                                """))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> reader.read(json("""
                                {"schemaVersion": "1", "table": {}}
                                """))
                ),
                () -> {
                    IllegalArgumentException exception = assertThrows(
                            IllegalArgumentException.class,
                            () -> reader.read(json("""
                                    {"schemaVersion": 3, "table": {}}
                                    """))
                    );

                    assertTrue(exception.getMessage().contains("schemaVersion=3"));
                }
        );
    }

    @Test
    void rejectsMissingOrMalformedTableObject() {
        IllegalArgumentException missingTable = assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(json("""
                        {"schemaVersion": 1}
                        """))
        );

        IllegalArgumentException malformedTable = assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(json("""
                        {
                          "schemaVersion": 1,
                          "table": {
                            "order": -1,
                            "sourceLineStart": 1,
                            "sourceLineEnd": 2,
                            "rows": []
                          }
                        }
                        """))
        );

        assertAll(
                () -> assertTrue(
                        missingTable.getMessage().contains("table 객체가 없습니다")
                ),
                () -> assertTrue(
                        malformedTable.getMessage().contains("TABLE payload 형식")
                )
        );
    }

    @Test
    void rejectsNonTableBlock() {
        DisclosureContentBlock paragraph = mock(DisclosureContentBlock.class);
        when(paragraph.getBlockType())
                .thenReturn(DisclosureContentBlockType.PARAGRAPH);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(paragraph)
        );

        assertTrue(exception.getMessage().contains("TABLE Block만"));
    }

    private JsonNode json(String value) {
        return objectMapper.readTree(value);
    }
}
