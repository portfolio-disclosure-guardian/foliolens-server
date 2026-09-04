package com.foliolens.backend.evaluation.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.ExecutionStep;
import com.foliolens.backend.answer.ThinkTraceEntry;
import com.foliolens.backend.retrieval.RetrievedDocument;

// 평가 API 5개 최상위 키 계약(6.1절)을 mapper 수준에서 검증한다.
class EvaluationAnswerResponseTest {

    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "question_id", "question", "retrieved_context", "think_trace", "answer");
    private static final Set<String> CONTEXT_ITEM_KEYS = Set.of(
            "receipt_no", "report_name", "submitted_at", "section", "content");
    private static final Set<String> TRACE_ITEM_KEYS = Set.of("step", "summary");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void 사용된_근거가_있으면_retrieved_context가_snake_case로_비어있지_않게_직렬화된다() {
        RetrievedDocument evidence = new RetrievedDocument(
                "20240424800596",
                "20240424800596",
                "SK하이닉스",
                "000660",
                "exchange",
                "신규시설투자등",
                LocalDate.parse("2024-04-24"),
                "2. 투자내역 > 투자금액",
                "투자금액은 ...입니다.",
                1.0);
        AnswerResult result = new AnswerResult(
                UUID.randomUUID(),
                "q-001",
                "SK하이닉스의 신규시설투자 금액은?",
                AnswerOutcome.COMPLETED,
                List.of(),
                List.of(),
                List.of(evidence),
                List.of(new ThinkTraceEntry(ExecutionStep.RETRIEVAL, "관련 공시를 검색했습니다.")),
                "투자금액은 ...입니다.");

        JsonNode root = objectMapper.convertValue(EvaluationAnswerResponse.from(result), JsonNode.class);

        assertEquals(TOP_LEVEL_KEYS, fieldNames(root));

        JsonNode context = root.get("retrieved_context");
        assertTrue(context.isArray());
        assertEquals(1, context.size());
        JsonNode item = context.get(0);
        assertEquals(CONTEXT_ITEM_KEYS, fieldNames(item));
        assertEquals("20240424800596", item.get("receipt_no").asString());
        assertEquals("2024-04-24", item.get("submitted_at").asString());

        JsonNode trace = root.get("think_trace");
        assertEquals(1, trace.size());
        assertEquals(TRACE_ITEM_KEYS, fieldNames(trace.get(0)));
        assertEquals("RETRIEVAL", trace.get(0).get("step").asString());
    }

    @Test
    void 사용된_근거가_없으면_retrieved_context는_빈_배열이지만_스키마는_동일하다() {
        AnswerResult result = new AnswerResult(
                UUID.randomUUID(),
                "q-002",
                "A사의 현재 주가는?",
                AnswerOutcome.UNANSWERABLE,
                List.of(),
                List.of(),
                List.of(),
                List.of(new ThinkTraceEntry(ExecutionStep.RETRIEVAL, "제공 공시 범위에 현재 주가 자료가 없었습니다.")),
                "현재 주가는 제공된 공시 범위에서 확인할 수 없습니다.");

        JsonNode root = objectMapper.convertValue(EvaluationAnswerResponse.from(result), JsonNode.class);

        assertEquals(TOP_LEVEL_KEYS, fieldNames(root));
        assertTrue(root.get("retrieved_context").isArray());
        assertEquals(0, root.get("retrieved_context").size());
    }

    private static Set<String> fieldNames(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }
}
