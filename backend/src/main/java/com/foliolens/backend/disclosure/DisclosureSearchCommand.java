package com.foliolens.backend.disclosure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DisclosureSearchCommand(
    List<UUID> companyIds,
    OffsetDateTime from,
    OffsetDateTime to,
    List<DisclosureType> disclosureTypes, //import 걸어놓으셈
    List<String> keywords,
    int limit
) {
}