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
  Eye,
  EyeOff,
} from "lucide-react";
import { employeeApi, type EmployeeCreate } from "@/api/employees";
import { departmentApi } from "@/api/departments";
import { tokenStore } from "@/lib/tokenStore";
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
    // 인증 헤더가 필요하므로 fetch 후 blob 다운로드
    fetch(employeeApi.exportUrl, {
      headers: { Authorization: `Bearer ${tokenStore.getAccess()}` },
    })
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
            placeholder="이름, 이메일, 사번 검색"
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
                            onClick={() => reactivateMutation.mutate(e.id)}
                          >
                            <UserCheck className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            size="icon"
                            variant="ghost"
                            title="퇴사 처리"
                            onClick={() => {
                              if (confirm(`${e.name} 님을 퇴사 처리할까요?`)) resignMutation.mutate(e.id);
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

function PasswordInput({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className="relative">
      <Input
        type={show ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        autoComplete="new-password"
        className="pr-10"
      />
      <button
        type="button"
        tabIndex={-1}
        onClick={() => setShow((s) => !s)}
        title={show ? "숨기기" : "보기"}
        className="absolute inset-y-0 right-0 flex items-center pr-3 text-muted-foreground hover:text-foreground"
      >
        {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  );
}

function ResetPasswordButton({ employee }: { employee: Employee }) {
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [pw, setPw] = useState("");
  const mutation = useMutation({
    mutationFn: () => employeeApi.resetPassword(employee.id, pw),
    onSuccess: () => {
      toast({ title: "비밀번호가 초기화되었습니다.", variant: "success" });
      setOpen(false);
      setPw("");
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });
  return (
    <>
      <Button size="icon" variant="ghost" title="비밀번호 초기화" onClick={() => setOpen(true)}>
        <KeyRound className="h-4 w-4" />
      </Button>
      {open && (
        <Dialog open onOpenChange={(o) => !o && setOpen(false)}>
          <DialogContent className="max-w-sm">
            <DialogHeader>
              <DialogTitle>{employee.name} 비밀번호 초기화</DialogTitle>
            </DialogHeader>
            <div className="space-y-2">
              <Label>새 비밀번호 (8자 이상)</Label>
              <PasswordInput value={pw} onChange={setPw} />
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setOpen(false)}>
                취소
              </Button>
              <Button disabled={pw.length < 8 || mutation.isPending} onClick={() => mutation.mutate()}>
                초기화
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </>
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
  const isEdit = !!employee;
  const [form, setForm] = useState<EmployeeCreate>({
    email: employee?.email ?? "",
    name: employee?.name ?? "",
    employeeNo: employee?.employeeNo ?? "",
    departmentId: employee?.departmentId ?? null,
    position: employee?.position ?? "",
    phone: employee?.phone ?? "",
    hireDate: employee?.hireDate ?? new Date().toISOString().slice(0, 10),
    roles: employee?.roles ?? ["EMPLOYEE"],
    initialPassword: "",
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
        employeeNo: form.employeeNo || undefined,
        departmentId: form.departmentId ?? null,
        position: form.position || undefined,
        phone: form.phone || undefined,
        hireDate: form.hireDate,
        roles: form.roles.length ? form.roles : (["EMPLOYEE"] as Role[]),
      };
      if (isEdit && employee) return employeeApi.update(employee.id, payload);
      return employeeApi.create({ ...payload, initialPassword: form.initialPassword || undefined });
    },
    onSuccess: () => {
      toast({ title: "저장되었습니다.", variant: "success" });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

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
          <Field label="사번">
            <Input value={form.employeeNo} onChange={(e) => set("employeeNo", e.target.value)} />
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
          {!isEdit && (
            <Field label="초기 비밀번호 (미입력 시 기본값)">
              <PasswordInput
                value={form.initialPassword ?? ""}
                onChange={(v) => set("initialPassword", v)}
                placeholder="welcome1234!"
              />
            </Field>
          )}
        </div>

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
            onClick={() => save.mutate()}
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
