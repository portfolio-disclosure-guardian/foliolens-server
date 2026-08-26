package com.foliolens.backend.disclosure.infrastructure.chunking;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedChunkSourceTest {

    private static final UUID BLOCK_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void createsBlockAndTableSources() {
        GeneratedChunkSource block = GeneratedChunkSource.block(
                BLOCK_ID,
                3,
                10,
                15
        );

        assertFalse(block.isTableSource());
        assertNull(block.tableNestingPath());
        assertNull(block.tableRowIndexStart());
        assertNull(block.tableRowIndexEnd());

        GeneratedChunkSource table = GeneratedChunkSource.tableRows(
                BLOCK_ID,
                4,
                20,
                30,
                " 0/1 ",
                2,
                5
        );

        assertTrue(table.isTableSource());
        assertEquals("0/1", table.tableNestingPath());
        assertEquals(2, table.tableRowIndexStart());
        assertEquals(5, table.tableRowIndexEnd());
    }

    @Test
    void rejectsInvalidLineAndTableRanges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedChunkSource.block(BLOCK_ID, 0, 1, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedChunkSource.block(BLOCK_ID, 1, 5, 4)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeneratedChunkSource(
                        BLOCK_ID,
                        1,
                        1,
                        2,
                        null,
                        0,
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedChunkSource.tableRows(
                        BLOCK_ID,
                        1,
                        1,
                        2,
                        null,
                        3,
                        2
                )
        );
    }
}
