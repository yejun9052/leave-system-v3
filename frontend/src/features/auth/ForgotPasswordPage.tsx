import { useState } from "react";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { KeyRound, Loader2 } from "lucide-react";
import { authApi } from "@/api/auth";
import { extractErrorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const schema = z.object({
  email: z.string().trim().min(1, "이메일을 입력하세요.").email("이메일 형식이 올바르지 않습니다."),
});
type Values = z.infer<typeof schema>;

/**
 * 비밀번호 찾기(로그인 불필요). 계정 존재 여부를 알 수 없도록 결과와 관계없이 항상 같은 완료 문구를 보인다.
 */
export default function ForgotPasswordPage() {
  const [done, setDone] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: "" } });

  const onSubmit = async (values: Values) => {
    try {
      await authApi.requestPasswordReset(values.email);
      setDone(true);
    } catch (err) {
      setError("root", { message: extractErrorMessage(err, "요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요.") });
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-slate-100 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="space-y-3 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <KeyRound className="h-6 w-6" />
          </div>
          <CardTitle className="text-xl">비밀번호 찾기</CardTitle>
          <CardDescription>가입한 회사 이메일로 재설정 링크를 보내드립니다</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {done ? (
            <p className="text-sm leading-relaxed">
              입력한 이메일이 등록되어 있으면 비밀번호 재설정 링크를 보냈습니다. 메일함(스팸함 포함)을
              확인하세요. 링크는 30분 동안 한 번만 사용할 수 있습니다.
            </p>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <div className="space-y-2">
                <Label htmlFor="email">이메일</Label>
                <Input id="email" type="email" autoComplete="username" placeholder="you@company.com" {...register("email")} />
                {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
              </div>
              {errors.root && <p className="text-sm text-destructive">{errors.root.message}</p>}
              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
                재설정 링크 받기
              </Button>
            </form>
          )}
          <Link to="/login" className="block text-center text-sm text-muted-foreground hover:text-foreground">
            로그인으로 돌아가기
          </Link>
        </CardContent>
      </Card>
    </div>
  );
}
