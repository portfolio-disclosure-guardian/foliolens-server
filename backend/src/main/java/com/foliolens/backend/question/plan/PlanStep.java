package com.foliolens.backend.question.plan;

import java.util.List;

public record PlanStep(Long stepId, IntentType intent, String arguments, List<String> dependsOn) {
}
