package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisclosureMetadataSearchConditionTest {

    @Test
    void normalizesOptionalFiltersAndUsesDefaultCorrectionFilter() {
        DisclosureMetadataSearchCondition condition = condition(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                20
        );

        assertEquals(Set.of(), condition.companyIds());
        assertEquals(Set.of(), condition.sourceGroups());
        assertEquals(Set.of(), condition.categories());
        assertEquals(Set.of(), condition.rawSubtypes());
        assertEquals(List.of(), condition.titleTerms());
        assertEquals(CorrectionFilter.ALL, condition.correctionFilter());
    }

    @Test
    void makesCollectionsImmutableAndNormalizesSearchText() {
        UUID companyId = UUID.randomUUID();
        Set<UUID> companyIds = new HashSet<>(Set.of(companyId));
        Set<String> rawSubtypes = new HashSet<>(Set.of("  신규시설투자등  "));
        List<String> titleTerms = new ArrayList<>(List.of("  시설투자  "));

        DisclosureMetadataSearchCondition condition = condition(
                companyIds,
                null,
                null,
                null,
                Set.of(DisclosureSourceGroup.MAJOR),
                Set.of(DisclosureCategory.MATERIAL),
                rawSubtypes,
                titleTerms,
                CorrectionFilter.ORIGINAL_ONLY,
                10
        );

        companyIds.clear();
        rawSubtypes.clear();
        titleTerms.clear();

        assertEquals(Set.of(companyId), condition.companyIds());
        assertEquals(Set.of("신규시설투자등"), condition.rawSubtypes());
        assertEquals(List.of("시설투자"), condition.titleTerms());
        assertThrows(
                UnsupportedOperationException.class,
                () -> condition.titleTerms().add("증설")
        );
    }

    @Test
    void returnsEarlierDateAsEffectiveUpperBound() {
        DisclosureMetadataSearchCondition condition = condition(
                Set.of(),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 6, 30),
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(),
                CorrectionFilter.ALL,
                20
        );

        assertEquals(
                LocalDate.of(2025, 6, 30),
                condition.effectiveReceiptDateTo()
        );
    }

    @Test
    void rejectsInvalidDateRangeAndLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> condition(
                        Set.of(),
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2025, 1, 1),
                        null,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        List.of(),
                        CorrectionFilter.ALL,
                        20
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> condition(
                        Set.of(),
                        null,
                        null,
                        null,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        List.of(),
                        CorrectionFilter.ALL,
                        51
                )
        );
    }

    private DisclosureMetadataSearchCondition condition(
            Set<UUID> companyIds,
            LocalDate receiptDateFrom,
            LocalDate receiptDateTo,
            LocalDate asOf,
            Set<DisclosureSourceGroup> sourceGroups,
            Set<DisclosureCategory> categories,
            Set<String> rawSubtypes,
            List<String> titleTerms,
            CorrectionFilter correctionFilter,
            int limit
    ) {
        return new DisclosureMetadataSearchCondition(
                companyIds,
                receiptDateFrom,
                receiptDateTo,
                asOf,
                sourceGroups,
                categories,
                rawSubtypes,
                titleTerms,
                correctionFilter,
                limit
        );
    }
}
