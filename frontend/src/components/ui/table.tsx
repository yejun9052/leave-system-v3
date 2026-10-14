import * as React from "react";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { cn } from "@/lib/utils";
import type { SortState } from "@/lib/useTableSort";

const Table = React.forwardRef<HTMLTableElement, React.HTMLAttributes<HTMLTableElement>>(
  ({ className, ...props }, ref) => (
    <div className="relative w-full overflow-x-auto">
      <table ref={ref} className={cn("w-full caption-bottom text-sm", className)} {...props} />
    </div>
  ),
);
Table.displayName = "Table";

const TableHeader = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => (
    <thead ref={ref} className={cn("[&_tr]:border-b", className)} {...props} />
  ),
);
TableHeader.displayName = "TableHeader";

const TableBody = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => (
    <tbody ref={ref} className={cn("[&_tr:last-child]:border-0", className)} {...props} />
  ),
);
TableBody.displayName = "TableBody";

const TableRow = React.forwardRef<HTMLTableRowElement, React.HTMLAttributes<HTMLTableRowElement>>(
  ({ className, ...props }, ref) => (
    <tr
      ref={ref}
      className={cn("border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted", className)}
      {...props}
    />
  ),
);
TableRow.displayName = "TableRow";

const TableHead = React.forwardRef<HTMLTableCellElement, React.ThHTMLAttributes<HTMLTableCellElement>>(
  ({ className, ...props }, ref) => (
    <th
      ref={ref}
      className={cn("h-10 px-3 text-left align-middle font-medium text-muted-foreground whitespace-nowrap", className)}
      {...props}
    />
  ),
);
TableHead.displayName = "TableHead";

/**
 * 눌러서 정렬하는 열 제목(useTableSort 와 함께 사용). 정렬 중인 열은 ↑(오름차순)/↓(내림차순), 나머지는 흐린 ↕.
 */
function SortableTableHead<K extends string>({
  sortKey,
  sort,
  onSort,
  className,
  children,
}: {
  sortKey: K;
  sort: SortState<K> | null;
  onSort: (key: K) => void;
  className?: string;
  children: React.ReactNode;
}) {
  const active = sort?.key === sortKey ? sort.dir : null;
  const Icon = active === "asc" ? ArrowUp : active === "desc" ? ArrowDown : ArrowUpDown;
  return (
    <TableHead
      className={className}
      aria-sort={active === "asc" ? "ascending" : active === "desc" ? "descending" : "none"}
    >
      <button
        type="button"
        className={cn(
          "-mx-1 inline-flex items-center gap-1 rounded px-1 py-0.5 hover:bg-muted hover:text-foreground",
          active && "text-foreground",
        )}
        title={active === "asc" ? "내림차순으로 정렬" : active === "desc" ? "정렬 해제" : "오름차순으로 정렬"}
        onClick={() => onSort(sortKey)}
      >
        {children}
        <Icon className={cn("h-3.5 w-3.5", !active && "opacity-40")} />
      </button>
    </TableHead>
  );
}

/**
 * 표 칸. 휴대폰 폭(640px 미만)에서는 줄바꿈하지 않는다: 좁은 화면에 표를 욱여넣으면 날짜·이름이 글자마다 꺾이므로
 * 한 줄로 두고 표를 좌우로 밀어 본다(Table 이 가로 스크롤을 감싼다).
 * 설명처럼 긴 글은 그 요소에 TABLE_WRAP_TEXT 를 붙여 일정 폭 안에서 줄바꿈한다.
 */
const TableCell = React.forwardRef<HTMLTableCellElement, React.TdHTMLAttributes<HTMLTableCellElement>>(
  ({ className, ...props }, ref) => (
    <td ref={ref} className={cn("p-3 align-middle max-sm:whitespace-nowrap", className)} {...props} />
  ),
);

/**
 * 한 줄로 두지 않을 긴 글(작업 설명·경고 등): 이 폭 안에서 줄바꿈한다.
 * 칸(TableCell)에 바로 붙여도 휴대폰 폭의 한 줄 규칙을 덮도록 max-sm 도 함께 둔다.
 */
const TABLE_WRAP_TEXT = "min-w-[220px] whitespace-normal max-sm:whitespace-normal";
TableCell.displayName = "TableCell";

export { Table, TableHeader, TableBody, TableRow, TableHead, SortableTableHead, TableCell, TABLE_WRAP_TEXT };
