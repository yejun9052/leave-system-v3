import { useQuery } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { TrendingUp } from "lucide-react";
import { dashboardApi } from "@/api/dashboard";
import Kpi from "@/components/Kpi";
import QueryError from "@/components/QueryError";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDays } from "@/lib/leaveFormat";

const PALETTE = ["#6366f1", "#8b5cf6", "#ec4899", "#14b8a6", "#f59e0b", "#3b82f6", "#10b981"];

/**
 * 리포트 › 회사 현황: 연차 사용률과 올해 월별·부서별 사용 그래프(인사관리자·시스템 관리자).
 * 오늘 휴가·결재 대기 같은 매일 보는 숫자는 대시보드에 남긴다. 같은 데이터(["dashboard", "admin"])를 같이 쓴다.
 */
export default function CompanyStatusTab() {
  const { data, isError, error, refetch } = useQuery({ queryKey: ["dashboard", "admin"], queryFn: dashboardApi.admin });
  if (isError) return <QueryError error={error} onRetry={() => void refetch()} />;
  const departments = (data?.departmentUsage ?? []).slice(0, 7);

  return (
    <div className="space-y-4">
      {/* 카드가 하나라 설명(사용·부여·이월 일수)이 잘리지 않게 넓게 */}
      <div className="max-w-md">
        <Kpi icon={TrendingUp} label="연차 사용률" value={`${data?.usageRate ?? 0}%`} accent
          sub={`사용 ${formatDays(data?.totalUsed)}일 / 부여 ${formatDays(data?.totalGranted)}일`
            + (data?.totalCarriedOver ? ` + 이월 ${formatDays(data.totalCarriedOver)}일` : "")} />
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
    </div>
  );
}
