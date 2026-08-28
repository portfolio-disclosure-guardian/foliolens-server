package com.foliolens.backend.disclosure.domain.fact;

/**
 * 정정공시와 후속공시가 있을 때 어떤 시점의 값을 조회할지 정함
 */
public enum FactQueryMode {
    AS_FILED, // 해당 공시가 제출됐을 당시의 값
    LATEST_AS_OF, // 특정 기준일 현재 유효한 최신 값
    FULL_HISTORY // 최초부터 정정·완료까지 전체 변경 이력
}
