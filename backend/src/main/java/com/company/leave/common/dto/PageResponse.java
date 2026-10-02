package com.company.leave.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 API 의 페이지 응답. Spring Data 의 Page 를 그대로 직렬화하면 JSON 구조가 내부 구현에 묶이므로
 * 화면이 쓰는 값만 담아 구조를 고정한다.
 *
 * <pre>
 * { "content": [...], "totalElements": 42, "totalPages": 3, "number": 0, "size": 20 }
 * </pre>
 *
 * @param number 현재 페이지(0부터)
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(),
                page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
