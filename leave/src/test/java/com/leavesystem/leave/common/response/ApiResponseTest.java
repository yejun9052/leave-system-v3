package com.leavesystem.leave.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("성공 응답에는 error 가 빠진다")
    void success() {
        String json = mapper.writeValueAsString(
                ApiResponse.success(Map.of("id", 1)));

        assertEquals("{\"success\":true,\"data\":{\"id\":1}}", json);
    }

    @Test
    @DisplayName("데이터 없는 성공 응답은 success 만 남는다")
    void successWithoutData() {
        String json = mapper.writeValueAsString(ApiResponse.success());

        assertEquals("{\"success\":true}", json);
    }

    @Test
    @DisplayName("실패 응답에는 data 가 빠지고 error 에 상태코드·메시지가 담긴다")
    void error() {
        String json = mapper.writeValueAsString(
                ApiResponse.error(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"));

        assertEquals(
                "{\"success\":false,\"error\":{\"status\":400,\"message\":\"잘못된 요청입니다\"}}",
                json);
    }
}
