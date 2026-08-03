package com.foliolens.backend.disclosure.domain;

/**
 * 서비스 API에서 사용하는 정규화된 공시 대분류
 */
public enum DisclosureCategory {
    PERIODIC,  // 사업,반기,분기보고서
    MATERIAL,  // 주요사항보고서 (major)
    EXCHANGE,  // 거래소 공시
    OWNERSHIP  // 주식 등의 대량보유상황보고서 (holding)
}
