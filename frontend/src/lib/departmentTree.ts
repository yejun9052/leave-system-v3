import type { Department } from "@/types";

/** 부서 선택 목록의 한 줄. depth 0 = 최상위 */
export interface DepartmentOption {
  id: number;
  name: string;
  depth: number;
  /** 상위부터 이어 붙인 이름: "개발 › 234" */
  path: string;
}

/**
 * 부서 트리(GET /departments?view=tree)를 부서 관리 트리와 같은 순서(상위 다음 하위, 같은 단계는 서버 정렬순)의
 * 평평한 목록으로 바꾼다. excludeSubtreeOf 부서와 그 하위는 뺀다(부서 이동 때 자기 아래로는 옮길 수 없음).
 */
export function flattenDepartments(tree: Department[], excludeSubtreeOf?: number): DepartmentOption[] {
  const options: DepartmentOption[] = [];
  const walk = (nodes: Department[], depth: number, parentPath: string) => {
    for (const d of nodes) {
      if (d.id === excludeSubtreeOf) continue;
      const path = parentPath ? `${parentPath} › ${d.name}` : d.name;
      options.push({ id: d.id, name: d.name, depth, path });
      walk(d.children ?? [], depth + 1, path);
    }
  };
  walk(tree, 0, "");
  return options;
}
