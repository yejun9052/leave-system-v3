import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Plus,
  Search,
  Pencil,
  UserX,
  UserCheck,
  KeyRound,
  Upload,
  Download,
} from "lucide-react";
import { employeeApi, type EmployeeCreate } from "@/api/employees";
import { departmentApi } from "@/api/departments";
import {
  ROLE_LABEL,
  type Employee,
  type EmployeeStatus,
  type Role,
} from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { cn } from "@/lib/utils";
import { extractErrorMessage } from "@/api/client";

const ALL_ROLES: Role[] = ["SUPER_ADMIN", "HR_ADMIN", "TEAM_LEAD", "EMPLOYEE"];
const STATUS_LABEL: Record<EmployeeStatus, string> = {
  ACTIVE: "재직",
  ON_LEAVE: "휴직",
  RESIGNED: "퇴사",
};

export default function EmployeePage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();
  const fileRef = useRef<HTMLInputElement>(null);

  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Employee | null>(null);
  const [creating, setCreating] = useState(false);

  const { data: departments = [] } = useQuery({
    queryKey: ["departments", "flat"],
    queryFn: departmentApi.flat,
  });

  const { data, isLoading } = useQuery({
    queryKey: ["employees", { search, page }],
    queryFn: () => employeeApi.search({ keyword: search || undefined, page, size: 15 }),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["employees"] });

  const resignMutation = useMutation({
    mutationFn: (id: number) => employeeApi.resign(id),
    onSuccess: () => {
      toast({ title: "퇴사 처리되었습니다.", variant: "success" });
      invalidate();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });
  const reactivateMutation = useMutation({
    mutationFn: (id: number) => employeeApi.reactivate(id),
    onSuccess: () => {
      toast({ title: "복원되었습니다.", variant: "success" });
      invalidate();
    },
  });

  const onImport = async (file: File) => {
    const ok = await confirm({
      title: "엑셀 파일로 사용자를 일괄 등록할까요?",
      description: `${file.name}\n등록된 사용자마다 임시 비밀번호 메일이 발송됩니다.`,
      confirmText: "등록",
    });
    if (!ok) return;
    try {
      const result = await employeeApi.importExcel(file);
      toast({
        title: `${result.created}명 등록 완료`,
        description: result.errors.length ? `오류 ${result.errors.length}건` : undefined,
        variant: result.errors.length ? "default" : "success",
      });
      if (result.errors.length) console.warn("Import errors:", result.errors);
      invalidate();
    } catch (e) {
      toast({ title: extractErrorMessage(e), variant: "destructive" });
    }
  };

  const onExport = () => {
    // 세션 쿠키로 인증(같은 출처) → fetch 후 blob 다운로드
    fetch(employeeApi.exportUrl, { credentials: "same-origin" })
      .then((r) => r.blob())
      .then((blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "employees.xlsx";
        a.click();
        URL.revokeObjectURL(url);
      });
  };

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(keyword);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">사용자 관리</h1>
          <p className="text-sm text-muted-foreground">직원 계정과 권한, 부서 배치를 관리합니다.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <input
            ref={fileRef}
            type="file"
            accept=".xlsx"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) onImport(f);
              e.target.value = "";
            }}
          />
          <Button variant="outline" onClick={() => fileRef.current?.click()}>
            <Upload className="h-4 w-4" /> 엑셀 등록
          </Button>
          <Button variant="outline" onClick={onExport}>
            <Download className="h-4 w-4" /> 내보내기
          </Button>
          <Button onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" /> 사용자 추가
          </Button>
        </div>
      </div>

      <form onSubmit={onSearch} className="flex gap-2">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="이름, 이메일 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary">
          검색
        </Button>
      </form>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>이름</TableHead>
                <TableHead>이메일</TableHead>
                <TableHead>부서</TableHead>
                <TableHead>직급</TableHead>
                <TableHead>권한</TableHead>
                <TableHead>상태</TableHead>
                <TableHead className="text-right">관리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    불러오는 중…
                  </TableCell>
                </TableRow>
              ) : data && data.content.length > 0 ? (
                data.content.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell className="font-medium">{e.name}</TableCell>
                    <TableCell className="text-muted-foreground">{e.email}</TableCell>
                    <TableCell>{e.departmentName ?? "-"}</TableCell>
                    <TableCell>{e.position ?? "-"}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {e.roles.map((r) => (
                          <Badge key={r} variant="secondary">
                            {ROLE_LABEL[r]}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={e.status === "ACTIVE" ? "success" : "outline"}>
                        {STATUS_LABEL[e.status]}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button size="icon" variant="ghost" title="수정" onClick={() => setEditing(e)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <ResetPasswordButton employee={e} />
                        {e.status === "RESIGNED" ? (
                          <Button
                            size="icon"
                            variant="ghost"
                            title="복원"
                            onClick={async () => {
                              const ok = await confirm({
                                title: `${e.name} 님을 재직 상태로 복원할까요?`,
                                description: "복원하면 다시 로그인할 수 있습니다.",
                                confirmText: "복원",
                              });
                              if (ok) reactivateMutation.mutate(e.id);
                            }}
                          >
                            <UserCheck className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            size="icon"
                            variant="ghost"
                            title="퇴사 처리"
                            onClick={async () => {
                              const ok = await confirm({
                                title: `${e.name} 님을 퇴사 처리할까요?`,
                                description: "퇴사 처리하면 즉시 로그아웃되고 더 이상 로그인할 수 없습니다.",
                                confirmText: "퇴사 처리",
                                destructive: true,
                              });
                              if (ok) resignMutation.mutate(e.id);
                            }}
                          >
                            <UserX className="h-4 w-4 text-destructive" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    사용자가 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            이전
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} / {data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}

      {(creating || editing) && (
        <EmployeeDialog
          employee={editing}
          departments={departments}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSaved={() => {
            setCreating(false);
            setEditing(null);
            invalidate();
          }}
        />
      )}
    </div>
  );
}

function ResetPasswordButton({ employee }: { employee: Employee }) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const mutation = useMutation({
    mutationFn: () => employeeApi.sendPasswordResetMail(employee.id),
    onSuccess: () => toast({ title: "재설정 메일을 보냈습니다.", variant: "success" }),
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });
  const onClick = async () => {
    const ok = await confirm({
      title: "비밀번호 재설정 메일을 보낼까요?",
      description:
        `${employee.name}(${employee.email})님에게 비밀번호 재설정 링크를 메일로 보냅니다.\n` +
        "링크는 30분 동안 한 번만 쓸 수 있으며, 본인이 새 비밀번호를 정하기 전까지 기존 비밀번호는 그대로입니다.",
      confirmText: "메일 발송",
    });
    if (ok) mutation.mutate();
  };
  return (
    <Button size="icon" variant="ghost" title="재설정 메일 발송" onClick={onClick} disabled={mutation.isPending}>
      <KeyRound className="h-4 w-4" />
    </Button>
  );
}

function EmployeeDialog({
  employee,
  departments,
  onClose,
  onSaved,
}: {
  employee: Employee | null;
  departments: { id: number; name: string }[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const isEdit = !!employee;
  const [form, setForm] = useState<EmployeeCreate>({
    email: employee?.email ?? "",
    name: employee?.name ?? "",
    departmentId: employee?.departmentId ?? null,
    position: employee?.position ?? "",
    phone: employee?.phone ?? "",
    hireDate: employee?.hireDate ?? new Date().toISOString().slice(0, 10),
    roles: employee?.roles ?? ["EMPLOYEE"],
  });

  const set = <K extends keyof EmployeeCreate>(k: K, v: EmployeeCreate[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const toggleRole = (role: Role) =>
    setForm((f) => ({
      ...f,
      roles: f.roles.includes(role) ? f.roles.filter((r) => r !== role) : [...f.roles, role],
    }));

  const save = useMutation({
    mutationFn: async () => {
      const payload = {
        email: form.email,
        name: form.name,
        departmentId: form.departmentId ?? null,
        position: form.position || undefined,
        phone: form.phone || undefined,
        hireDate: form.hireDate,
        roles: form.roles.length ? form.roles : (["EMPLOYEE"] as Role[]),
      };
      if (isEdit && employee) return employeeApi.update(employee.id, payload);
      return employeeApi.create(payload);
    },
    onSuccess: () => {
      toast({
        title: isEdit ? "저장되었습니다." : "사용자를 추가하고 임시 비밀번호 메일을 보냈습니다.",
        variant: "success",
      });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const roleText = (roles: Role[]) =>
    (roles.length ? roles : (["EMPLOYEE"] as Role[])).map((r) => ROLE_LABEL[r]).join(", ");

  const onSave = async () => {
    const lines: string[] = [`${form.name.trim()} (${form.email.trim()})`];
    if (isEdit && employee) {
      const before = roleText(employee.roles);
      const after = roleText(form.roles);
      lines.push(before === after ? `권한: ${after}` : `권한 변경: ${before} → ${after}`);
    } else {
      lines.push(`권한: ${roleText(form.roles)}`);
      lines.push("임시 비밀번호가 사용자 이메일로 발송됩니다.");
    }
    const ok = await confirm({
      title: isEdit ? "사용자 정보를 저장할까요?" : "사용자를 추가할까요?",
      description: lines.join("\n"),
      confirmText: isEdit ? "저장" : "추가",
    });
    if (ok) save.mutate();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "사용자 수정" : "사용자 추가"}</DialogTitle>
        </DialogHeader>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="이름">
            <Input value={form.name} onChange={(e) => set("name", e.target.value)} />
          </Field>
          <Field label="이메일">
            <Input type="email" value={form.email} onChange={(e) => set("email", e.target.value)} />
          </Field>
          <Field label="직급">
            <Input value={form.position} onChange={(e) => set("position", e.target.value)} />
          </Field>
          <Field label="부서">
            <Select
              value={form.departmentId ? String(form.departmentId) : "none"}
              onValueChange={(v) => set("departmentId", v === "none" ? null : Number(v))}
            >
              <SelectTrigger>
                <SelectValue placeholder="부서 선택" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">미배정</SelectItem>
                {departments.map((d) => (
                  <SelectItem key={d.id} value={String(d.id)}>
                    {d.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field label="전화번호">
            <Input value={form.phone} onChange={(e) => set("phone", e.target.value)} />
          </Field>
          <Field label="입사일">
            <Input type="date" value={form.hireDate} onChange={(e) => set("hireDate", e.target.value)} />
          </Field>
        </div>
        {!isEdit && (
          <p className="text-sm text-muted-foreground">
            저장하면 임시 비밀번호가 사용자 이메일로 발송됩니다. 사용자는 첫 로그인 때 비밀번호를 변경해야 합니다.
          </p>
        )}

        <div className="space-y-2">
          <Label>권한</Label>
          <div className="flex flex-wrap gap-2">
            {ALL_ROLES.map((role) => (
              <button
                key={role}
                type="button"
                onClick={() => toggleRole(role)}
                className={cn(
                  "rounded-full border px-3 py-1 text-sm transition-colors",
                  form.roles.includes(role)
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-input hover:bg-accent",
                )}
              >
                {ROLE_LABEL[role]}
              </button>
            ))}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button
            onClick={onSave}
            disabled={save.isPending || !form.name.trim() || !form.email.trim()}
          >
            저장
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
    </div>
  );
}
