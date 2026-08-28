package com.foliolens.backend.disclosure.domain.fact;

/**
 * 여러 공시를 하나의 경제 사건으로 연결했을 때 각 공시가 갖는 의미 역할.
 *
 * 하나의 공시에 포함된 실제 파일 역할인 DisclosureDocumentRole과는
 * 별개의 축이다.
 */
public enum EventDocumentRole {
    ORIGINAL, // 최초 공시
    CORRECTION, // 정정공시
    PROGRESS, // 진행 상황
    COMPLETION, // 완료
    TERMINATION, // 중단·종료
    RESULT, // 결과 보고
    UNKNOWN
}
