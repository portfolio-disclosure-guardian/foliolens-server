package com.foliolens.backend.question.plan;

import java.util.List;

public record PlanStep(Long stepId,ToolType toolType, String input, List<String> dependsOn) {
}
