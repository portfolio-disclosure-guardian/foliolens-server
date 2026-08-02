package com.foliolens.backend.global.exception;

import com.foliolens.backend.global.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        log.warn(
                "Business exception. code={}, message={}",
                errorCode.getCode(),
                exception.getMessage()
        );

        return createResponse(
                errorCode,
                exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        String message = extractValidationMessage(
                exception.getBindingResult()
        );

        return createResponse(
                ErrorCode.COMMON_400_1,
                message
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException exception
    ) {
        String message = extractValidationMessage(
                exception.getBindingResult()
        );

        return createResponse(
                ErrorCode.COMMON_400_1,
                message
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleHandlerMethodValidation(
            HandlerMethodValidationException exception
    ) {
        return createResponse(
                ErrorCode.COMMON_400_1,
                ErrorCode.COMMON_400_1.getMessage()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .sorted(Comparator.comparing(
                        violation ->
                                violation.getPropertyPath().toString()
                ))
                .map(ConstraintViolation::getMessage)
                .filter(value ->
                        value != null && !value.isBlank()
                )
                .findFirst()
                .orElse(ErrorCode.COMMON_400_1.getMessage());

        return createResponse(
                ErrorCode.COMMON_400_1,
                message
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        String message = "필수 요청 파라미터가 없습니다: "
                + exception.getParameterName();

        return createResponse(
                ErrorCode.COMMON_400_1,
                message
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        String message = "요청값의 형식이 올바르지 않습니다: "
                + exception.getName();

        return createResponse(
                ErrorCode.COMMON_400_1,
                message
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        return createResponse(
                ErrorCode.COMMON_400_1,
                "요청 본문의 형식이 올바르지 않습니다."
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleNoResourceFound(
            NoResourceFoundException exception
    ) {
        return createResponse(
                ErrorCode.COMMON_404_1,
                ErrorCode.COMMON_404_1.getMessage()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        return createResponse(
                ErrorCode.COMMON_405_1,
                ErrorCode.COMMON_405_1.getMessage()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception
    ) {
        return createResponse(
                ErrorCode.COMMON_415_1,
                ErrorCode.COMMON_415_1.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        log.warn(
                "Database constraint violation.",
                exception
        );

        return createResponse(
                ErrorCode.COMMON_409_1,
                ErrorCode.COMMON_409_1.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception
    ) {
        log.error(
                "Unhandled server exception.",
                exception
        );

        return createResponse(
                ErrorCode.COMMON_500_1,
                ErrorCode.COMMON_500_1.getMessage()
        );
    }

    private String extractValidationMessage(
            BindingResult bindingResult
    ) {
        return bindingResult.getFieldErrors()
                .stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(FieldError::getDefaultMessage)
                .filter(message ->
                        message != null && !message.isBlank()
                )
                .findFirst()
                .orElse(ErrorCode.COMMON_400_1.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> createResponse(
            ErrorCode errorCode,
            String message
    ) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode, message));
    }
}
