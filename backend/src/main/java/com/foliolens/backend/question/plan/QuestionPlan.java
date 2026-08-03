package com.foliolens.backend.question.plan;

import java.time.LocalDate;
import java.util.List;

import com.foliolens.backend.question.entity.QuestionType;

public record QuestionPlan(QuestionType questionType,
        List<String> companyNames,
        LocalDate from,
        LocalDate to,
        List<String> disclosureTypes,
        List<String> metrics,
        List<ToolName> toolNames,
        //AccountingBasis accountingBasis,
        boolean needsClarification,
        String clarificationReason) {
}
