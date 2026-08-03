package com.foliolens.backend.evaluation.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foliolens.backend.retrieval.RetrievedContextResponse;

public record EvaluationAnswerResponse(
    @JsonProperty("question_id")
    String questionId,

    String question,

    @JsonProperty("retrieved_context")
    List<RetrievedContextResponse> retrievedContext,

    @JsonProperty("think_trace")
    List<String> thinkTrace,

    String answer
) {
}