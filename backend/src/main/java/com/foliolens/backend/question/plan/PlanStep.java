package com.foliolens.backend.question.plan;

public record PlanStep(Long stepId, ToolName tool, String arguments, String dependsOn) {
}
