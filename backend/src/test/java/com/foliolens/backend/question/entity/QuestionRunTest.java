package com.foliolens.backend.question.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.RequestChannel;

class QuestionRunTest {

    @Test
    void 정상_실행은_PROCESSING에서_COMPLETED로_종료된다() {
        QuestionRun run = newRun();

        run.start();
        run.complete("검증된 답변");

        assertThat(run.getStatus()).isEqualTo(QuestionRunStatus.COMPLETED);
        assertThat(run.getAnswerText()).isEqualTo("검증된 답변");
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getProcessingTimeMillis()).isNotNegative();
    }

    @Test
    void 실패_실행도_오류와_종료시각을_남긴다() {
        QuestionRun run = newRun();

        run.start();
        run.fail(ErrorCode.AGENT_504_1);

        assertThat(run.getStatus()).isEqualTo(QuestionRunStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo(ErrorCode.AGENT_504_1);
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getProcessingTimeMillis()).isNotNegative();
    }

    @Test
    void 종료된_run은_다시_전이할_수_없다() {
        QuestionRun run = newRun();
        run.start();
        run.complete("검증된 답변");

        assertThatThrownBy(() -> run.fail(ErrorCode.COMMON_500_1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.complete("다른 답변"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static QuestionRun newRun() {
        return QuestionRun.builder()
                .externalQuestionId("GOLD-FACILITY-001")
                .requestId("request-001")
                .questionText("질문")
                .channel(RequestChannel.EVALUATION)
                .build();
    }
}
