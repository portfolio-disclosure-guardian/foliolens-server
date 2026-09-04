package com.foliolens.backend.disclosure.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 시설투자 Fact 배치 적재 한 회차의 결과.
 *
 * 한 접수번호의 적재 실패가 나머지 접수번호 처리를 막지 않으므로,
 * 성공한 결과와 실패 사유를 접수번호별로 함께 담는다.
 */
public record FacilityInvestmentFactIngestionBatchResult(
        List<FacilityInvestmentFactIngestionResult> successes,
        Map<String, String> failures
) {

    public FacilityInvestmentFactIngestionBatchResult {
        successes = List.copyOf(
                Objects.requireNonNull(successes, "successes는 필수입니다.")
        );
        failures = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(failures, "failures는 필수입니다.")
        ));
    }

    public int totalCount() {
        return successes.size() + failures.size();
    }

    public long coreCompleteCount() {
        return successes.stream()
                .filter(FacilityInvestmentFactIngestionResult::hasAllCoreFacts)
                .count();
    }
}
