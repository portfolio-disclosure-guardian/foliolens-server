package com.foliolens.backend.question.plan.confirmation;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record DateRange(
        LocalDate from,
        LocalDate to) {

    public DateRange {
        if (from == null || to == null) {
            throw invalid("from과 to는 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw invalid("from은 to 이후일 수 없습니다.");
        }
    }

    static DateRange parse(String fieldName, String from, String to) {
        return new DateRange(
                parseDate(fieldName + ".from", from),
                parseDate(fieldName + ".to", to));
    }

    static LocalDate parseDate(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + "이 비어 있습니다.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    ErrorCode.QUESTION_400_5,
                    "PlanTime." + fieldName + "이 yyyy-MM-dd 형식이 아닙니다: " + value,
                    e);
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.QUESTION_400_5, "PlanTime." + message);
    }
}
