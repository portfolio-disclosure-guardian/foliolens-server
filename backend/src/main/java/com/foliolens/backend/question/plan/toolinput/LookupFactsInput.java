package com.foliolens.backend.question.plan.toolinput;

import java.util.List;

public record LookupFactsInput(String disclosureIdsFrom,
                               List<String> factKeys) implements ToolInput {
}
