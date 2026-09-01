package com.foliolens.backend.answer;

// 정상 실행 뒤 답변 충족도
// QuestionRun이 정상적으로 실행이 안끝났으면, AnswerOutcome은 지정되지 않음.
public enum AnswerOutcome {
    COMPLETED, PARTIAL, UNANSWERABLE
}
