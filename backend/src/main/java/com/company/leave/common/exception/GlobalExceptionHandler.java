package com.company.leave.common.exception;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.leave.domain.LeaveRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        // 같은 휴가 신청을 두 결재자가 동시에 처리하면 늦게 온 쪽(@Version 충돌) → 이미 처리됨(409)
        if (ex instanceof ObjectOptimisticLockingFailureException o
                && LeaveRequest.class.getName().equals(o.getPersistentClassName())) {
            return ResponseEntity.status(ErrorCode.CONFLICT.status())
                    .body(ApiResponse.error(ErrorCode.CONFLICT.name(), "이미 처리된 신청입니다. 목록을 새로고침해 주세요."));
        }
        // 그 밖의 동시 차감 충돌(잔액 등) → 사용자에게 재시도 유도(409)
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

    /**
     * 브라우저가 먼저 연결을 끊음(실시간 갱신 연결이 열린 탭을 닫거나 숨김 등). 정상적인 끝이고
     * 응답을 받을 곳도 없으므로 오류로 남기지 않는다.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnected(AsyncRequestNotUsableException ex) {
        log.debug("클라이언트 연결 끊김: {}", ex.getMessage());
    }

    /** 업로드 파일이 너무 큼(엑셀 5MB, 요청 8MB). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.INVALID_INPUT, "파일이 너무 큽니다(최대 5MB). 나눠서 올려 주세요.");
    }

    /** 요청 형식 오류: 본문 JSON·값 형식·필수 값·범위(@Min 등). 사용자 입력 실수이므로 400. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.debug("잘못된 요청: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다. 입력을 확인해 주세요.");
    }

    /** 없는 주소(404)·지원하지 않는 방식(405)·형식(406·415). */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return error(HttpStatus.NOT_FOUND, ErrorCode.INVALID_INPUT, "요청한 주소를 찾을 수 없습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.INVALID_INPUT, "지원하지 않는 요청 방식입니다.");
    }

    @ExceptionHandler({HttpMediaTypeNotAcceptableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Void> handleMediaType(Exception ex) {
        // 응답 형식을 맞출 수 없는 경우라 본문 없이 상태만
        return ResponseEntity.status(ex instanceof HttpMediaTypeNotAcceptableException
                ? HttpStatus.NOT_ACCEPTABLE : HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.name(), code.defaultMessage()));
    }
}
