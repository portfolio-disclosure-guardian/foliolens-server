package com.foliolens.backend.global.exception;

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

    // 공통 오류
    COMMON_400_1(HttpStatus.BAD_REQUEST, "COMMON_400_1", "입력값이 올바르지 않습니다."),
    COMMON_401_1(HttpStatus.UNAUTHORIZED, "COMMON_401_1", "인증이 필요합니다."),
    COMMON_403_1(HttpStatus.FORBIDDEN, "COMMON_403_1", "접근 권한이 없습니다."),
    COMMON_404_1(HttpStatus.NOT_FOUND, "COMMON_404_1", "요청한 리소스를 찾을 수 없습니다."),
    COMMON_405_1(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405_1", "지원하지 않는 HTTP 메서드입니다."),
    COMMON_409_1(HttpStatus.CONFLICT, "COMMON_409_1", "요청이 현재 리소스 상태와 충돌합니다."),
    COMMON_415_1(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_415_1", "지원하지 않는 요청 형식입니다."),
    COMMON_500_1(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500_1", "서버 내부 오류가 발생했습니다."),

    // 기업 오류
    COMPANY_404_1(HttpStatus.NOT_FOUND, "COMPANY_404_1", "제공 기업 목록에서 기업을 찾을 수 없습니다."),

    // 질문 오류
    QUESTION_400_1(HttpStatus.BAD_REQUEST, "QUESTION_400_1", "질문을 입력해 주세요."),
    QUESTION_400_2(HttpStatus.BAD_REQUEST, "QUESTION_400_2", "질문이 허용 길이를 초과했습니다."),
    QUESTION_400_3(HttpStatus.BAD_REQUEST, "QUESTION_400_3", "선택 가능한 기업 후보가 아닙니다."),
    QUESTION_403_1(HttpStatus.FORBIDDEN, "QUESTION_403_1", "해당 질문 실행에 접근할 수 없습니다."),
    QUESTION_404_1(HttpStatus.NOT_FOUND, "QUESTION_404_1", "질문 실행을 찾을 수 없습니다."),
    QUESTION_409_1(HttpStatus.CONFLICT, "QUESTION_409_1", "현재 상태에서는 요청을 처리할 수 없습니다."),

    // 공시·근거·계산 오류
    DISCLOSURE_404_1(HttpStatus.NOT_FOUND, "DISCLOSURE_404_1", "공시를 찾을 수 없습니다."),
    EVIDENCE_404_1(HttpStatus.NOT_FOUND, "EVIDENCE_404_1", "답변 근거를 찾을 수 없습니다."),
    CALCULATION_404_1(HttpStatus.NOT_FOUND, "CALCULATION_404_1", "계산 기록을 찾을 수 없습니다."),

    // 데이터셋 오류
    DATASET_409_1(HttpStatus.CONFLICT, "DATASET_409_1", "같은 데이터셋의 적재 작업이 이미 진행 중입니다."),
    DATASET_503_1(HttpStatus.SERVICE_UNAVAILABLE, "DATASET_503_1", "공시 데이터가 아직 준비되지 않았습니다."),

    // Agent 오류
    AGENT_429_1(HttpStatus.TOO_MANY_REQUESTS, "AGENT_429_1", "잠시 후 다시 시도해 주세요."),
    AGENT_502_1(HttpStatus.BAD_GATEWAY, "AGENT_502_1", "답변 생성 결과를 검증할 수 없습니다."),
    AGENT_500_1(HttpStatus.INTERNAL_SERVER_ERROR, "AGENT_500_1", "답변 검증을 완료하지 못했습니다."),
    AGENT_504_1(HttpStatus.GATEWAY_TIMEOUT, "AGENT_504_1", "질문 처리 시간이 초과되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
