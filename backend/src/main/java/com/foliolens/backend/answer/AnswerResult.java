package com.foliolens.backend.answer;

import java.util.List;

import com.foliolens.backend.retrieval.RetrievedDocument;

// 검색·계산·답변 생성·검증이 끝난 뒤 공통 답변 엔진(DisclosureAnswerService이 반환하는 내부 최종 결과
public record AnswerResult(
    String externalQuestionId,
    String originalQuestion,
    List<RetrievedDocument> usedDocuments,
    List<String> safeExecutionSummary,
    String renderedAnswer
) {
}
