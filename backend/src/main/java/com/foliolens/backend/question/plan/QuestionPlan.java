package com.foliolens.backend.question.plan;

import java.time.LocalDate;
import java.util.List;

import com.foliolens.backend.question.entity.QuestionType;

// 사용자의 프롬프트 요구사항에 맞게끔 AI가 출력한 공시 검색 계획
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
