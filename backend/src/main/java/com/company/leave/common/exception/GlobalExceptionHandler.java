package com.company.leave.common.exception;

import com.company.leave.common.dto.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_INPUT.name(),
                        ErrorCode.INVALID_INPUT.defaultMessage(),
                        fieldErrors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        ErrorCode code = ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.name(), code.defaultMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        // 동시 승인/차감 충돌 → 사용자에게 재시도 유도(409)
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(ApiResponse.error(ErrorCode.CONFLICT.name(),
                        "다른 요청과 동시에 처리되어 충돌했습니다. 잠시 후 다시 시도하세요."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // DB 제약 위반(동시성 레이스 등) → 500 대신 재시도 가능한 409. 원인은 서버 로그로.
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(ApiResponse.error(ErrorCode.CONFLICT.name(),
                        "데이터 제약 조건과 충돌했습니다. 잠시 후 다시 시도하거나 입력을 확인하세요."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ErrorCode code = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.name(), code.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.name(), code.defaultMessage()));
    }
}
