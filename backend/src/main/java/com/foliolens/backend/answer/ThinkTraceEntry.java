package com.foliolens.backend.answer;

// think_trace 한 단계의 공개 가능한 실행 요약. step/summary 필드명이 곧 외부 계약이므로 별도 응답 DTO 없이 그대로 직렬화한다.
public record ThinkTraceEntry(ExecutionStep step, String summary) {
}
