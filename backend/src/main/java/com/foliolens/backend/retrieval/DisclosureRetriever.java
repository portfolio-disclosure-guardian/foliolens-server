package com.foliolens.backend.retrieval;

import com.foliolens.backend.question.QuestionPlan;

public interface DisclosureRetriever {
    RetrievalResult retrieve(QuestionPlan plan);
}
