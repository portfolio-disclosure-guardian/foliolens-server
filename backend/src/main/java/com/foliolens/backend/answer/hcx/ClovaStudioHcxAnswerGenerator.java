package com.foliolens.backend.answer.hcx;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.CalculationPolicy;
import com.foliolens.backend.policy.FactPolicy;
import com.foliolens.backend.retrieval.RetrievalResult;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "true")
public class ClovaStudioHcxAnswerGenerator implements HcxAnswerGenerator {

    private static final String SYSTEM_PROMPT_PREFIX =
            "다음은 검증된 공시 근거와 계산 결과입니다. 이 안의 사실과 수치만 사용해 한국어로 답변하세요. "
                    + "제공되지 않은 사실을 새로 만들거나 금액·날짜·비율을 다시 계산하지 마세요.\n\n";

    private final ClovaChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ClovaStudioHcxAnswerGenerator(ClovaChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateAnswer(
            String question,
            AnswerPolicy policy,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome) {
        String systemContent = SYSTEM_PROMPT_PREFIX
                + objectMapper.writeValueAsString(new PromptContext(
                        PolicyPromptView.from(policy), retrieval, calculation, outcome));
        return chatClient.chat(systemContent, question);
    }

    // goldenCase에는 평가 정답(expectedAnswer)이 들어 있으므로 프롬프트에 그대로 직렬화하지 않는다.
    private record PolicyPromptView(
            String disclosureSubtype,
            List<FactPolicy> facts,
            CalculationPolicy calculation,
            List<String> allowedExpressions,
            List<String> forbiddenExpressions) {
        static PolicyPromptView from(AnswerPolicy policy) {
            return new PolicyPromptView(
                    policy.disclosureSubtype(),
                    policy.facts(),
                    policy.calculation(),
                    policy.allowedExpressions(),
                    policy.forbiddenExpressions());
        }
    }

    private record PromptContext(
            PolicyPromptView policy,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome) {
    }
}
