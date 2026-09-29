import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { CalendarClock, Users, UserMinus, Inbox, TrendingUp } from "lucide-react";
import { dashboardApi } from "@/api/dashboard";
import { useAuthStore } from "@/store/auth";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { LEAVE_STATUS_LABEL } from "@/types";

export default function DashboardPage() {
  const { user, isManager } = useAuthStore();
  const manager = isManager();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold">안녕하세요, {user?.name}님 👋</h1>
        <p className="text-sm text-muted-foreground">오늘의 휴가 현황을 확인하세요.</p>
      </div>
      <PersonalSection />
      {manager && <AdminSection />}
    </div>
  );
}

function PersonalSection() {
  const { data } = useQuery({ queryKey: ["dashboard", "me"], queryFn: dashboardApi.personal });
  const b = data?.balance;

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat icon={CalendarClock} label="잔여 연차" value={`${b?.remaining ?? 0}일`} accent />
        <Stat icon={TrendingUp} label="올해 사용" value={`${b?.used ?? 0}일`} />
        <Stat icon={Inbox} label="결재 대기중" value={`${data?.pendingCount ?? 0}건`} />
        <Stat icon={Users} label="오늘 팀 부재" value={`${data?.teamOnLeaveToday ?? 0}명`} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">다가오는 휴가</CardTitle>
        </CardHeader>
        <CardContent>
          {data && data.upcoming.length > 0 ? (
            <ul className="divide-y">
              {data.upcoming.map((r) => (
                <li key={r.id} className="flex items-center justify-between py-2">
                  <span className="inline-flex items-center gap-2">
                    <span
                      className="inline-block h-3 w-3 rounded-full"
                      style={{ backgroundColor: r.leaveTypeColor }}
                    />
                    {r.leaveTypeName}
                  </span>
                  <span className="text-sm text-muted-foreground">
                    {r.startDate}
                    {r.startDate !== r.endDate && ` ~ ${r.endDate}`}
                  </span>
                  <Badge variant="success">{LEAVE_STATUS_LABEL[r.status]}</Badge>
                </li>
              ))}
            </ul>
          ) : (
            <p className="py-4 text-sm text-muted-foreground">예정된 휴가가 없습니다.</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function AdminSection() {
  const { data } = useQuery({ queryKey: ["dashboard", "admin"], queryFn: dashboardApi.admin });
  if (!data) return null;

  const palette = ["#4f46e5", "#06b6d4", "#22c55e", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899"];

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">전사 현황</h2>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat icon={Users} label="재직 인원" value={`${data.totalEmployees}명`} />
        <Stat icon={UserMinus} label="오늘 휴가자" value={`${data.onLeaveToday}명`} />
        <Stat icon={Inbox} label="결재 대기" value={`${data.pendingApprovals}건`} />
        <Stat icon={TrendingUp} label="연차 소진율" value={`${data.usageRate}%`} accent />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">월별 휴가 사용 (올해)</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={data.monthlyUsage.map((m) => ({ name: `${m.month}월`, days: m.days }))}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="name" fontSize={12} />
                <YAxis fontSize={12} allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="days" fill="#4f46e5" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">부서별 사용 일수</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart
                layout="vertical"
                data={data.departmentUsage.slice(0, 7).map((d) => ({
                  name: d.departmentName,
                  days: d.days,
                }))}
              >
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" fontSize={12} allowDecimals={false} />
                <YAxis type="category" dataKey="name" width={80} fontSize={12} />
                <Tooltip />
                <Bar dataKey="days" radius={[0, 4, 4, 0]}>
                  {data.departmentUsage.slice(0, 7).map((_, i) => (
                    <Cell key={i} fill={palette[i % palette.length]} />
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

function Stat({
  icon: Icon,
  label,
  value,
  accent,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  accent?: boolean;
}) {
  return (
    <Card className={accent ? "border-primary/40 bg-primary/5" : undefined}>
      <CardContent className="flex items-center gap-3 p-4">
        <div
          className={`flex h-10 w-10 items-center justify-center rounded-lg ${
            accent ? "bg-primary text-primary-foreground" : "bg-secondary text-secondary-foreground"
          }`}
        >
          <Icon className="h-5 w-5" />
        </div>
        <div>
          <p className="text-xs text-muted-foreground">{label}</p>
          <p className={`text-xl font-bold ${accent ? "text-primary" : ""}`}>{value}</p>
        </div>
      </CardContent>
    </Card>
  );
}
