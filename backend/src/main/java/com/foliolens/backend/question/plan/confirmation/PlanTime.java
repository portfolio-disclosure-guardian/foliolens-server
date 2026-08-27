package com.foliolens.backend.question.plan.confirmation;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;

import java.time.LocalDate;

public record PlanTime(
        DateRange receiptPeriod,
        DateRange reportPeriod,
        LocalDate asOf) {

    public PlanTime {
        if (receiptPeriod == null || reportPeriod == null || asOf == null) {
            throw new BusinessException(ErrorCode.QUESTION_400_5, "PlanTime의 날짜 값은 필수입니다.");
        }
    }

    public static PlanTime parse(
            String receiptFrom,
            String receiptTo,
            String reportFrom,
            String reportTo,
            String asOf) {
        return new PlanTime(
                DateRange.parse("receiptPeriod", receiptFrom, receiptTo),
                DateRange.parse("reportPeriod", reportFrom, reportTo),
                DateRange.parseDate("asOf", asOf));
    }
}
