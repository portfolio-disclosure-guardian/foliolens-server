package com.foliolens.backend.orchestration;

import java.util.List;
import org.springframework.stereotype.Service;

import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.ExecutionStep;
import com.foliolens.backend.answer.ThinkTraceEntry;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.service.QuestionRunService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrchestrationAnswerService {
    // public final DisclosureRetriever disclosureRetriever;
    public final QuestionRunService questionRunService;

    public AnswerResult getAnswer(AnswerQuestionCommand command) {
        // 질문 계획 → 검색 → 계산 → 답변 조립
        QuestionRun run = questionRunService.createQuestionRun(command.externalQuestionId(), command.question());
        return new AnswerResult(
                run.getId(),
                run.getExternalQuestionId(),
                run.getQuestionText(),
                List.of(),
                List.of(new ThinkTraceEntry(ExecutionStep.PLANNING, "질문 실행을 접수했습니다.")),
                "답변 생성 기능이 아직 연결되지 않았습니다."); // 검증완료된 AI의 자연어 답변
    }
}