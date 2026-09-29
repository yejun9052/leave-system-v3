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
import { ChangePasswordForm } from "@/features/auth/ChangePasswordForm";

// 서버 검증과 동일: 이름 필수(100자), 직급 50자, 전화번호 30자
const profileSchema = z.object({
  name: z.string().trim().min(1, "이름을 입력하세요.").max(100, "이름은 100자 이하여야 합니다."),
  position: z.string().max(50, "직급은 50자 이하여야 합니다."),
  phone: z.string().max(30, "전화번호는 30자 이하여야 합니다."),
});
type ProfileValues = z.infer<typeof profileSchema>;

export default function MyInfoPage() {
  const { user, loadMe } = useAuthStore();
  const { toast } = useToast();

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

      <Card>
        <CardHeader>
          <CardTitle>프로필</CardTitle>
          <CardDescription>이메일·부서·권한은 관리자만 변경할 수 있습니다.</CardDescription>
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
