import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  ArrowRight,
  CalendarCheck2,
  CalendarClock,
  CalendarDays,
  Clock,
  Inbox,
  Plus,
  Sun,
  TrendingUp,
  UserMinus,
  Users,
  type LucideIcon,
} from "lucide-react";
import { dashboardApi, type TeamLeave } from "@/api/dashboard";
import { leaveApi } from "@/api/leave";
import { holidayApi, type Holiday } from "@/api/holidays";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/utils";
import { formatDays, formatLeaveAmount } from "@/lib/leaveFormat";
import { LEAVE_STATUS_LABEL, type LeaveRequest, type LeaveRequestStatus } from "@/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { LeaveRequestDialog } from "@/features/leave/MyLeavesPage";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const DAY_MS = 24 * 60 * 60 * 1000;

/** 오늘(브라우저 시간) yyyy-MM-dd */
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** "2026-10-08" → 자정 기준 Date */
function parseIso(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** 오늘부터 그 날까지 남은 날 수(오늘 0, 내일 1) */
function daysFromToday(iso: string): number {
  return Math.round((parseIso(iso).getTime() - parseIso(todayIso()).getTime()) / DAY_MS);
}

/** "2026-10-08" → "2026.10.08" */
const dotted = (iso: string) => iso.replace(/-/g, ".");

/** "2026-10-08" → "10월 8일" */
function monthDay(iso: string): string {
  const d = parseIso(iso);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

function period(start: string, end: string): string {
  return start === end ? dotted(start) : `${dotted(start)} – ${dotted(end)}`;
}

export default function DashboardPage() {
  const { user, isManager } = useAuthStore();
  const manager = isManager();
  const qc = useQueryClient();
  const { toast } = useToast();
  const [applying, setApplying] = useState(false);
  const now = new Date();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold sm:text-3xl">안녕하세요, {user?.name}님 👋</h1>
          <p className="text-muted-foreground">오늘의 휴가와 팀 일정을 한눈에 확인하세요.</p>
          <p className="text-sm text-muted-foreground">
            {now.getFullYear()}년 {now.getMonth() + 1}월 {now.getDate()}일 {WEEKDAYS[now.getDay()]}요일
          </p>
        </div>
        <Button size="lg" onClick={() => setApplying(true)}>
          <Plus className="h-4 w-4" /> 휴가 신청
        </Button>
      </div>

      {/* 관리 전용 계정도 테스트용으로 휴가를 쓸 수 있어 개인 연차·휴가 카드를 보여 준다 */}
      <PersonalSection />
      {manager && <AdminSection />}

      {applying && (
        <LeaveRequestDialog
          onClose={() => setApplying(false)}
          onSaved={(warning) => {
            setApplying(false);
            if (warning) toast({ title: warning, variant: "destructive" });
            for (const key of ["dashboard", "myRequests", "myBalance"]) qc.invalidateQueries({ queryKey: [key] });
          }}
        />
      )}
    </div>
  );
}

function PersonalSection() {
  const { data } = useQuery({ queryKey: ["dashboard", "me"], queryFn: dashboardApi.personal });
  const b = data?.balance;
  const nextStart = b?.periodEnd
    ? (() => {
        const d = parseIso(b.periodEnd);
        d.setDate(d.getDate() + 1);
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      })()
    : null;
  const dDay = nextStart ? daysFromToday(nextStart) : null;
  const pending = data?.pendingCount ?? 0;
  const team = data?.teamOnLeaveToday ?? 0;
  const granted = (b?.granted ?? 0) + (b?.carriedOver ?? 0);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={CalendarCheck2} label="잔여 연차" value={`${formatDays(b?.remaining)}일`} accent
          sub={`이번 기간 부여 ${formatDays(granted)}일`} />
        <Kpi icon={Inbox} label="결재 대기" value={`${pending}건`}
          sub={pending > 0 ? <><span className="mr-1.5 inline-block h-2 w-2 rounded-full bg-amber-500" />내 신청 승인 대기</> : "대기 중인 신청이 없어요"} />
        <Kpi icon={CalendarClock} label="기산일 D-DAY" valueClassName="text-primary"
          value={dDay == null ? "-" : dDay <= 0 ? "D-DAY" : `D-${dDay}`}
          sub={nextStart ? `다음 기산일 ${monthDay(nextStart)}` : "연차 기간 정보 없음"} />
        <Kpi icon={Users} label="오늘 팀 부재" value={`${team}명`}
          sub={team === 0 ? "오늘은 모두 함께해요" : `팀원 ${team}명이 휴가 중이에요`} />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <RecentRequests className="lg:col-span-3" />
        <UsageDonut className="lg:col-span-2" used={b?.used ?? 0} remaining={b?.remaining ?? 0}
          expired={b?.expired ?? 0} granted={granted} periodEnd={b?.periodEnd} />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <UpcomingHolidays className="lg:col-span-3" />
        <TeamSchedule className="lg:col-span-2" leaves={data?.teamLeaves ?? []} loaded={!!data} />
      </div>
    </div>
  );
}

function Kpi({ icon: Icon, label, value, sub, accent, valueClassName }: {
  icon: LucideIcon;
  label: string;
  value: string;
  sub: React.ReactNode;
  accent?: boolean;
  valueClassName?: string;
}) {
  return (
    <Card className={cn(accent && "border-primary/40 bg-primary/5")}>
      <CardContent className="flex items-center gap-4 p-5">
        <div className={cn("flex h-12 w-12 shrink-0 items-center justify-center rounded-xl",
          accent ? "bg-primary/10 text-primary" : "bg-muted text-foreground/70")}>
          <Icon className="h-6 w-6" />
        </div>
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className={cn("text-2xl font-bold", accent && "text-primary", valueClassName)}>{value}</p>
          <p className="flex items-center truncate text-sm text-muted-foreground">{sub}</p>
        </div>
      </CardContent>
    </Card>
  );
}

const STATUS_PILL: Record<LeaveRequestStatus, string> = {
  APPROVED: "bg-emerald-100 text-emerald-700",
  PENDING: "bg-amber-100 text-amber-700",
  CANCEL_REQUESTED: "bg-amber-100 text-amber-700",
  REJECTED: "bg-red-100 text-red-700",
  CANCELLED: "bg-muted text-muted-foreground",
};

/** 표의 짧은 상태 이름(그림처럼 "대기") */
const STATUS_SHORT: Partial<Record<LeaveRequestStatus, string>> = { PENDING: "대기" };

type RecentTab = "ALL" | "PENDING" | "APPROVED";
const RECENT_TABS: { key: RecentTab; label: string }[] = [
  { key: "ALL", label: "전체" },
  { key: "PENDING", label: "대기" },
  { key: "APPROVED", label: "승인" },
];

function leaveIcon(r: LeaveRequest): LucideIcon {
  if (r.portion === "HOURLY") return Clock;
  if (r.portion === "HALF" || r.halfDayPart) return Sun;
  return CalendarDays;
}

function RecentRequests({ className }: { className?: string }) {
  const [tab, setTab] = useState<RecentTab>("ALL");
  const { data, isLoading } = useQuery({ queryKey: ["myRequests"], queryFn: () => leaveApi.myRequests() });
  const rows = [...(data?.content ?? [])]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .filter((r) => tab === "ALL" || (tab === "PENDING" ? r.status === "PENDING" || r.status === "CANCEL_REQUESTED" : r.status === tab))
    .slice(0, 5);

  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-lg">최근 휴가 신청</CardTitle>
        <Link to="/my-leaves" className="flex items-center gap-1 text-sm font-medium text-primary hover:underline">
          전체 보기 <ArrowRight className="h-4 w-4" />
        </Link>
      </CardHeader>
      <CardContent className="space-y-3">
        <div role="tablist" aria-label="신청 상태" className="flex gap-1 border-b">
          {RECENT_TABS.map((t) => (
            <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}
              className={cn("-mb-px border-b-2 px-4 py-2 text-sm transition-colors",
                tab === t.key ? "border-primary font-semibold text-primary" : "border-transparent text-muted-foreground hover:text-foreground")}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[520px] text-sm">
            <thead>
              <tr className="bg-muted/60 text-left text-xs text-muted-foreground">
                <th className="rounded-l-md px-3 py-2 font-medium">휴가 종류</th>
                <th className="px-3 py-2 font-medium">기간</th>
                <th className="px-3 py-2 font-medium">사용 일수</th>
                <th className="rounded-r-md px-3 py-2 font-medium">상태</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {rows.map((r) => {
                const Icon = leaveIcon(r);
                return (
                  <tr key={r.id}>
                    <td className="px-3 py-2.5">
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                          <Icon className="h-5 w-5" style={{ color: r.leaveTypeColor || undefined }} />
                        </div>
                        <div className="min-w-0">
                          <p className="font-medium">{r.leaveTypeName}</p>
                          <p className="truncate text-xs text-muted-foreground">{r.reason || "사유 없음"}</p>
                        </div>
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-3 py-2.5">{period(r.startDate, r.endDate)}</td>
                    <td className="whitespace-nowrap px-3 py-2.5">{formatLeaveAmount(r)}</td>
                    <td className="px-3 py-2.5">
                      <span className={cn("inline-block rounded-full px-3 py-1 text-xs font-medium", STATUS_PILL[r.status])}>
                        {STATUS_SHORT[r.status] ?? LEAVE_STATUS_LABEL[r.status]}
                      </span>
                    </td>
                  </tr>
                );
              })}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={4} className="py-10 text-center text-sm text-muted-foreground">
                    {isLoading ? "불러오는 중…" : "해당하는 신청이 없습니다."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}

function UsageDonut({ className, used, remaining, expired, granted, periodEnd }: {
  className?: string;
  used: number;
  remaining: number;
  expired: number;
  granted: number;
  periodEnd?: string;
}) {
  const rate = granted > 0 ? Math.round((used / granted) * 100) : 0;
  const slices = [
    { name: "사용", value: used, color: "hsl(var(--primary))" },
    { name: "잔여", value: Math.max(remaining, 0), color: "hsl(var(--primary) / 0.15)" },
    { name: "소멸", value: expired, color: "hsl(var(--muted-foreground) / 0.35)" },
  ].filter((s) => s.value > 0);
  const empty = slices.length === 0;

  return (
    <Card className={className}>
      <CardHeader className="pb-2">
        <CardTitle className="text-lg">내 연차 사용 현황</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-col items-center gap-6 sm:flex-row sm:justify-center">
          <div className="relative h-48 w-48 shrink-0">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={empty ? [{ name: "없음", value: 1 }] : slices} dataKey="value" innerRadius="70%" outerRadius="100%"
                  startAngle={90} endAngle={-270} stroke="none" isAnimationActive={false}>
                  {(empty ? [{ color: "hsl(var(--muted))" }] : slices).map((s, i) => <Cell key={i} fill={s.color} />)}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-3xl font-bold">{formatDays(granted)}일</span>
              <span className="text-sm text-muted-foreground">총 부여</span>
            </div>
          </div>
          <div className="w-full max-w-[12rem] space-y-3">
            <Legend color="bg-primary" label="사용" value={used} />
            <Legend color="bg-primary/15" label="잔여" value={remaining} />
            {expired > 0 && <Legend color="bg-muted-foreground/35" label="소멸" value={expired} />}
            <div className="border-t pt-3">
              <p className="text-sm text-muted-foreground">연차 사용률</p>
              <p className="text-3xl font-bold text-primary">{rate}%</p>
            </div>
          </div>
        </div>
        <p className="text-center text-xs text-muted-foreground">
          {periodEnd
            ? `남은 연차는 사용 기한(${dotted(periodEnd)})이 지나면 회사 정책에 따라 이월되거나 소멸됩니다.`
            : "미사용 연차는 회사 정책에 따라 처리됩니다."}
        </p>
      </CardContent>
    </Card>
  );
}

function Legend({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <div className="flex items-center gap-3">
      <span className={cn("h-3 w-3 rounded-full", color)} />
      <span className="flex-1 text-sm text-muted-foreground">{label}</span>
      <span className="text-xl font-bold">{formatDays(value)}일</span>
    </div>
  );
}

function UpcomingHolidays({ className }: { className?: string }) {
  const year = new Date().getFullYear();
  const thisYear = useQuery({ queryKey: ["holidays", year], queryFn: () => holidayApi.list(year) });
  const nextYear = useQuery({ queryKey: ["holidays", year + 1], queryFn: () => holidayApi.list(year + 1) });
  const today = todayIso();
  const upcoming: Holiday[] = [...(thisYear.data ?? []), ...(nextYear.data ?? [])]
    .filter((h) => h.date >= today)
    .sort((a, b) => a.date.localeCompare(b.date))
    .slice(0, 3);

  return (
    <Card className={className}>
      <CardHeader className="pb-2">
        <CardTitle className="text-lg">다가오는 휴일</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {upcoming.map((h) => {
          const d = parseIso(h.date);
          const left = daysFromToday(h.date);
          return (
            <div key={h.date} className="flex items-center gap-3 rounded-xl border px-3 py-3 sm:gap-4 sm:px-4">
              <div className="flex h-12 w-12 shrink-0 flex-col items-center justify-center rounded-lg bg-muted text-xs font-medium leading-tight">
                <span>{d.getMonth() + 1}월</span>
                <span>{d.getDate()}일</span>
              </div>
              <span className="min-w-0 truncate font-medium">{h.name}</span>
              <span className="shrink-0 whitespace-nowrap rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-medium text-red-600">공휴일</span>
              <span className="ml-auto hidden shrink-0 whitespace-nowrap text-xs text-muted-foreground sm:inline">{WEEKDAYS[d.getDay()]}요일</span>
              <span className={cn("ml-auto shrink-0 whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium sm:ml-0",
                left <= 1 ? "bg-red-50 text-red-600" : "bg-muted text-muted-foreground")}>
                {left === 0 ? "오늘" : left === 1 ? "내일" : `D-${left}`}
              </span>
            </div>
          );
        })}
        {upcoming.length === 0 && (
          <p className="py-6 text-center text-sm text-muted-foreground">
            {thisYear.isLoading ? "불러오는 중…" : "등록된 공휴일이 없습니다. 정책 › 공휴일에서 받아 올 수 있습니다."}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function initial(name: string): string {
  return name.trim().charAt(0) || "?";
}

function TeamSchedule({ className, leaves, loaded }: { className?: string; leaves: TeamLeave[]; loaded: boolean }) {
  const [range, setRange] = useState<"today" | "week">("today");
  const hasDept = useAuthStore((s) => s.user?.departmentId != null);
  const today = todayIso();
  const shown = range === "today" ? leaves.filter((l) => l.startDate <= today && l.endDate >= today) : leaves;
  const people = [...new Map(shown.map((l) => [l.employeeId, l.employeeName])).entries()];

  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-lg">팀 일정</CardTitle>
        <Select value={range} onValueChange={(v) => setRange(v as "today" | "week")}>
          <SelectTrigger className="h-8 w-24 border-none text-sm text-muted-foreground shadow-none" aria-label="기간">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="today">오늘</SelectItem>
            <SelectItem value="week">이번 주</SelectItem>
          </SelectContent>
        </Select>
      </CardHeader>
      <CardContent className="space-y-4">
        {!hasDept ? (
          <p className="py-8 text-center text-sm text-muted-foreground">부서가 정해지지 않아 팀 일정이 없습니다.</p>
        ) : shown.length === 0 ? (
          <div className="flex flex-col items-center gap-1 py-6 text-center">
            <Users className="mb-2 h-8 w-8 text-muted-foreground" />
            <p className="font-medium">
              {!loaded ? "불러오는 중…" : range === "today" ? "오늘 휴가 중인 팀원이 없어요" : "이번 주 휴가 예정인 팀원이 없어요"}
            </p>
            {loaded && <p className="text-sm text-muted-foreground">모두 함께 근무 중이에요! 🎉</p>}
          </div>
        ) : (
          <>
            <div className="flex -space-x-2">
              {people.slice(0, 4).map(([id, name]) => (
                <span key={id} title={name}
                  className="flex h-9 w-9 items-center justify-center rounded-full border-2 border-background bg-primary/10 text-sm font-semibold text-primary">
                  {initial(name)}
                </span>
              ))}
              {people.length > 4 && (
                <span className="flex h-9 w-9 items-center justify-center rounded-full border-2 border-background bg-muted text-xs font-semibold">
                  +{people.length - 4}
                </span>
              )}
            </div>
            <ul className="space-y-2">
              {shown.slice(0, 5).map((l, i) => (
                <li key={`${l.employeeId}-${l.startDate}-${i}`} className="flex items-center gap-3 text-sm">
                  <span className="font-medium">{l.employeeName}</span>
                  <span className="text-muted-foreground">{l.leaveTypeName}</span>
                  <span className="ml-auto whitespace-nowrap text-xs text-muted-foreground">
                    {l.startDate === l.endDate ? monthDay(l.startDate) : `${monthDay(l.startDate)} ~ ${monthDay(l.endDate)}`}
                  </span>
                </li>
              ))}
              {shown.length > 5 && <li className="text-xs text-muted-foreground">외 {shown.length - 5}건</li>}
            </ul>
          </>
        )}
        <div className="flex justify-end">
          <Link to="/calendar" className="flex items-center gap-1 text-sm font-medium text-primary hover:underline">
            팀 캘린더 보기 <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}

function AdminSection() {
  const { data } = useQuery({ queryKey: ["dashboard", "admin"], queryFn: dashboardApi.admin });
  const palette = ["#6366f1", "#8b5cf6", "#ec4899", "#14b8a6", "#f59e0b", "#3b82f6", "#10b981"];

  return (
    <div className="space-y-4 pt-2">
      <div>
        <h2 className="text-xl font-bold">회사 현황</h2>
        <p className="text-sm text-muted-foreground">인사관리자·시스템 관리자에게만 보입니다.</p>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={Users} label="재직 인원" value={`${data?.totalEmployees ?? 0}명`} sub="재직 중인 직원" />
        <Kpi icon={UserMinus} label="오늘 부재" value={`${data?.onLeaveToday ?? 0}명`} sub="오늘 승인된 휴가" />
        <Kpi icon={Inbox} label="결재 대기 (전체)" value={`${data?.pendingApprovals ?? 0}건`} sub="신청·취소 요청" />
        <Kpi icon={TrendingUp} label="연차 사용률" value={`${data?.usageRate ?? 0}%`} accent
          sub={`사용 ${formatDays(data?.totalUsed)}일 / 부여 ${formatDays(data?.totalGranted)}일`} />
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
              <BarChart data={(data?.departmentUsage ?? []).slice(0, 7)} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" fontSize={12} />
                <YAxis type="category" dataKey="departmentName" width={90} fontSize={12} />
                <Tooltip formatter={(v) => [`${v}일`, "사용"]} />
                <Bar dataKey="days" radius={[0, 4, 4, 0]}>
                  {(data?.departmentUsage ?? []).slice(0, 7).map((d, i) => (
                    <Cell key={d.departmentName} fill={palette[i % palette.length]} />
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
