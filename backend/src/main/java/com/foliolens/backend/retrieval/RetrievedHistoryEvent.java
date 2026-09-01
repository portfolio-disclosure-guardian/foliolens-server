package com.foliolens.backend.retrieval;

import java.time.LocalDate;
import java.util.List;

// relationType/state는 아직 승인된 enum이 없어 String으로 두고, 확정되면 enum으로 교체한다.
public record RetrievedHistoryEvent(
        String disclosureId,
        String relationType,
        LocalDate effectiveAt,
        String state,
        List<String> changedFactKeys) {
}
