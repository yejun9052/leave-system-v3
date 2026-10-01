import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, ShieldCheck } from "lucide-react";
import { auditApi } from "@/api/audit";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableRow,
  SortableTableHead,
} from "@/components/ui/table";
import { useTableSort } from "@/lib/useTableSort";

// 삭제·반려성 동작은 붉은 배지로 강조
const NEGATIVE_ACTIONS = new Set(["DELETE", "reject", "cancel", "cancel_reject", "force_cancel"]);

export default function AuditLogPage() {
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["auditLogs", { search, page }],
    queryFn: () => auditApi.list(search, page, 30),
  });
  // 서버가 최신순으로 페이지를 나눠 주므로 정렬은 지금 페이지 안에서만 한다
  const { sorted, sort, toggle } = useTableSort(data?.content ?? [], {
    time: (a) => a.createdAt,
    actor: (a) => a.actorName,
    action: (a) => a.actionLabel,
    entity: (a) => `${a.entityLabel ?? a.entityType} ${a.entityId ?? ""}`,
    result: (a) => a.success,
    detail: (a) => a.detail,
  });

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(keyword);
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <ShieldCheck className="h-6 w-6 text-primary" /> 이벤트 로그
        </h1>
        <p className="text-sm text-muted-foreground">
          시스템 전반의 생성·수정·삭제·로그인 등 모든 활동 기록입니다.
        </p>
      </div>

      <form onSubmit={onSearch} className="flex gap-2">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="사용자·동작·대상·결과·상세·날짜(2026-10-01) 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary">
          검색
        </Button>
      </form>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <SortableTableHead sortKey="time" sort={sort} onSort={toggle} className="w-[170px]">시간</SortableTableHead>
                <SortableTableHead sortKey="actor" sort={sort} onSort={toggle}>사용자</SortableTableHead>
                <SortableTableHead sortKey="action" sort={sort} onSort={toggle}>동작</SortableTableHead>
                <SortableTableHead sortKey="entity" sort={sort} onSort={toggle}>대상</SortableTableHead>
                <SortableTableHead sortKey="result" sort={sort} onSort={toggle}>결과</SortableTableHead>
                <SortableTableHead sortKey="detail" sort={sort} onSort={toggle}>상세</SortableTableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    불러오는 중…
                  </TableCell>
                </TableRow>
              ) : data && data.content.length > 0 ? (
                sorted.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="whitespace-nowrap text-muted-foreground">
                      {new Date(a.createdAt).toLocaleString("ko-KR")}
                    </TableCell>
                    <TableCell className="font-medium">{a.actorName}</TableCell>
                    <TableCell>
                      <Badge variant={NEGATIVE_ACTIONS.has(a.action) ? "destructive" : "secondary"}>
                        {a.actionLabel}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {a.entityLabel ?? a.entityType}
                      {a.entityId && <span className="text-muted-foreground"> #{a.entityId}</span>}
                    </TableCell>
                    <TableCell>
                      <Badge variant={a.success ? "success" : "destructive"}>
                        {a.success ? "성공" : "실패"}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-[320px] truncate text-xs text-muted-foreground" title={a.detail}>
                      {a.detail}
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                    기록이 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            이전
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} / {data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}
    </div>
  );
}
