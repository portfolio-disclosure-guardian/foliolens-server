package com.foliolens.backend.question.entity;

import java.time.LocalDate;
import java.util.List;

public record QuestionPlan(QuestionType questionType,
        List<String> companyNames,
        LocalDate from,
        LocalDate to,
        List<String> disclosureTypes,
        List<String> metrics,
        List<OperationType> operations,
        AccountingBasis accountingBasis,
        boolean needsClarification,
        String clarificationReason) {
}
