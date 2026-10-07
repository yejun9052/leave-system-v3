import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Building2, ChevronDown } from "lucide-react";
import { departmentApi } from "@/api/departments";
import DepartmentTreeSelect from "@/components/DepartmentTreeSelect";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** 펼친 패널 너비(px) */
const PANEL_WIDTH = 288;

/**
 * 목록 검색 옆의 "부서" 필터 버튼. 누르면 부서 트리 체크박스가 열린다(리포트와 같은 규칙:
 * 상위 부서를 체크하면 하위 부서도 함께, 하위 부서는 따로 해제). 아무것도 고르지 않으면 부서 조건 없음.
 */
export default function DepartmentFilter({
  selected,
  onChange,
}: {
  selected: Set<number>;
  onChange: (next: Set<number>) => void;
}) {
  const [open, setOpen] = useState(false);
  // 버튼이 화면 오른쪽 끝에 있으면 패널을 왼쪽으로 펼친다(화면 밖으로 잘리지 않게)
  const [alignRight, setAlignRight] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { data: tree = [] } = useQuery({ queryKey: ["departments", "tree"], queryFn: departmentApi.tree });

  // 바깥을 누르거나 Esc 를 누르면 닫는다
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className="relative" ref={ref}>
      <Button
        type="button"
        variant={selected.size > 0 ? "secondary" : "outline"}
        aria-expanded={open}
        onClick={() => {
          const rect = ref.current?.getBoundingClientRect();
          setAlignRight(!!rect && rect.left + PANEL_WIDTH > window.innerWidth - 8);
          setOpen((o) => !o);
        }}
      >
        <Building2 className="h-4 w-4" />
        {selected.size > 0 ? `부서 ${selected.size}곳` : "부서"}
        <ChevronDown className="h-4 w-4 opacity-60" />
      </Button>
      {open && (
        <div
          className={cn(
            "absolute top-full z-50 mt-1 rounded-md border bg-popover p-2 text-popover-foreground shadow-md",
            alignRight ? "right-0" : "left-0",
          )}
          style={{ width: PANEL_WIDTH }}
        >
          <p className="px-1 pb-1 text-xs text-muted-foreground">
            상위 부서를 체크하면 하위 부서도 함께 체크됩니다. 하위 부서는 따로 해제할 수 있습니다.
          </p>
          <div className="max-h-80 overflow-y-auto">
            <DepartmentTreeSelect tree={tree} selected={selected} onChange={onChange} />
          </div>
          <div className="mt-2 flex items-center justify-between border-t pt-2">
            <span className="text-xs text-muted-foreground">
              {selected.size > 0 ? `${selected.size}곳 선택` : "전체 부서"}
            </span>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="h-7"
              disabled={selected.size === 0}
              onClick={() => onChange(new Set())}
            >
              선택 해제
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
