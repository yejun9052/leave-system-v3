import type { Department } from "@/types";

/** 부서와 그 아래 모든 하위 부서의 id(자신 포함). */
function subtreeIds(d: Department): number[] {
  return [d.id, ...d.children.flatMap(subtreeIds)];
}

/**
 * 부서 트리 체크박스. 상위 부서를 체크하면 하위 부서가 모두 함께 체크되고(해제도 함께),
 * 하위 부서는 하나씩 따로 해제할 수 있다. 선택 값은 체크된 부서 id 그대로다(서버가 하위 부서를 다시 펼치지 않는다).
 */
export default function DepartmentTreeSelect({
  tree,
  selected,
  onChange,
}: {
  tree: Department[];
  selected: Set<number>;
  onChange: (next: Set<number>) => void;
}) {
  const toggle = (d: Department) => {
    const next = new Set(selected);
    const ids = subtreeIds(d);
    if (selected.has(d.id)) {
      ids.forEach((id) => next.delete(id));
    } else {
      ids.forEach((id) => next.add(id));
    }
    onChange(next);
  };

  if (tree.length === 0) {
    return <p className="text-sm text-muted-foreground">부서가 없습니다.</p>;
  }
  return (
    <ul className="space-y-0.5">
      {tree.map((d) => (
        <Node key={d.id} dept={d} depth={0} selected={selected} onToggle={toggle} />
      ))}
    </ul>
  );
}

function Node({
  dept,
  depth,
  selected,
  onToggle,
}: {
  dept: Department;
  depth: number;
  selected: Set<number>;
  onToggle: (d: Department) => void;
}) {
  const checked = selected.has(dept.id);
  // 자신은 빠졌지만 아래에 체크된 부서가 있으면 일부 선택 표시
  const partial = !checked && subtreeIds(dept).slice(1).some((id) => selected.has(id));
  return (
    <li>
      <label
        className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-sm hover:bg-accent"
        style={{ paddingLeft: `${depth * 1.25 + 0.25}rem` }}
      >
        <input
          type="checkbox"
          className="h-4 w-4"
          checked={checked}
          ref={(el) => {
            if (el) el.indeterminate = partial;
          }}
          onChange={() => onToggle(dept)}
        />
        <span>{dept.name}</span>
        <span className="text-xs text-muted-foreground">{dept.memberCount}명</span>
      </label>
      {dept.children.length > 0 && (
        <ul className="space-y-0.5">
          {dept.children.map((c) => (
            <Node key={c.id} dept={c} depth={depth + 1} selected={selected} onToggle={onToggle} />
          ))}
        </ul>
      )}
    </li>
  );
}
