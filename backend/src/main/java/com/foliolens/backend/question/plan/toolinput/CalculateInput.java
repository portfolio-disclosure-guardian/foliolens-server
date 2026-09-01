package com.foliolens.backend.question.plan.toolinput;

import java.util.List;

public record CalculateInput(String factsFrom,
                             CalculationOperation operation,
                             List<String> inputBindings) implements ToolInput {
}
