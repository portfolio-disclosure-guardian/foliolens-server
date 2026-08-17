package com.foliolens.backend.disclosure.infrastructure.persistence;

import java.util.UUID;

public record DisclosureParsingPersistenceResult(
        UUID disclosureDocumentId,
        int deletedSectionCount,
        int deletedBlockCount,
        int savedSectionCount,
        int savedBlockCount
) {
}
