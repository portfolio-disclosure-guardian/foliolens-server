package com.foliolens.backend.question.plan.candidate;

public record PlanTimeCandidate(
        DateRangeCandidate receiptPeriod,
        DateRangeCandidate reportPeriod,
        String asOf) {
}
