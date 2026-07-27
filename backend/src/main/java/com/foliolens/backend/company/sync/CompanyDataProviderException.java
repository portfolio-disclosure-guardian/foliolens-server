package com.foliolens.backend.company.sync;

/**
 * 다음 상황에서 예외 사용
 * OpenDART 연결 실패
 * 응답 시간 초과
 * 잘못된 인증키
 * ZIP 대신 오류 XML 반환
 * ZIP 손상
 * XML 파싱 실패
 */
public class CompanyDataProviderException extends RuntimeException {

    public CompanyDataProviderException(String message) {
        super(message);
    }

    public CompanyDataProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
