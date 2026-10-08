import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { employeeApi } from "@/api/employees";
import { extractErrorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { ChangePasswordForm } from "@/features/auth/ChangePasswordForm";
import { Badge } from "@/components/ui/badge";
import { ROLE_LABEL, type Me, type Role } from "@/types";

// 서버 검증과 동일: 이름 필수(100자), 직급 50자, 전화번호 30자
const profileSchema = z.object({
  name: z.string().trim().min(1, "이름을 입력하세요.").max(100, "이름은 100자 이하여야 합니다."),
  position: z.string().max(50, "직급은 50자 이하여야 합니다."),
  phone: z.string().max(30, "전화번호는 30자 이하여야 합니다."),
});
type ProfileValues = z.infer<typeof profileSchema>;

const ROLE_ORDER: Role[] = ["SYSTEM_ADMIN", "HR_ADMIN", "TEAM_LEAD", "EMPLOYEE"];

/** 입사일부터 오늘까지 근속 기간: "4년 3개월", 한 달이 안 되면 "1개월 미만" */
function tenure(hireDate: string, today = new Date()): string {
  const [y, m, d] = hireDate.split("-").map(Number);
  let months = (today.getFullYear() - y) * 12 + (today.getMonth() + 1 - m);
  if (today.getDate() < d) months -= 1;
  if (months < 1) return "1개월 미만";
  const years = Math.floor(months / 12);
  const rest = months % 12;
  return [years > 0 ? `${years}년` : "", rest > 0 ? `${rest}개월` : ""].filter(Boolean).join(" ");
}

/** 읽기 전용 기본 정보: 관리자가 정하는 항목(이메일·부서·권한·입사일)과 프로필 값을 한눈에 */
function BasicInfo({ user }: { user: Me }) {
  const roles = ROLE_ORDER.filter((r) => user.roles.includes(r));
  const rows: { label: string; value: React.ReactNode }[] = [
    { label: "이름", value: user.name },
    { label: "이메일", value: user.email },
    { label: "부서", value: user.departmentName ?? "부서 없음" },
    { label: "직급", value: user.position || "-" },
    {
      label: "권한",
      value: (
        <span className="flex flex-wrap gap-1">
          {roles.map((r) => <Badge key={r} variant="secondary">{ROLE_LABEL[r]}</Badge>)}
          {user.systemAccount && <Badge variant="outline">관리 전용 계정</Badge>}
        </span>
      ),
    },
    {
      label: "입사일",
      value: user.hireDate ? (
        <>
          {user.hireDate.replace(/-/g, ".")}
          <span className="ml-2 text-muted-foreground">근속 {tenure(user.hireDate)}</span>
        </>
      ) : "-",
    },
    { label: "전화번호", value: user.phone || "-" },
  ];
  return (
    <dl className="grid grid-cols-[6rem_1fr] gap-x-4 gap-y-3 text-sm">
      {rows.map((r) => (
        <div key={r.label} className="contents">
          <dt className="text-muted-foreground">{r.label}</dt>
          <dd>{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}

export default function MyInfoPage() {
  const { user, loadMe } = useAuthStore();
  const { toast } = useToast();
  const confirm = useConfirm();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
    reset,
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { name: user?.name ?? "", position: user?.position ?? "", phone: user?.phone ?? "" },
  });

  const saveProfile = async (values: ProfileValues) => {
    const ok = await confirm({ title: "프로필을 저장할까요?", confirmText: "저장" });
    if (!ok) return;
    try {
      const saved = await employeeApi.updateMyProfile({
        name: values.name,
        position: values.position || undefined,
        phone: values.phone || undefined,
      });
      reset({ name: saved.name, position: saved.position ?? "", phone: saved.phone ?? "" });
      await loadMe();
      toast({ title: "프로필이 저장되었습니다.", variant: "success" });
    } catch (err) {
      toast({ title: extractErrorMessage(err), variant: "destructive" });
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">내 정보</h1>
        <p className="text-sm text-muted-foreground">{user?.email}</p>
      </div>

      {user && (
        <Card>
          <CardHeader>
            <CardTitle>기본 정보</CardTitle>
            <CardDescription>이메일·부서·권한·입사일은 관리자만 바꿀 수 있습니다. 틀린 곳이 있으면 인사관리자에게 알려 주세요.</CardDescription>
          </CardHeader>
          <CardContent>
            <BasicInfo user={user} />
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>프로필 수정</CardTitle>
          <CardDescription>이름·직급·전화번호는 직접 바꿀 수 있습니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(saveProfile)} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="name">이름</Label>
              <Input id="name" {...register("name")} />
              {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="position">직급</Label>
                <Input id="position" {...register("position")} />
                {errors.position && <p className="text-sm text-destructive">{errors.position.message}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="phone">전화번호</Label>
                <Input id="phone" {...register("phone")} />
                {errors.phone && <p className="text-sm text-destructive">{errors.phone.message}</p>}
              </div>
            </div>
            <Button type="submit" disabled={isSubmitting || !isDirty}>
              {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
              저장
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>비밀번호 변경</CardTitle>
          <CardDescription>변경하면 다른 기기에서의 로그인은 모두 종료됩니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <ChangePasswordForm
            onChanged={() => toast({ title: "비밀번호가 변경되었습니다.", variant: "success" })}
          />
        </CardContent>
      </Card>
    </div>
  );
}
