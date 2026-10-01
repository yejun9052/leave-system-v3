package com.company.leave.common.search;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 목록 검색어 도우미. 검색어를 공백으로 나눠 단어마다 "표의 어느 칸이든 포함" 조건을 만들고, 단어끼리는 모두 만족(AND)하게 한다.
 * 화면에 한글로 보이는 값(권한·상태·동작 등)은 코드로 저장되므로, 단어가 한글 표시 이름에 포함되면 그 코드로도 찾는다.
 */
public final class SearchKeywords {

    private SearchKeywords() {
    }

    /** 공백으로 나눈 검색 단어(소문자). 비어 있으면 빈 목록. */
    public static List<String> tokens(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keyword.trim().split("\\s+"))
                .map(t -> t.toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
    }

    /** 표시 이름에 단어가 들어 있는 코드들(예: "로그" → LOGIN, LOGOUT). */
    public static <T> Set<T> codesMatching(Map<T, String> labels, String token) {
        return labels.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).replace(" ", "").contains(token.replace(" ", "")))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** SQL LIKE 패턴: %단어% (와일드카드 문자는 그대로 검색되도록 이스케이프). */
    public static String likePattern(String token) {
        return "%" + token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
