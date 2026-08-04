package com.foliolens.backend.question.plan;

import java.time.LocalDate;
import java.util.List;

import com.foliolens.backend.question.entity.QuestionType;

// 기존 기능명세서에는 이에 대해 JPA Entity로 만들라고 돼있지만, 아직 Plan 내부 필드로 검색/통계할 필요가 없어 DTO로 치부
// 사용자의 프롬프트 요구사항에 맞게끔 AI가 출력한 공시 검색 계획
public record QuestionPlan(QuestionType questionType,
                List<String> companyNames,
                LocalDate from,
                LocalDate to,
                List<String> disclosureTypes,
                List<String> metrics,
                List<ToolName> toolNames,
                // AccountingBasis accountingBasis,
                boolean needsClarification,
                String clarificationReason) {
}
