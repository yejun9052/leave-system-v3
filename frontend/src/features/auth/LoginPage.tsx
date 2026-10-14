import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { CalendarCheck2, Clock, Loader2 } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { extractErrorMessage, safeReturnPath } from "@/api/client";

/** "아이디 저장"으로 기억해 둔 아이디(이 브라우저에만. 비밀번호는 저장하지 않는다) */
const SAVED_LOGIN_ID_KEY = "savedLoginId";

function readSavedLoginId(): string {
  try {
    return localStorage.getItem(SAVED_LOGIN_ID_KEY) ?? "";
  } catch {
    return "";
  }
}

function writeSavedLoginId(id: string | null) {
  try {
    if (id) localStorage.setItem(SAVED_LOGIN_ID_KEY, id);
    else localStorage.removeItem(SAVED_LOGIN_ID_KEY);
  } catch {
    // 저장소를 못 쓰면 기억하지 않는다
  }
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);
  /** 자동 로그아웃 등으로 로그인 화면에 온 이유 */
  const notice = useAuthStore((s) => s.signedOutNotice);
  const [savedId] = useState(readSavedLoginId);
  const [email, setEmail] = useState(savedId);
  const [password, setPassword] = useState("");
  const [saveId, setSaveId] = useState(savedId !== "");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 돌아갈 화면: 앱 안 이동은 라우터 state, 세션이 끊겨 페이지를 새로 연 경우는 ?from=
  const from =
    safeReturnPath((location.state as { from?: string } | null)?.from) ??
    safeReturnPath(new URLSearchParams(location.search).get("from")) ??
    "/";

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      writeSavedLoginId(saveId ? email.trim() : null);
      navigate(from, { replace: true });
    } catch (err) {
      setError(extractErrorMessage(err, "로그인에 실패했습니다."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-slate-100 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="space-y-3 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <CalendarCheck2 className="h-6 w-6" />
          </div>
          <CardTitle className="text-xl">연차관리 시스템</CardTitle>
          <CardDescription>회사 이메일로 로그인하세요 (관리자는 아이디)</CardDescription>
        </CardHeader>
        <CardContent>
          {notice && (
            <p
              role="status"
              className="mb-4 flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900"
            >
              <Clock className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
              {notice}
            </p>
          )}
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">이메일 또는 아이디</Label>
              <Input
                id="email"
                type="text"
                autoComplete="username"
                autoFocus={savedId === ""}
                placeholder="you@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">비밀번호</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                autoFocus={savedId !== ""}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <div className="space-y-1">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="h-4 w-4 shrink-0 accent-primary"
                  checked={saveId}
                  onChange={(e) => setSaveId(e.target.checked)}
                  aria-describedby="save-id-note"
                />
                <span className="whitespace-nowrap">아이디 저장</span>
              </label>
              {/* 좁은 화면에서 첫 줄이 쪼개지지 않게 주의 문구는 아래 줄로(체크박스 폭만큼 들여쓰기) */}
              <p id="save-id-note" className="pl-6 text-xs text-muted-foreground">
                공용 PC에서는 체크하지 마세요.
              </p>
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={loading}>
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              로그인
            </Button>
            <Link
              to="/forgot-password"
              className="block text-center text-sm text-muted-foreground hover:text-foreground"
            >
              비밀번호를 잊으셨나요?
            </Link>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
