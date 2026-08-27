package com.foliolens.backend.question.plan.confirmation;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanTimeTest {

    @Test
    void 날짜_문자열을_접수기간_보고기간_기준일로_변환한다() {
        PlanTime time = PlanTime.parse(
                "2024-04-01", "2024-04-30",
                "2024-01-01", "2024-03-31",
                "2024-04-24");

        assertThat(time).isEqualTo(new PlanTime(
                new DateRange(LocalDate.parse("2024-04-01"), LocalDate.parse("2024-04-30")),
                new DateRange(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-03-31")),
                LocalDate.parse("2024-04-24")));
    }

    @Test
    void 날짜_형식이_아니면_QUESTION_400_5_예외를_던진다() {
        assertQuestionTimeError(() -> PlanTime.parse(
                "2024-04-01T00:00:00Z", "2024-04-30",
                "2024-01-01", "2024-03-31",
                "2024-04-24"));
    }

    @Test
    void 날짜가_null이면_QUESTION_400_5_예외를_던진다() {
        assertQuestionTimeError(() -> PlanTime.parse(
                null, "2024-04-30",
                "2024-01-01", "2024-03-31",
                "2024-04-24"));
    }

    @Test
    void 기간의_from이_to_이후면_QUESTION_400_5_예외를_던진다() {
        assertQuestionTimeError(() -> PlanTime.parse(
                "2024-04-30", "2024-04-01",
                "2024-01-01", "2024-03-31",
                "2024-04-24"));
    }

    @Test
    void 검증된_계획의_날짜_값이_null이면_QUESTION_400_5_예외를_던진다() {
        DateRange period = new DateRange(LocalDate.parse("2024-04-01"), LocalDate.parse("2024-04-30"));

        assertQuestionTimeError(() -> new PlanTime(period, period, null));
    }

    private static void assertQuestionTimeError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUESTION_400_5);
    }
}
