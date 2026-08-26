package com.foliolens.backend.question.plan.confirmation;

import com.foliolens.backend.question.plan.ToolType;
import com.foliolens.backend.question.plan.toolinput.ToolInput;

import java.util.List;

public record PlanStep(String stepId, ToolType toolType, ToolInput input, List<String> dependsOn) {
}