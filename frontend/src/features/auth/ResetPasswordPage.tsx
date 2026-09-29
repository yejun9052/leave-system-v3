import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound, Loader2 } from "lucide-react";
import { authApi } from "@/api/auth";
import { extractErrorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { newPasswordSchema, type NewPasswordValues } from "./passwordSchema";

/**
 * 메일 링크(/reset-password?token=...)로 들어와 새 비밀번호를 정하는 화면(로그인 불필요).
 * 토큰이 주소창·방문 기록에 남지 않도록 진입하자마자 주소에서 지운다.
 */
export default function ResetPasswordPage() {
  // 최초 렌더 시 한 번만 읽어 보관(주소에서 지운 뒤에도 유지)
  const [token] = useState(() => new URLSearchParams(window.location.search).get("token") ?? "");
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (window.location.search) {
      window.history.replaceState(window.history.state, "", window.location.pathname);
    }
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<NewPasswordValues>({
    resolver: zodResolver(newPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  const onSubmit = async (values: NewPasswordValues) => {
    try {
      await authApi.confirmPasswordReset(token, values.newPassword);
      setDone(true);
    } catch (err) {
      setError("root", { message: extractErrorMessage(err, "비밀번호를 변경하지 못했습니다.") });
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-slate-100 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="space-y-3 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <KeyRound className="h-6 w-6" />
          </div>
          <CardTitle className="text-xl">비밀번호 재설정</CardTitle>
          <CardDescription>새로 사용할 비밀번호를 입력하세요</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {done ? (
            <>
              <p className="text-sm leading-relaxed">
                비밀번호가 변경되었습니다. 보안을 위해 기존에 로그인된 모든 기기에서 로그아웃되었습니다.
              </p>
              <Button asChild className="w-full">
                <Link to="/login">로그인하기</Link>
              </Button>
            </>
          ) : !token ? (
            <p className="text-sm leading-relaxed">
              링크가 올바르지 않습니다. 메일의 링크를 다시 열거나{" "}
              <Link to="/forgot-password" className="underline">
                비밀번호 찾기
              </Link>
              를 다시 요청하세요.
            </p>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <div className="space-y-2">
                <Label htmlFor="newPassword">새 비밀번호 (8~72자)</Label>
                <Input id="newPassword" type="password" autoComplete="new-password" {...register("newPassword")} />
                {errors.newPassword && <p className="text-sm text-destructive">{errors.newPassword.message}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmPassword">새 비밀번호 확인</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  {...register("confirmPassword")}
                />
                {errors.confirmPassword && (
                  <p className="text-sm text-destructive">{errors.confirmPassword.message}</p>
                )}
              </div>
              {errors.root && (
                <p className="text-sm text-destructive">
                  {errors.root.message}{" "}
                  <Link to="/forgot-password" className="underline">
                    비밀번호 찾기
                  </Link>
                </p>
              )}
              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
                비밀번호 변경
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
