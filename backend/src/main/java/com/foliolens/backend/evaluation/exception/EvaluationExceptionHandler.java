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
                log.error("Unexpected evaluation failure.", exception);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .build();
        }
}