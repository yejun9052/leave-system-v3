package com.company.leave.common.exception;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. {@link ErrorCode}로 HTTP 상태와 기본 메시지를 결정한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
