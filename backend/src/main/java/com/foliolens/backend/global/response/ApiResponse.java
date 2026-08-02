package com.foliolens.backend.global.response;

import com.foliolens.backend.global.exception.ErrorCode;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    /**
     * 성공 응답 예시
     * {
     *   "success": true,
     *   "code": null,
     *   "message": "기업 목록 조회에 성공했습니다.",
     *   "data": {
     *     "items": []
     *   }
     * }
     */

    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {
        return new ApiResponse<>(
                true,
                null,
                message,
                data
        );
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(
                true,
                null,
                message,
                null
        );
    }

    /**
     * 실패 응답 예시
     * {
     *   "success": false,
     *   "code": "COMPANY_404_1",
     *   "message": "제공 기업 목록에서 기업을 찾을 수 없습니다.",
     *   "data": null
     * }
     */
    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(
                false,
                errorCode.getCode(),
                errorCode.getMessage(),
                null
        );
    }

    public static ApiResponse<Void> fail(
            ErrorCode errorCode,
            String message
    ) {
        String responseMessage =
                message == null || message.isBlank()
                        ? errorCode.getMessage()
                        : message;

        return new ApiResponse<>(
                false,
                errorCode.getCode(),
                responseMessage,
                null
        );
    }
}
