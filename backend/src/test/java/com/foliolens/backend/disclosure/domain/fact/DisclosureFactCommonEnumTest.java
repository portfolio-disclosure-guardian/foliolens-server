package com.foliolens.backend.disclosure.domain.fact;

import com.foliolens.backend.disclosure.infrastructure.search.CorrectionFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DisclosureFactCommonEnumTest {

    @Test
    void keepsSearchAndHistoryContractValues() {
        assertArrayEquals(
                new CorrectionFilter[]{
                        CorrectionFilter.ALL,
                        CorrectionFilter.ORIGINAL_ONLY,
                        CorrectionFilter.CORRECTION_ONLY
                },
                CorrectionFilter.values()
        );

        assertArrayEquals(
                new EventDocumentRole[]{
                        EventDocumentRole.ORIGINAL,
                        EventDocumentRole.CORRECTION,
                        EventDocumentRole.PROGRESS,
                        EventDocumentRole.COMPLETION,
                        EventDocumentRole.TERMINATION,
                        EventDocumentRole.RESULT,
                        EventDocumentRole.UNKNOWN
                },
                EventDocumentRole.values()
        );

        assertArrayEquals(
                new FactQueryMode[]{
                        FactQueryMode.AS_FILED,
                        FactQueryMode.LATEST_AS_OF,
                        FactQueryMode.FULL_HISTORY
                },
                FactQueryMode.values()
        );
    }

    @Test
    void keepsEvidenceContractValues() {
        assertArrayEquals(
                new EvidenceBlockType[]{
                        EvidenceBlockType.DOCUMENT_METADATA,
                        EvidenceBlockType.SECTION,
                        EvidenceBlockType.TITLE,
                        EvidenceBlockType.HEADING,
                        EvidenceBlockType.PARAGRAPH,
                        EvidenceBlockType.TABLE,
                        EvidenceBlockType.TABLE_ROW,
                        EvidenceBlockType.TABLE_CELL,
                        EvidenceBlockType.NOTE
                },
                EvidenceBlockType.values()
        );

        assertArrayEquals(
                new EvidenceStatus[]{
                        EvidenceStatus.CANDIDATE,
                        EvidenceStatus.VERIFIED
                },
                EvidenceStatus.values()
        );
    }

    @Test
    void keepsFactContractValues() {
        assertArrayEquals(
                new FactValueType[]{
                        FactValueType.TEXT,
                        FactValueType.DECIMAL,
                        FactValueType.INTEGER,
                        FactValueType.DATE,
                        FactValueType.BOOLEAN,
                        FactValueType.CODE,
                        FactValueType.LIST
                },
                FactValueType.values()
        );

        assertArrayEquals(
                new FactGenerationMethod[]{
                        FactGenerationMethod.SOURCE_METADATA,
                        FactGenerationMethod.DIRECT_RAW,
                        FactGenerationMethod.TEXT_EXTRACTED,
                        FactGenerationMethod.DIRECT_NORMALIZED,
                        FactGenerationMethod.DERIVED_CLASSIFICATION,
                        FactGenerationMethod.DERIVED_CALCULATION,
                        FactGenerationMethod.LINKED_RESOLVED,
                        FactGenerationMethod.SYSTEM_ASSIGNED
                },
                FactGenerationMethod.values()
        );

        assertArrayEquals(
                new FactAvailabilityStatus[]{
                        FactAvailabilityStatus.AVAILABLE,
                        FactAvailabilityStatus.NOT_STATED,
                        FactAvailabilityStatus.WITHHELD,
                        FactAvailabilityStatus.NOT_APPLICABLE,
                        FactAvailabilityStatus.AMBIGUOUS,
                        FactAvailabilityStatus.PARSE_FAILED
                },
                FactAvailabilityStatus.values()
        );

        assertArrayEquals(
                new FactNormalizationStatus[]{
                        FactNormalizationStatus.MAPPED,
                        FactNormalizationStatus.UNMAPPED,
                        FactNormalizationStatus.AMBIGUOUS,
                        FactNormalizationStatus.REVIEW_REQUIRED,
                        FactNormalizationStatus.NOT_APPLICABLE,
                        FactNormalizationStatus.MISSING
                },
                FactNormalizationStatus.values()
        );

        assertArrayEquals(
                new FactValidationStatus[]{
                        FactValidationStatus.UNVALIDATED,
                        FactValidationStatus.VERIFIED,
                        FactValidationStatus.REJECTED
                },
                FactValidationStatus.values()
        );

        assertArrayEquals(
                new AccountingBasis[]{
                        AccountingBasis.CONSOLIDATED,
                        AccountingBasis.SEPARATE,
                        AccountingBasis.OTHER,
                        AccountingBasis.UNKNOWN
                },
                AccountingBasis.values()
        );
    }
}
