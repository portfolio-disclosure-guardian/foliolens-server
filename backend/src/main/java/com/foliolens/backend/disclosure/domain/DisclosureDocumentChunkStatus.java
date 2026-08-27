package com.foliolens.backend.disclosure.domain;

/**
 * 개별 원문 파일의 검색 청크 생성 상태.
 *
 * 청크 저장은 문서 단위 트랜잭션으로 전체 교체하므로
 * 부분 성공 상태를 두지 않는다.
 */
public enum DisclosureDocumentChunkStatus {

    PENDING,
    COMPLETED,
    FAILED
}
