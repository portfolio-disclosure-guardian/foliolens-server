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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisclosureMetadataSearchHitTest {

    @Test
    void normalizesTextAndCopiesMatchedTerms() {
        List<String> matchedTerms = new ArrayList<>(
                List.of("  시설투자  ")
        );

        DisclosureMetadataSearchHit hit = hit(
                "  삼성전자  ",
                "  005930  ",
                "  신규시설투자등  ",
                1,
                0.95,
                matchedTerms
        );

        matchedTerms.clear();

        assertEquals("삼성전자", hit.companyName());
        assertEquals("005930", hit.stockCode());
        assertEquals("신규시설투자등", hit.rawSubtype());
        assertEquals(List.of("시설투자"), hit.matchedTerms());
        assertThrows(
                UnsupportedOperationException.class,
                () -> hit.matchedTerms().add("증설")
        );
    }

    @Test
    void convertsBlankOptionalTextToNull() {
        DisclosureMetadataSearchHit hit = hit(
                "삼성전자",
                " ",
                null,
                0,
                0.0,
                null
        );

        assertNull(hit.stockCode());
        assertNull(hit.rawSubtype());
        assertEquals(List.of(), hit.matchedTerms());
    }

    @Test
    void rejectsInvalidDocumentCountAndSearchScore() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hit(
                        "삼성전자",
                        "005930",
                        null,
                        -1,
                        0.5,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> hit(
                        "삼성전자",
                        "005930",
                        null,
                        1,
                        Double.NaN,
                        List.of()
                )
        );
    }

    private DisclosureMetadataSearchHit hit(
            String companyName,
            String stockCode,
            String rawSubtype,
            int documentCount,
            double searchScore,
            List<String> matchedTerms
    ) {
        return new DisclosureMetadataSearchHit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                companyName,
                stockCode,
                "20250410000123",
                LocalDate.of(2025, 4, 10),
                "신규시설투자등",
                DisclosureSourceGroup.MAJOR,
                DisclosureCategory.MATERIAL,
                rawSubtype,
                false,
                SourceProvider.CONTEST,
                documentCount,
                searchScore,
                matchedTerms
        );
    }
}
