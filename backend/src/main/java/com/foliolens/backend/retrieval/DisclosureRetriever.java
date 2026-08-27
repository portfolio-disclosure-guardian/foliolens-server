package com.foliolens.backend.retrieval;

import com.foliolens.backend.question.plan.confirmation.QuestionPlan;

// A-B 경계 interface
public interface DisclosureRetriever {
    RetrievalResult retrieve(QuestionPlan plan);
}
