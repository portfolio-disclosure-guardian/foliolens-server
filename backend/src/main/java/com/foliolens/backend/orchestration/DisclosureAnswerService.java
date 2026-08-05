package com.foliolens.backend.orchestration;

import java.util.List;
import org.springframework.stereotype.Service;

import com.foliolens.backend.answer.dto.AnswerResult;
import com.foliolens.backend.answer.dto.AnswerStatus;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.service.QuestionRunService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DisclosureAnswerService {
    // public final DisclosureRetriever disclosureRetriever;
    public final QuestionRunService questionRunService;

    public AnswerResult getAnswer(AnswerQuestionCommand command) {
        // 질문 계획 → 검색 → 계산 → 답변 조립
        QuestionRun run = questionRunService.createQuestionRun(command.externalQuestionId(), command.question());
        // AnswerStatus status =
        return new AnswerResult(
                run.getId(),
                run.getExternalQuestionId(),
                run.getQuestionText(),
                AnswerStatus.COMPLETED, // 임시 status. 상태 처리 로직 必
                List.of(),
                List.of(),
                "답변 생성 기능이 아직 연결되지 않았습니다."); // 검증완료된 AI의 자연어 답변
    }
}