package com.foliolens.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /**
     * 에러 코드 정의 enum
     *
     * 새로운 에러가 필요할 때는 이 파일에 항목을 추가해주세요.
     * 형식: DESCRIPTIVE_NAME(HttpStatus.XXX, "DOMAIN_상태코드_순번", "사용자에게 노출할 메시지")
     *
     * 코드 네이밍 규칙:
     *   - 도메인 접두어 + 상태코드 + 순번으로 구성합니다.
     *   - 동일한 상태코드가 여러 개면 뒤에 _1, _2 순번을 붙입니다.
     *   예시)
     *     USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_1", "사용자를 찾을 수 없습니다.")
     *     POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_404_1", "게시글을 찾을 수 없습니다.")
     *     DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_409_1", "이미 사용 중인 이메일입니다.")
     */

    // 4xx
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400_1", "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401_1", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403_1", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404_1", "요청한 리소스를 찾을 수 없습니다."),



    // 5xx
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500_1", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
