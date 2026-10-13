import { useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  CalendarDays,
  LayoutDashboard,
  CalendarCheck2,
  Inbox,
  Users,
  Building2,
  Settings,
  BarChart3,
  ShieldCheck,
  CalendarCheck,
  LogOut,
  Menu,
  BellRing,
  ChevronRight,
  UserRound,
  X,
} from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { useRealtime } from "@/lib/useRealtime";
import { dashboardApi } from "@/api/dashboard";
import { formatDays } from "@/lib/leaveFormat";
import { ROLE_LABEL, type Role } from "@/types";
import { cn } from "@/lib/utils";
import {
  APPROVAL_TABS,
  CALENDAR_VIEWS,
  POLICY_TABS,
  REPORT_TABS,
  sectionOf,
  type PageSection,
} from "@/lib/pageSections";
import { Button } from "@/components/ui/button";
import NotificationBell, { useUnreadNotifications } from "@/components/NotificationBell";
import LicenseBanner from "@/features/license/LicenseBanner";
import ForcePasswordChangeDialog from "@/features/auth/ForcePasswordChangeDialog";
import { useUnsavedChangesStore } from "@/store/unsavedChanges";

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  roles?: Role[];
  end?: boolean;
}

const NAV: NavItem[] = [
  { to: "/", label: "대시보드", icon: LayoutDashboard, end: true },
  { to: "/calendar", label: "캘린더", icon: CalendarDays },
  { to: "/my-leaves", label: "내 휴가", icon: CalendarCheck2 },
  { to: "/me", label: "내 정보", icon: UserRound },
  { to: "/approvals", label: "결재함", icon: Inbox, roles: ["TEAM_LEAD", "HR_ADMIN", "SYSTEM_ADMIN"] },
  { to: "/admin/employees", label: "사용자 관리", icon: Users, roles: ["HR_ADMIN", "SYSTEM_ADMIN"] },
  { to: "/admin/departments", label: "부서 관리", icon: Building2, roles: ["HR_ADMIN", "SYSTEM_ADMIN"] },
  { to: "/admin/policy", label: "정책 · 휴가종류", icon: Settings, roles: ["HR_ADMIN", "SYSTEM_ADMIN"] },
  { to: "/admin/reports", label: "리포트", icon: BarChart3, roles: ["HR_ADMIN", "SYSTEM_ADMIN"] },
  { to: "/admin/audit", label: "이벤트 로그", icon: ShieldCheck, roles: ["HR_ADMIN", "SYSTEM_ADMIN"] },
];

export default function AppLayout() {
  const { user, logout, hasAnyRole } = useAuthStore();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [bellOpen, setBellOpen] = useState(false);
  // 누가 데이터를 바꾸면 새로고침 없이 지금 화면을 다시 불러온다(비밀번호를 바꾸기 전에는 서버가 막으므로 연결하지 않음)
  useRealtime(!!user && !user.passwordChangeRequired);

  // 관리 전용 계정도 테스트용으로 휴가를 쓸 수 있어 "내 휴가" 메뉴를 보여 준다
  const visibleNav = NAV.filter((n) => !n.roles || hasAnyRole(...n.roles));

  const onLogout = async () => {
    // 저장 안 한 설정이 있으면 로그아웃(세션 종료) 전에 묻는다: 로그아웃 뒤에는 저장할 수 없다
    if (!(await useUnsavedChangesStore.getState().confirmLeave())) return;
    await logout().catch(() => undefined);
    navigate("/login", { replace: true });
  };

  // 비밀번호 변경 전에는 화면(과 그 화면의 API 호출)을 띄우지 않고 변경 창만 보여준다
  if (user?.passwordChangeRequired) {
    return (
      <div className="min-h-dvh bg-muted/30">
        <ForcePasswordChangeDialog />
      </div>
    );
  }

  return (
    <div className="min-h-dvh bg-muted/30">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r bg-background lg:flex">
        <Brand />
        <NavList items={visibleNav} onNavigate={() => setMobileOpen(false)} />
        <SidebarSummary onNavigate={() => setMobileOpen(false)} />
        <div className="border-t p-3">
          <UserCard onLogout={onLogout} name={user?.name} roleLabel={[user?.departmentName, (user ? ROLE_LABEL[user.role] : "")].filter(Boolean).join(" · ")} />
        </div>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col bg-background shadow-xl">
            <div className="flex items-center justify-between pr-3">
              <Brand />
              <Button variant="ghost" size="icon" aria-label="메뉴 닫기" onClick={() => setMobileOpen(false)}>
                <X className="h-5 w-5" />
              </Button>
            </div>
            <NavList items={visibleNav} onNavigate={() => setMobileOpen(false)} />
            <SidebarSummary onNavigate={() => setMobileOpen(false)} />
            <div className="border-t p-3">
              <UserCard onLogout={onLogout} name={user?.name} roleLabel={[user?.departmentName, (user ? ROLE_LABEL[user.role] : "")].filter(Boolean).join(" · ")} />
            </div>
          </aside>
        </div>
      )}

      {/* Main */}
      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b bg-background/80 px-4 backdrop-blur lg:px-8">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            aria-label="메뉴 열기"
            aria-expanded={mobileOpen}
            onClick={() => setMobileOpen(true)}
          >
            <Menu className="h-5 w-5" />
          </Button>
          <Breadcrumb />
          {/* 빈 곳을 채우고 알림 안내는 오른쪽 끝(벨 바로 옆)에 붙인다. 좁으면 안내 글자가 줄어든다 */}
          <div className="flex min-w-0 flex-1 items-center justify-end">
            <UnreadNotice onOpen={() => setBellOpen(true)} />
          </div>
          <NotificationBell open={bellOpen} onOpenChange={setBellOpen} />
          {/* 모바일에서는 이름·부서 칸 폭을 묶어 긴 부서 이름이 알림 안내를 밀어내지 않게 */}
          <div className="max-w-28 text-right sm:max-w-none">
            <p className="truncate text-sm font-medium leading-tight">{user?.name}</p>
            <p className="truncate text-xs text-muted-foreground leading-tight">
              {user?.departmentName ?? "-"}
            </p>
          </div>
        </header>
        <LicenseBanner />
        <main className="p-4 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

// 메뉴·로고는 AppLayout 밖에 둔다. 안에서 만들면 다시 그릴 때마다 새 컴포넌트가 되어 통째로 다시 만들어진다.
function NavList({ items, onNavigate }: { items: NavItem[]; onNavigate: () => void }) {
  return (
    <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto px-3 py-2">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              "relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
              // 선택된 메뉴: 연한 바탕 + 보라 글자 + 왼쪽 막대
              isActive
                ? "bg-primary/10 text-primary before:absolute before:inset-y-1.5 before:-left-3 before:w-1 before:rounded-r-full before:bg-primary"
                : "text-muted-foreground hover:bg-accent hover:text-foreground",
            )
          }
        >
          <item.icon className="h-5 w-5" />
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

/**
 * 메뉴 아래 내 요약: 잔여 연차와 결재 대기(내 신청). 대시보드와 같은 데이터를 함께 쓴다(["dashboard", "me"]).
 * 비밀번호를 바꿔야 하는 동안에는 서버가 막으므로 부르지 않는다.
 */
function SidebarSummary({ onNavigate }: { onNavigate: () => void }) {
  const user = useAuthStore((s) => s.user);
  const { data } = useQuery({
    queryKey: ["dashboard", "me"],
    queryFn: dashboardApi.personal,
    enabled: !!user && !user.passwordChangeRequired,
  });
  if (!data) return null;
  const b = data.balance;
  const pending = data.pendingCount;
  return (
    <div className="space-y-2 px-3 pb-3">
      <Link to="/my-leaves" onClick={onNavigate}
        className="flex items-center gap-3 rounded-xl border border-primary/40 bg-primary/5 p-3 transition-colors hover:bg-primary/10">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <CalendarCheck className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <p className="text-xs text-muted-foreground">잔여 연차</p>
          <p className="text-lg font-bold leading-tight text-primary">{formatDays(b.remaining)}일</p>
          <p className="truncate text-xs text-muted-foreground">
            이번 기간 부여 {formatDays(b.granted + b.carriedOver)}일
          </p>
        </div>
      </Link>
      <Link to="/my-leaves" onClick={onNavigate}
        className="flex items-center gap-3 rounded-xl border bg-background p-3 transition-colors hover:bg-accent">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
          <Inbox className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <p className="text-xs text-muted-foreground">결재 대기</p>
          <p className="text-lg font-bold leading-tight">{pending}건</p>
          <p className="flex items-center truncate text-xs text-muted-foreground">
            {pending > 0 ? (
              <><span className="mr-1.5 inline-block h-2 w-2 rounded-full bg-amber-500" />내 신청 승인 대기</>
            ) : "대기 중인 신청 없음"}
          </p>
        </div>
      </Link>
    </div>
  );
}

/** 탭(보기)이 있는 화면: 주소의 어떤 검색어로 탭을 고르는지 */
const PAGE_SECTIONS: Record<string, { param: string; sections: PageSection[] }> = {
  "/admin/policy": { param: "tab", sections: POLICY_TABS },
  "/approvals": { param: "tab", sections: APPROVAL_TABS },
  "/calendar": { param: "view", sections: CALENDAR_VIEWS },
  "/admin/reports": { param: "tab", sections: REPORT_TABS },
};

/**
 * 상단 왼쪽의 현재 위치: "정책 · 휴가종류 > 연차 정책". 메뉴 이름 다음에 탭이 있는 화면이면 지금 탭을 붙인다.
 * 좁은 화면(휴대폰)에서는 메뉴 버튼과 알림 자리를 위해 숨긴다.
 */
function Breadcrumb() {
  const { pathname } = useLocation();
  const [params] = useSearchParams();
  const item = NAV.find((n) => (n.end ? pathname === n.to : pathname === n.to || pathname.startsWith(`${n.to}/`)));
  if (!item) return null;
  const page = PAGE_SECTIONS[item.to];
  const section = page ? sectionOf(page.sections, params.get(page.param)) : null;
  return (
    <nav aria-label="현재 위치" className="hidden min-w-0 shrink items-center gap-1.5 text-sm sm:flex">
      <item.icon className="h-4 w-4 shrink-0 text-muted-foreground" />
      {section ? (
        <>
          <Link to={item.to} className="truncate text-muted-foreground hover:text-foreground hover:underline">
            {item.label}
          </Link>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="truncate font-medium" aria-current="page">
            {section.label}
          </span>
        </>
      ) : (
        <span className="truncate font-medium" aria-current="page">
          {item.label}
        </span>
      )}
    </nav>
  );
}

/** 상단 알림(벨) 옆 안내: 확인하지 않은 알림이 있으면 눈에 띄게 보여 주고, 누르면 알림 목록을 연다. */
function UnreadNotice({ onOpen }: { onOpen: () => void }) {
  const unread = useUnreadNotifications();
  if (unread === 0) return null;
  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex min-w-0 items-center gap-2 rounded-full border border-red-200 bg-red-50 py-1.5 pl-3 pr-2 text-sm font-medium text-red-700 shadow-sm transition-colors hover:bg-red-100"
    >
      <span className="relative flex h-2.5 w-2.5 shrink-0">
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
        <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-red-500" />
      </span>
      <BellRing className="hidden h-4 w-4 shrink-0 sm:block" />
      <span className="truncate">
        <span className="hidden sm:inline">확인하지 않은 알림 </span>
        <b className="text-base">{unread > 99 ? "99+" : unread}</b>개<span className="hidden sm:inline">가 있어요</span>
      </span>
      <span className="ml-1 hidden shrink-0 items-center rounded-full bg-red-600 px-2.5 py-0.5 text-xs font-semibold text-white sm:flex">
        확인하기 <ChevronRight className="h-3.5 w-3.5" />
      </span>
    </button>
  );
}

function Brand() {
  return (
    <div className="flex h-16 items-center gap-2 px-6">
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <CalendarCheck2 className="h-5 w-5" />
      </div>
      <span className="font-semibold">연차관리</span>
    </div>
  );
}

function UserCard({
  name,
  roleLabel,
  onLogout,
}: {
  name?: string;
  roleLabel: string;
  onLogout: () => void;
}) {
  return (
    <div className="flex items-center gap-3 rounded-lg px-2 py-1.5">
      <div className="flex h-10 w-10 items-center justify-center rounded-full border bg-background text-sm font-semibold">
        {name?.charAt(0) ?? "?"}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{name}</p>
        <p className="truncate text-xs text-muted-foreground">{roleLabel}</p>
      </div>
      <Button variant="ghost" size="icon" onClick={onLogout} title="로그아웃">
        <LogOut className="h-4 w-4" />
      </Button>
    </div>
  );
}
