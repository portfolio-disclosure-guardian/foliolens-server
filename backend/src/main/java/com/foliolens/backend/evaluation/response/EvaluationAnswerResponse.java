package com.foliolens.backend.evaluation.response;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.ThinkTraceEntry;
import com.foliolens.backend.retrieval.RetrievedContextResponse;

// 주최측 평가 API 공지(2026-09-05)에 따라 retrieved_context/think_trace는 모두 문자열이어야 한다.
// 여러 근거·단계는 문자열 안에서 구분 태그로 자유롭게 연결하면 되고, 태그 형식 자체는 평가 대상이 아니다.
public record EvaluationAnswerResponse(
                @JsonProperty("question_id") String questionId,

                @JsonProperty("question") String questionText,

                @JsonProperty("retrieved_context") String retrievedContext,

                // thinkTrace는 AI의 내부 사고과정이 아닙니다. "공시 3건을 검색했습니다", "증감률 계산을 수행했습니다" 같은 실행 기록입니다.
                @JsonProperty("think_trace") String thinkTrace,

                @JsonProperty("answer") String answerText) {
        public static EvaluationAnswerResponse from(AnswerResult result) {
                List<RetrievedContextResponse> retrievedContext = result.usedEvidences().stream()
                                .map(RetrievedContextResponse::from)
                                .toList();
                return new EvaluationAnswerResponse(result.externalQuestionId(), result.originalQuestion(),
                                formatRetrievedContext(retrievedContext),
                                formatThinkTrace(result.safeExecutionSummary()),
                                result.renderedAnswer());
        }

        private static String formatRetrievedContext(List<RetrievedContextResponse> documents) {
                return IntStream.range(0, documents.size())
                                .mapToObj(index -> formatDocument(index + 1, documents.get(index)))
                                .collect(Collectors.joining("\n\n"));
        }

        private static String formatDocument(int order, RetrievedContextResponse document) {
                return "[%d] receipt_no=%s | report_name=%s | submitted_at=%s | section=%s\n%s".formatted(
                                order,
                                document.receiptNo(),
                                document.reportName(),
                                document.submittedAt(),
                                document.section(),
                                document.content());
        }

        private static String formatThinkTrace(List<ThinkTraceEntry> entries) {
                return entries.stream()
                                .map(entry -> "[%s] %s".formatted(entry.step(), entry.summary()))
                                .collect(Collectors.joining("\n"));
        }
}