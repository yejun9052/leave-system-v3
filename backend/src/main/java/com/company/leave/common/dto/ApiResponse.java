package com.company.leave.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 표준 API 응답 래퍼.
 *
 * <pre>
 * { "success": true,  "data": {...},  "error": null }
 * { "success": false, "data": null,   "error": { "code": "...", "message": "..." } }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, null));
    }

    public static ApiResponse<Void> error(String code, String message, Object details) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, details));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, Object details) {
    }
}
