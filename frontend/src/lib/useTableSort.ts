import { useState } from "react";

export type SortDir = "asc" | "desc";
export type SortValue = string | number | boolean | null | undefined;
export interface SortState<K extends string> {
  key: K;
  dir: SortDir;
}

/** 빈 값(null·undefined·"")은 방향과 상관없이 맨 뒤 */
function isEmpty(v: SortValue): boolean {
  return v == null || v === "";
}

/** 숫자는 크기, 글자는 한국어 순(숫자 포함 글자는 자연 정렬: "2팀" < "10팀"), 참/거짓은 거짓 먼저 */
export function compareValues(a: SortValue, b: SortValue): number {
  if (typeof a === "number" && typeof b === "number") return a - b;
  if (typeof a === "boolean" && typeof b === "boolean") return Number(a) - Number(b);
  return String(a).localeCompare(String(b), "ko", { numeric: true, sensitivity: "base" });
}

/**
 * 표 열 제목을 눌러 정렬. 같은 열을 누를 때마다 오름차순 → 내림차순 → 원래 순서(서버 순서)로 돌아간다.
 * accessors 는 열 key 별로 정렬에 쓸 값을 돌려준다(화면 글자와 달라도 됨, 예: 날짜 문자열·일수 숫자).
 * 같은 값끼리는 원래 순서를 지킨다.
 */
export function useTableSort<T, K extends string>(rows: T[], accessors: Record<K, (row: T) => SortValue>) {
  const [sort, setSort] = useState<SortState<K> | null>(null);

  let sorted = rows;
  if (sort) {
    const get = accessors[sort.key];
    const sign = sort.dir === "asc" ? 1 : -1;
    sorted = rows
      .map((row, index) => ({ row, index, value: get(row) }))
      .sort((x, y) => {
        const ex = isEmpty(x.value);
        const ey = isEmpty(y.value);
        if (ex || ey) return ex === ey ? x.index - y.index : ex ? 1 : -1;
        return compareValues(x.value, y.value) * sign || x.index - y.index;
      })
      .map((x) => x.row);
  }

  const toggle = (key: K) =>
    setSort((s) => (s?.key !== key ? { key, dir: "asc" } : s.dir === "asc" ? { key, dir: "desc" } : null));

  return { sorted, sort, toggle };
}
