package com.foliolens.backend.disclosure.infrastructure.chunking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisclosureChunkingPolicyTest {

    @Test
    void createsDartXmlV1PolicyWithAgreedThresholds() {
        DisclosureChunkingPolicy policy =
                DisclosureChunkingPolicy.dartXmlV1();

        assertEquals(
                "DartXmlDisclosureChunkGenerator",
                policy.generatorName()
        );
        assertEquals("dart-xml-chunk-v1", policy.generatorVersion());
        assertEquals(
                new DisclosureChunkingPolicy.ChunkSizePolicy(
                        700,
                        1_000,
                        1_400,
                        2_000
                ),
                policy.text()
        );
        assertEquals(
                new DisclosureChunkingPolicy.ChunkSizePolicy(
                        1_000,
                        1_500,
                        2_000,
                        3_000
                ),
                policy.table()
        );
    }

    @Test
    void createsDartXmlV2PolicyForLimitedNestedTableContext() {
        DisclosureChunkingPolicy policy =
                DisclosureChunkingPolicy.dartXmlV2();

        assertEquals(
                "DartXmlDisclosureChunkGenerator",
                policy.generatorName()
        );
        assertEquals("dart-xml-chunk-v2", policy.generatorVersion());
        assertEquals(
                DisclosureChunkingPolicy.dartXmlV1().text(),
                policy.text()
        );
        assertEquals(
                DisclosureChunkingPolicy.dartXmlV1().table(),
                policy.table()
        );
    }

    @Test
    void createsDartXmlV3PolicyForAdjacentNestedTableContext() {
        DisclosureChunkingPolicy policy =
                DisclosureChunkingPolicy.dartXmlV3();

        assertEquals(
                "DartXmlDisclosureChunkGenerator",
                policy.generatorName()
        );
        assertEquals("dart-xml-chunk-v3", policy.generatorVersion());
        assertEquals(
                DisclosureChunkingPolicy.dartXmlV2().text(),
                policy.text()
        );
        assertEquals(
                DisclosureChunkingPolicy.dartXmlV2().table(),
                policy.table()
        );
    }

    @Test
    void evaluatesChunkLengthBoundaries() {
        DisclosureChunkingPolicy.ChunkSizePolicy policy =
                new DisclosureChunkingPolicy.ChunkSizePolicy(
                        10,
                        20,
                        30,
                        40
                );

        assertTrue(policy.fitsTarget(12, 8));
        assertFalse(policy.fitsTarget(12, 9));
        assertTrue(policy.fitsNormalMax(20, 10));
        assertFalse(policy.fitsNormalMax(20, 11));
        assertFalse(policy.requiresSplit(40));
        assertTrue(policy.requiresSplit(41));
    }

    @Test
    void rejectsInvalidPolicyValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DisclosureChunkingPolicy.ChunkSizePolicy(
                        0,
                        10,
                        20,
                        30
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DisclosureChunkingPolicy.ChunkSizePolicy(
                        10,
                        9,
                        20,
                        30
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DisclosureChunkingPolicy.ChunkSizePolicy(
                        10,
                        20,
                        19,
                        30
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DisclosureChunkingPolicy.ChunkSizePolicy(
                        10,
                        20,
                        30,
                        29
                )
        );

        DisclosureChunkingPolicy.ChunkSizePolicy valid =
                new DisclosureChunkingPolicy.ChunkSizePolicy(
                        10,
                        20,
                        30,
                        40
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> valid.fitsTarget(-1, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> valid.requiresSplit(-1)
        );
    }
}
