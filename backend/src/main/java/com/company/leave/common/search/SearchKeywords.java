package com.company.leave.common.search;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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

    /** 부서 검색용 부서 정보(id·상위 id·이름). */
    public record DepartmentNode(Long id, Long parentId, String name) {
    }

    /**
     * 이름에 단어가 들어 있는 부서와 그 하위 부서 전부의 id.
     * 상위 부서 이름으로 찾으면 하위 부서도 함께 나오게 한다(예: "연구소" → 연구소·QA·개발팀).
     */
    public static Set<Long> departmentSubtrees(List<DepartmentNode> departments, String token) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (DepartmentNode node : departments) {
            if (node.parentId() != null) {
                children.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node.id());
            }
        }
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        departments.stream()
                .filter(node -> node.name() != null && node.name().toLowerCase(Locale.ROOT).contains(token))
                .forEach(node -> stack.push(node.id()));
        while (!stack.isEmpty()) {
            Long id = stack.pop();
            if (result.add(id)) {
                children.getOrDefault(id, List.of()).forEach(stack::push);
            }
        }
        return result;
    }

    /** SQL LIKE 패턴: %단어% (와일드카드 문자는 그대로 검색되도록 이스케이프). */
    public static String likePattern(String token) {
        return "%" + token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
