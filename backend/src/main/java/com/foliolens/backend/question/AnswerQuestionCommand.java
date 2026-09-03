package com.foliolens.backend.question;

public record AnswerQuestionCommand(
    String externalQuestionId,
    String question,
    RequestChannel channel,
    String requestId
) {}
