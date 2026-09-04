package com.foliolens.backend.evaluation.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.foliolens.backend.evaluation.controller.EvaluationAnswerController;
import com.foliolens.backend.global.exception.BusinessException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(assignableTypes = EvaluationAnswerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class EvaluationExceptionHandler {

        @ExceptionHandler({
                        MissingServletRequestParameterException.class,
                        HandlerMethodValidationException.class,
                        ConstraintViolationException.class
        })
        ResponseEntity<Void> handleBadRequest(Exception exception) {
                log.warn("Invalid evaluation request. type={}", exception.getClass().getSimpleName());

                return ResponseEntity.badRequest().build();
        }

        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Void> handleBusinessException(BusinessException exception) {
                var errorCode = exception.getErrorCode();

                log.warn("Evaluation failed. code={}", errorCode.getCode());

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .build();
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<Void> handleUnexpectedException(Exception exception) {
                log.error(
                                "Unexpected evaluation failure. type={}{}{}",
                                exception.getClass().getSimpleName(),
                                System.lineSeparator(),
                                safeStackTrace(exception));

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .build();
        }

        // exception.getMessage()에는 잘못된 파라미터 값이나 하위 시스템 오류 메시지가 그대로
        // 담길 수 있어 원인 위치 진단에 필요한 클래스명·스택 프레임만 남기고 메시지는 뺀다.
        private static String safeStackTrace(Throwable throwable) {
                StringBuilder builder = new StringBuilder();
                for (Throwable current = throwable; current != null; current = current.getCause()) {
                        if (!builder.isEmpty()) {
                                builder.append("Caused by: ");
                        }
                        builder.append(current.getClass().getName()).append(System.lineSeparator());
                        for (StackTraceElement element : current.getStackTrace()) {
                                builder.append("\tat ").append(element).append(System.lineSeparator());
                        }
                }
                return builder.toString();
        }
}
