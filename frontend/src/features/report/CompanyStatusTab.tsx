import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Inbox, Search, TrendingUp, UserMinus, Users } from "lucide-react";
import { dashboardApi } from "@/api/dashboard";
import Kpi from "@/components/Kpi";
import QueryError from "@/components/QueryError";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  SortableTableHead,
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDays } from "@/lib/leaveFormat";
import { useTableSort } from "@/lib/useTableSort";

const PALETTE = ["#6366f1", "#8b5cf6", "#ec4899", "#14b8a6", "#f59e0b", "#3b82f6", "#10b981"];

/**
 * 리포트 › 회사 현황(인사관리자·시스템 관리자): 재직 인원·오늘 휴가·결재 대기·연차 사용률,
 * 올해 월별·부서별 사용 그래프, 직원별 사용 표. 사용률과 직원별 표는 직원마다 지금 연차 기간 기준이라 합계가 맞는다.
 */
export default function CompanyStatusTab() {
  const { data, isError, error, refetch } = useQuery({ queryKey: ["dashboard", "admin"], queryFn: dashboardApi.admin });
  if (isError) return <QueryError error={error} onRetry={() => void refetch()} />;
  const departments = (data?.departmentUsage ?? []).slice(0, 7);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={Users} label="재직 인원" value={`${data?.totalEmployees ?? 0}명`} sub="재직 중인 직원" />
        {/* 서버 값은 오늘에 걸린 승인 휴가 신청 건수(사람 수 아님: 한 사람이 오전·오후 반차를 따로 내면 2건) */}
        <Kpi icon={UserMinus} label="오늘 휴가(승인)" value={`${data?.onLeaveToday ?? 0}건`} sub="오늘 승인된 휴가 신청" />
        <Kpi icon={Inbox} label="결재 대기 (전체)" value={`${data?.pendingApprovals ?? 0}건`} sub="신청·취소 요청" />
        {/* 카드 네 개가 한 줄이라 설명은 짧게: 쓸 수 있던 연차 = 부여 + 이월 */}
        <Kpi icon={TrendingUp} label="연차 사용률" value={`${data?.usageRate ?? 0}%`} accent
          sub={`사용 ${formatDays(data?.totalUsed)}일 / ${formatDays((data?.totalGranted ?? 0) + (data?.totalCarriedOver ?? 0))}일`} />
      </div>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-lg">월별 휴가 사용 (올해)</CardTitle>
          </CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data?.monthlyUsage ?? []}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="month" tickFormatter={(m) => `${m}월`} fontSize={12} />
                <YAxis fontSize={12} />
                <Tooltip formatter={(v) => [`${v}일`, "사용"]} labelFormatter={(m) => `${m}월`} />
                <Bar dataKey="days" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-lg">부서별 사용 일수</CardTitle>
          </CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={departments} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" fontSize={12} />
                <YAxis type="category" dataKey="departmentName" width={90} fontSize={12} />
                <Tooltip formatter={(v) => [`${v}일`, "사용"]} />
                <Bar dataKey="days" radius={[0, 4, 4, 0]}>
                  {departments.map((d, i) => (
                    <Cell key={d.departmentName} fill={PALETTE[i % PALETTE.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>
      <EmployeeUsageTable />
    </div>
  );
}

/** 직원별 사용: 이름·부서로 찾고, 열 제목으로 정렬(기본은 부서 → 이름 순) */
function EmployeeUsageTable() {
  const { data: rows = [], isLoading, isError, error, refetch } = useQuery({
    queryKey: ["dashboard", "admin", "employees"],
    queryFn: dashboardApi.employeeUsage,
  });
  const [keyword, setKeyword] = useState("");
  const visible = useMemo(() => {
    const words = keyword.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (words.length === 0) return rows;
    return rows.filter((r) => words.every((w) => `${r.name} ${r.departmentName ?? ""}`.toLowerCase().includes(w)));
  }, [rows, keyword]);
  const { sorted, sort, toggle } = useTableSort(visible, {
    name: (r) => r.name,
    department: (r) => r.departmentName,
    period: (r) => r.periodStart,
    granted: (r) => r.granted + r.carriedOver,
    used: (r) => r.used,
    remaining: (r) => r.remaining,
    rate: (r) => r.usageRate,
  });

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0 pb-2">
        <div>
          <CardTitle className="text-lg">직원별 사용</CardTitle>
          <p className="text-sm text-muted-foreground">재직 중인 직원마다 지금 연차 기간 기준입니다.</p>
        </div>
        <div className="relative w-64">
          <Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="이름 · 부서로 찾기"
            aria-label="직원별 사용에서 이름 · 부서로 찾기"
            className="pl-8"
          />
        </div>
      </CardHeader>
      <CardContent className="p-0">
        {isError ? (
          <div className="p-4">
            <QueryError error={error} onRetry={() => void refetch()} />
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <SortableTableHead sortKey="name" sort={sort} onSort={toggle}>이름</SortableTableHead>
                <SortableTableHead sortKey="department" sort={sort} onSort={toggle}>부서</SortableTableHead>
                <SortableTableHead sortKey="period" sort={sort} onSort={toggle}>연차 기간</SortableTableHead>
                <SortableTableHead sortKey="granted" sort={sort} onSort={toggle}>부여(+이월)</SortableTableHead>
                <SortableTableHead sortKey="used" sort={sort} onSort={toggle}>사용</SortableTableHead>
                <SortableTableHead sortKey="remaining" sort={sort} onSort={toggle}>남은 연차</SortableTableHead>
                <SortableTableHead sortKey="rate" sort={sort} onSort={toggle}>사용률</SortableTableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    {isLoading ? "불러오는 중…" : keyword.trim() ? "맞는 직원이 없습니다." : "직원이 없습니다."}
                  </TableCell>
                </TableRow>
              ) : (
                sorted.map((r) => (
                  <TableRow key={r.employeeId}>
                    <TableCell className="font-medium">{r.name}</TableCell>
                    <TableCell className="text-muted-foreground">{r.departmentName ?? "-"}</TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">
                      {r.periodStart} ~ {r.periodEnd}
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      {formatDays(r.granted)}일
                      {r.carriedOver > 0 && (
                        <span className="text-muted-foreground"> + {formatDays(r.carriedOver)}일</span>
                      )}
                      {r.carriedOver < 0 && (
                        <span className="text-muted-foreground"> − 당겨쓴 {formatDays(-r.carriedOver)}일</span>
                      )}
                    </TableCell>
                    <TableCell>{formatDays(r.used)}일</TableCell>
                    <TableCell className="font-medium">{formatDays(r.remaining)}일</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 w-16 overflow-hidden rounded-full bg-muted">
                          <div className="h-full bg-primary" style={{ width: `${Math.min(r.usageRate, 100)}%` }} />
                        </div>
                        <span className="tabular-nums">{r.usageRate}%</span>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
