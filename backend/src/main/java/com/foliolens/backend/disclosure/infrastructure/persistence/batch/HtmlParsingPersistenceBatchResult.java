package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record HtmlParsingPersistenceBatchResult(
        List<DisclosureParsingPersistenceResult> successes,
        Map<UUID, String> failures
) {
    public HtmlParsingPersistenceBatchResult {
        successes = List.copyOf(successes);
        failures = Map.copyOf(failures);
    }
    public int totalCount() { return successes.size() + failures.size(); }
    public long savedSectionCount() { return successes.stream().mapToLong(r -> r.savedSectionCount()).sum(); }
    public long savedBlockCount() { return successes.stream().mapToLong(r -> r.savedBlockCount()).sum(); }
}
