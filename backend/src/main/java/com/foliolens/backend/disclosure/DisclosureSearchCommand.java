package com.foliolens.backend.disclosure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.foliolens.backend.disclosure.domain.DisclosureCategory;

public record DisclosureSearchCommand(
    List<UUID> companyIds,
    OffsetDateTime from,
    OffsetDateTime to,
    List<DisclosureCategory> disclosureTypes,
    List<String> keywords,
    int limit
) {
}