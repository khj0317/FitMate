package com.fitmate.global.error;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 동시 요청으로 애플리케이션 레벨 중복 검사를 통과해도 DB 유니크 제약에서 걸린다.
     * 제약 조건 이름으로 어떤 비즈니스 에러인지 매핑한다.
     */
    private static final Map<String, ErrorCode> CONSTRAINT_ERRORS = Map.of(
            "users_login_id_key", ErrorCode.DUPLICATE_LOGIN_ID,
            "users_email_key", ErrorCode.DUPLICATE_EMAIL,
            "users_nickname_key", ErrorCode.DUPLICATE_NICKNAME,
            "uq_match_requests_pending", ErrorCode.DUPLICATE_MATCH_REQUEST,
            "uq_chat_rooms_direct_key", ErrorCode.ALREADY_MATCHED,
            "uq_user_reports_pending", ErrorCode.DUPLICATE_REPORT,
            "gathering_participants_pkey", ErrorCode.ALREADY_JOINED,
            "uq_manner_reviews_gathering", ErrorCode.DUPLICATE_REVIEW,
            "uq_manner_reviews_match", ErrorCode.DUPLICATE_REVIEW
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(code.getStatus().value(), code.name(), e.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, errors));
    }

    /** InvalidParameterException: 쿼리 문자열이 올바른 UTF-8이 아닐 때 (Tomcat이 파라미터를 해석하지 못함) */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, InvalidParameterException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return toResponse(ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return toResponse(ErrorCode.IMAGE_TOO_LARGE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        ErrorCode errorCode = CONSTRAINT_ERRORS.entrySet().stream()
                .filter(entry -> message.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(ErrorCode.CONFLICT);
        return toResponse(errorCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // Spring MVC 표준 예외(405, 404 등)는 원래 상태 코드를 유지한다
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            ErrorCode errorCode = status.value() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_INPUT;
            return ResponseEntity.status(status)
                    .body(new ErrorResponse(status.value(), errorCode.name(), springError.getBody().getDetail(), List.of()));
        }
        log.error("Unexpected error", e);
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }
}
