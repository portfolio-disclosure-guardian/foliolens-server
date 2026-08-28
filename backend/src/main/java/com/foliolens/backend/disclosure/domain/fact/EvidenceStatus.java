package com.foliolens.backend.disclosure.domain.fact;

/**
 * 검색·추출된 근거 후보의 검증 상태.
 */
public enum EvidenceStatus {
    CANDIDATE, // 검색이나 추출 과정에서 발견한 근거 후보
    VERIFIED // 실제 Fact를 뒷받침한다고 확인된 근거
}
