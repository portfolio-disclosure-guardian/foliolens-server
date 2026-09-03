package com.foliolens.backend.question.plan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;

@Component
@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "false", matchIfMissing = true)
public final class FakeHcxPlanGenerator implements HcxPlanGenerator {

    @Override
    public QuestionPlanCandidate generatePlan(String question) {
        return GoldFacility001Fixture.questionPlanCandidate();
    }
}
