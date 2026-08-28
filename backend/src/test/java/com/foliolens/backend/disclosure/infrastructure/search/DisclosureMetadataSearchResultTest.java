package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisclosureMetadataSearchResultTest {

    @Test
    void createsNormalEmptyResult() {
        DisclosureMetadataSearchResult result =
                DisclosureMetadataSearchResult.empty("metadata-search-v1");

        assertEquals(List.of(), result.items());
        assertEquals(0, result.candidateCount());
        assertFalse(result.truncated());
        assertEquals(List.of(), result.warnings());
        assertEquals("metadata-search-v1", result.retrievalVersion());
    }

    @Test
    void copiesItemsAndWarningsAndAllowsTruncatedResult() {
        List<DisclosureMetadataSearchHit> items = new ArrayList<>(
                List.of(hit())
        );
        List<String> warnings = new ArrayList<>(
                List.of("  기간이 지정되지 않았습니다.  ")
        );

        DisclosureMetadataSearchResult result =
                new DisclosureMetadataSearchResult(
                        items,
                        12,
                        true,
                        warnings,
                        "  metadata-search-v1  "
                );

        items.clear();
        warnings.clear();

        assertEquals(1, result.items().size());
        assertTrue(result.truncated());
        assertEquals(
                List.of("기간이 지정되지 않았습니다."),
                result.warnings()
        );
        assertEquals("metadata-search-v1", result.retrievalVersion());
    }

    @Test
    void rejectsInconsistentCandidateCountAndTruncatedFlag() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DisclosureMetadataSearchResult(
                        List.of(hit()),
                        0,
                        false,
                        List.of(),
                        "metadata-search-v1"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new DisclosureMetadataSearchResult(
                        List.of(hit()),
                        2,
                        false,
                        List.of(),
                        "metadata-search-v1"
                )
        );
    }

    private DisclosureMetadataSearchHit hit() {
        return new DisclosureMetadataSearchHit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "삼성전자",
                "005930",
                "20250410000123",
                LocalDate.of(2025, 4, 10),
                "신규시설투자등",
                DisclosureSourceGroup.MAJOR,
                DisclosureCategory.MATERIAL,
                "신규시설투자등",
                false,
                SourceProvider.CONTEST,
                1,
                0.95,
                List.of("시설투자")
        );
    }
}
