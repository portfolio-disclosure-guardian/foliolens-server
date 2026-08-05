package com.foliolens.backend.evaluation.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foliolens.backend.answer.dto.AnswerResult;
import com.foliolens.backend.retrieval.RetrievedContextResponse;

public record EvaluationAnswerResponse(
                @JsonProperty("question_id") String questionId,

                @JsonProperty("question") String questionText,

                @JsonProperty("retrieved_context") List<RetrievedContextResponse> retrievedContext,

                // thinkTrace는 AI의 내부 사고과정이 아닙니다. "공시 3건을 검색했습니다", "증감률 계산을 수행했습니다" 같은 실행 기록입니다.
                @JsonProperty("think_trace") List<String> thinkTrace,

                @JsonProperty("answer") String answerText) {
        public static EvaluationAnswerResponse from(AnswerResult result) {
                return new EvaluationAnswerResponse(result.externalQuestionId(), result.originalQuestion(), List.of(),
                                result.safeExecutionSummary(),
                                result.renderedAnswer());
        }
}