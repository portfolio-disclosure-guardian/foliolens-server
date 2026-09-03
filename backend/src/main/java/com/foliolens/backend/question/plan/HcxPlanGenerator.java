package com.foliolens.backend.question.plan;

import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;

public interface HcxPlanGenerator {

    QuestionPlanCandidate generatePlan(String question);
}
