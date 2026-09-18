package com.leavesystem.leave.common.response;

/**
 * 실패 응답의 에러 정보.
 *
 * @param status  HTTP 상태 코드
 * @param message 클라이언트에 노출할 메시지
 */
public record ApiError(int status, String message) {
}
