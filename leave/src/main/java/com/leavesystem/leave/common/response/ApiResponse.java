package com.leavesystem.leave.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

/**
 * 모든 API 의 공통 응답 포맷.
 *
 * <pre>
 * 성공: { "success": true,  "data": {...} }
 * 실패: { "success": false, "error": { "status": 400, "message": "..." } }
 * </pre>
 *
 * null 필드는 직렬화에서 제외되므로 성공 응답에 {@code error} 가,
 * 실패 응답에 {@code data} 가 나타나지 않는다.
 *
 * <p>컴포넌트 이름이 {@code isSuccess} 인 이유: 레코드 접근자 {@code success()} 가
 * 정적 팩토리 {@code success()} 와 시그니처가 겹쳐 컴파일되지 않는다.
 * JSON 필드명은 {@code @JsonProperty} 로 {@code success} 에 고정한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @JsonProperty("success") boolean isSuccess,
        T data,
        ApiError error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 반환할 데이터가 없는 성공 응답 (등록·수정·삭제 등). */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> error(HttpStatus status, String message) {
        return new ApiResponse<>(false, null, new ApiError(status.value(), message));
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<>(false, null, new ApiError(status, message));
    }
}
