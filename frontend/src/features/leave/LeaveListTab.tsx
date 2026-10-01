import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { leaveApi } from "@/api/leave";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  SortableTableHead,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatLeaveAmount, formatSpecialRule } from "@/lib/leaveFormat";
import { useTableSort } from "@/lib/useTableSort";
import { useAuthStore } from "@/store/auth";
import { LEAVE_STATUS_LABEL, type LeaveRequest, type LeaveRequestStatus } from "@/types";
import ForceCancelDialog from "./ForceCancelDialog";

const PAGE_SIZE = 20;

/** 상태 보기: 기본은 캘린더에 보이는 휴가(승인 + 취소 요청 중) */
const STATUS_FILTERS: { value: string; label: string; statuses: LeaveRequestStatus[] }[] = [
  { value: "registered", label: "등록된 휴가 (승인·취소 요청)", statuses: ["APPROVED", "CANCEL_REQUESTED"] },
  { value: "pending", label: "결재 대기", statuses: ["PENDING"] },
  { value: "rejected", label: "반려", statuses: ["REJECTED"] },
  { value: "cancelled", label: "취소", statuses: ["CANCELLED"] },
  { value: "all", label: "전체", statuses: [] },
];

const STATUS_VARIANT: Record<LeaveRequestStatus, "default" | "success" | "warning" | "destructive" | "secondary"> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
  CANCEL_REQUESTED: "default",
  CANCELLED: "secondary",
};

const STATUS_RANK: LeaveRequestStatus[] = ["PENDING", "APPROVED", "CANCEL_REQUESTED", "REJECTED", "CANCELLED"];

function period(r: LeaveRequest): string {
  return r.startDate === r.endDate ? r.startDate : `${r.startDate} ~ ${r.endDate}`;
}

/**
 * 결재함 "휴가 목록" 탭: 캘린더에서 날짜를 눌러야 보이던 휴가를 목록으로 찾는다.
 * 팀장은 맡은 부서(하위 포함), 인사관리자·시스템 관리자는 전 직원. 승인된 휴가는 관리자가 여기서 강제 취소할 수 있다.
 */
export default function LeaveListTab() {
  const canForceCancel = useAuthStore((s) => s.hasAnyRole("HR_ADMIN", "SYSTEM_ADMIN"));
  const year = new Date().getFullYear();
  const [statusFilter, setStatusFilter] = useState("registered");
  const [from, setFrom] = useState(`${year}-01-01`);
  const [to, setTo] = useState(`${year}-12-31`);
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [forceCancelling, setForceCancelling] = useState<LeaveRequest | null>(null);

  const statuses = STATUS_FILTERS.find((f) => f.value === statusFilter)?.statuses ?? [];
  const { data, isLoading } = useQuery({
    queryKey: ["leaveList", { search, statusFilter, from, to, page }],
    queryFn: () =>
      leaveApi.search({
        keyword: search || undefined,
        statuses,
        from: from || undefined,
        to: to || undefined,
        page,
        size: PAGE_SIZE,
      }),
  });
  // 서버가 휴가 시작일 최신순으로 페이지를 나눠 주므로 열 정렬은 지금 페이지 안에서만 한다
  const { sorted, sort, toggle } = useTableSort(data?.content ?? [], {
    employee: (r) => r.employeeName,
    department: (r) => r.departmentName,
    type: (r) => r.leaveTypeName,
    period: (r) => `${r.startDate} ${r.endDate}`,
    days: (r) => r.days,
    reason: (r) => r.reason,
    status: (r) => STATUS_RANK.indexOf(r.status),
    approver: (r) => r.approverName,
  });

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(keyword.trim());
  };

  const columns = canForceCancel ? 9 : 8;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label className="text-xs text-muted-foreground">상태</Label>
          <Select
            value={statusFilter}
            onValueChange={(v) => {
              setStatusFilter(v);
              setPage(0);
            }}
          >
            <SelectTrigger className="w-[260px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATUS_FILTERS.map((f) => (
                <SelectItem key={f.value} value={f.value}>
                  {f.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-muted-foreground">기간 (이 기간과 겹치는 휴가)</Label>
          <div className="flex items-center gap-1">
            <Input
              type="date"
              className="w-[150px]"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
            />
            <span className="text-muted-foreground">~</span>
            <Input
              type="date"
              className="w-[150px]"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
            />
          </div>
        </div>
        <form onSubmit={onSearch} className="flex min-w-[240px] flex-1 gap-2">
          <div className="relative max-w-sm flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-9"
              placeholder="이름·부서·종류·사유·결재자·상태 검색"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </div>
          <Button type="submit" variant="secondary">
            검색
          </Button>
        </form>
      </div>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <SortableTableHead sortKey="employee" sort={sort} onSort={toggle}>신청자</SortableTableHead>
                <SortableTableHead sortKey="department" sort={sort} onSort={toggle}>부서</SortableTableHead>
                <SortableTableHead sortKey="type" sort={sort} onSort={toggle}>종류</SortableTableHead>
                <SortableTableHead sortKey="period" sort={sort} onSort={toggle}>기간</SortableTableHead>
                <SortableTableHead sortKey="days" sort={sort} onSort={toggle}>일수</SortableTableHead>
                <SortableTableHead sortKey="reason" sort={sort} onSort={toggle}>사유</SortableTableHead>
                <SortableTableHead sortKey="status" sort={sort} onSort={toggle}>상태</SortableTableHead>
                <SortableTableHead sortKey="approver" sort={sort} onSort={toggle}>결재자</SortableTableHead>
                {canForceCancel && <TableHead className="text-right">관리</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={columns} className="py-8 text-center text-muted-foreground">
                    불러오는 중…
                  </TableCell>
                </TableRow>
              ) : sorted.length > 0 ? (
                sorted.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium">{r.employeeName}</TableCell>
                    <TableCell>{r.departmentName ?? "-"}</TableCell>
                    <TableCell>
                      <span className="inline-flex items-center gap-2">
                        <span
                          className="inline-block h-3 w-3 rounded-full"
                          style={{ backgroundColor: r.leaveTypeColor }}
                        />
                        {r.leaveTypeName}
                        {r.specialRuleName && (
                          <span className="text-xs text-muted-foreground">{formatSpecialRule(r)}</span>
                        )}
                      </span>
                    </TableCell>
                    <TableCell className="whitespace-nowrap">{period(r)}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{formatLeaveAmount(r)}</Badge>
                    </TableCell>
                    <TableCell className="max-w-[180px] truncate text-muted-foreground" title={r.reason ?? ""}>
                      {r.reason || "-"}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[r.status]}>{LEAVE_STATUS_LABEL[r.status]}</Badge>
                    </TableCell>
                    <TableCell>{r.approverName ?? "-"}</TableCell>
                    {canForceCancel && (
                      <TableCell className="text-right">
                        {r.status === "APPROVED" && (
                          <Button
                            size="sm"
                            variant="ghost"
                            className="h-7 px-2 text-destructive hover:text-destructive"
                            onClick={() => setForceCancelling(r)}
                          >
                            강제 취소
                          </Button>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={columns} className="py-8 text-center text-muted-foreground">
                    조건에 맞는 휴가가 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>총 {data?.totalElements ?? 0}건</span>
        {data && data.totalPages > 1 && (
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              이전
            </Button>
            <span>
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

      {forceCancelling && <ForceCancelDialog leave={forceCancelling} onClose={() => setForceCancelling(null)} />}
    </div>
  );
}
