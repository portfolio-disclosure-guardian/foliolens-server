package com.foliolens.backend.question.plan.candidate;

import com.foliolens.backend.question.plan.ToolType;

import java.util.List;

import tools.jackson.databind.JsonNode;

//PlanStep과 똑같이 생겼지만, 검증 이전과 이후를 명확히 구분하기 편하게 타입 자체를 다르게 가져가기로 함
public record PlanStepCandidate(String stepId, ToolType toolType, JsonNode input, List<String> dependsOn) {
}
