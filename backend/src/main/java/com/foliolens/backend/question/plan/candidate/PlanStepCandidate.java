package com.foliolens.backend.question.plan.candidate;

import com.foliolens.backend.question.plan.ToolType;

import java.util.List;

import tools.jackson.databind.JsonNode;

//PlanStep과 똑같이 생겼지만, 검증 이전과 이후를 명확히 구분하기 편하게 타입 자체를 다르게 가져가기로 함
public record PlanStepCandidate(String stepId, ToolType toolType, JsonNode input, List<String> dependsOn) {

    // HCX가 의존성 없는 step에서 dependsOn을 아예 생략하거나 null로 보내는 경우가 있어
    // QuestionPlanConverter가 순회할 때 NPE가 나지 않도록 빈 리스트로 정규화한다.
    public PlanStepCandidate {
        dependsOn = dependsOn == null ? List.of() : dependsOn;
    }
}
