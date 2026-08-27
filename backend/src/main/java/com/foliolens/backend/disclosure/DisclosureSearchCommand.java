package com.foliolens.backend.disclosure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.foliolens.backend.disclosure.domain.DisclosureCategory;

public record DisclosureSearchCommand(
    List<UUID> companyIds,
    LocalDate from,
    LocalDate to,
    List<DisclosureCategory> disclosureTypes,
    List<String> keywords,
    int limit
) {
}
