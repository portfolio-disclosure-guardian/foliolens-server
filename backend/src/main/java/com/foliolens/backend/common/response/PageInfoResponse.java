package com.foliolens.backend.common.response;

import org.springframework.data.domain.Page;


/**
 * 다음 목록 API에서 RESPONSE에 사용
 * 공시 목록
 * 포트폴리오 목록
 * 질문 기록
 * 동기화 작업 목록
 */
public record PageInfoResponse(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static PageInfoResponse from(
            Page<?> page
    ) {
        return new PageInfoResponse(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
