import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
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
  LogOut,
  Menu,
  UserRound,
  X,
} from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { ROLE_LABEL, type Role } from "@/types";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import NotificationBell from "@/components/NotificationBell";
import LicenseBanner from "@/features/license/LicenseBanner";
import ForcePasswordChangeDialog from "@/features/auth/ForcePasswordChangeDialog";

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

  // 관리 전용 계정도 테스트용으로 휴가를 쓸 수 있어 "내 휴가" 메뉴를 보여 준다
  const visibleNav = NAV.filter((n) => !n.roles || hasAnyRole(...n.roles));

  const onLogout = async () => {
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
        <div className="border-t p-3">
          <UserCard onLogout={onLogout} name={user?.name} roleLabel={primaryRoleLabel(user?.roles)} />
        </div>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col bg-background shadow-xl">
            <div className="flex items-center justify-between pr-3">
              <Brand />
              <Button variant="ghost" size="icon" onClick={() => setMobileOpen(false)}>
                <X className="h-5 w-5" />
              </Button>
            </div>
            <NavList items={visibleNav} onNavigate={() => setMobileOpen(false)} />
            <div className="border-t p-3">
              <UserCard onLogout={onLogout} name={user?.name} roleLabel={primaryRoleLabel(user?.roles)} />
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
            onClick={() => setMobileOpen(true)}
          >
            <Menu className="h-5 w-5" />
          </Button>
          <div className="flex-1" />
          <NotificationBell />
          <div className="text-right">
            <p className="text-sm font-medium leading-tight">{user?.name}</p>
            <p className="text-xs text-muted-foreground leading-tight">
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
    <nav className="flex flex-1 flex-col gap-1 px-3">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
              isActive
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
            )
          }
        >
          <item.icon className="h-4 w-4" />
          {item.label}
        </NavLink>
      ))}
    </nav>
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

function primaryRoleLabel(roles?: Role[]): string {
  if (!roles || roles.length === 0) return "";
  const order: Role[] = ["SYSTEM_ADMIN", "HR_ADMIN", "TEAM_LEAD", "EMPLOYEE"];
  const top = order.find((r) => roles.includes(r)) ?? roles[0];
  return ROLE_LABEL[top];
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
      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-secondary text-sm font-semibold">
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
