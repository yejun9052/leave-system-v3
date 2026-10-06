import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
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
import { flattenDepartments, type DepartmentOption } from "@/lib/departmentTree";
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
  SortableTableHead,
} from "@/components/ui/table";
import { useTableSort } from "@/lib/useTableSort";
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
  SelectTreeItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { cn } from "@/lib/utils";
import { extractErrorMessage } from "@/api/client";

const ALL_ROLES: Role[] = ["HR_ADMIN", "TEAM_LEAD", "EMPLOYEE"];
const STATUS_LABEL: Record<EmployeeStatus, string> = {
  ACTIVE: "재직",
  ON_LEAVE: "휴직",
  RESIGNED: "퇴사",
};

const ROLE_RANK: Role[] = ["SYSTEM_ADMIN", "HR_ADMIN", "TEAM_LEAD", "EMPLOYEE"];
const STATUS_RANK: EmployeeStatus[] = ["ACTIVE", "ON_LEAVE", "RESIGNED"];

export default function EmployeePage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();
  const fileRef = useRef<HTMLInputElement>(null);

  // 검색어는 주소(?keyword=)에 둔다: 부서 관리에서 부서를 눌러 넘어오면 그 부서 이름으로 검색된 상태로 열린다
  const [searchParams, setSearchParams] = useSearchParams();
  const search = searchParams.get("keyword") ?? "";
  const [keyword, setKeyword] = useState(search);
  const [page, setPage] = useState(0);
  useEffect(() => {
    setKeyword(search);
    setPage(0);
  }, [search]);
  const [editing, setEditing] = useState<Employee | null>(null);
  const [creating, setCreating] = useState(false);

  // 부서 선택은 부서 관리 트리와 같은 순서·들여쓰기로 보여 준다
  const { data: departmentTree = [] } = useQuery({
    queryKey: ["departments", "tree"],
    queryFn: departmentApi.tree,
  });
  const departments = useMemo(() => flattenDepartments(departmentTree), [departmentTree]);

  const { data, isLoading } = useQuery({
    queryKey: ["employees", { search, page }],
    queryFn: () => employeeApi.search({ keyword: search || undefined, page, size: 15 }),
  });
  // 서버가 이름순으로 페이지를 나눠 주므로 정렬은 지금 페이지 안에서만 한다
  const { sorted, sort, toggle } = useTableSort(data?.content ?? [], {
    name: (e) => e.name,
    email: (e) => e.email,
    department: (e) => e.departmentName,
    position: (e) => e.position,
    // 권한은 높은 권한 순(시스템관리자 → 인사관리자 → 팀장 → 사원)
    roles: (e) => Math.min(...e.roles.map((r) => ROLE_RANK.indexOf(r))),
    status: (e) => STATUS_RANK.indexOf(e.status),
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
    const trimmed = keyword.trim();
    setSearchParams(trimmed ? { keyword: trimmed } : {}, { replace: true });
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
            placeholder="이름·이메일·부서·직급·권한·상태 검색"
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
                <SortableTableHead sortKey="name" sort={sort} onSort={toggle}>이름</SortableTableHead>
                <SortableTableHead sortKey="email" sort={sort} onSort={toggle}>이메일</SortableTableHead>
                <SortableTableHead sortKey="department" sort={sort} onSort={toggle}>부서</SortableTableHead>
                <SortableTableHead sortKey="position" sort={sort} onSort={toggle}>직급</SortableTableHead>
                <SortableTableHead sortKey="roles" sort={sort} onSort={toggle}>권한</SortableTableHead>
                <SortableTableHead sortKey="status" sort={sort} onSort={toggle}>상태</SortableTableHead>
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
                sorted.map((e) => (
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
                        {/* 관리 전용 계정은 이메일이 없고 퇴사 처리할 수 없다 */}
                        {!e.systemAccount && <ResetPasswordButton employee={e} />}
                        {e.systemAccount ? null : e.status === "RESIGNED" ? (
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
  departments: DepartmentOption[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const isEdit = !!employee;
  // 관리 전용 계정: 아이디(admin)와 권한은 고칠 수 없고 이름·부서·직급·연락처·입사일만
  const isSystem = !!employee?.systemAccount;
  const [form, setForm] = useState<EmployeeCreate>({
    email: employee?.email ?? "",
    name: employee?.name ?? "",
    departmentId: employee?.departmentId ?? null,
    position: employee?.position ?? "",
    phone: employee?.phone ?? "",
    hireDate: employee?.hireDate ?? new Date().toISOString().slice(0, 10),
    roles: isSystem ? employee!.roles : (employee?.roles.filter((role) => role !== "SYSTEM_ADMIN") ?? ["EMPLOYEE"]),
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
      if (isEdit && employee) {
        return employeeApi.update(
          employee.id,
          isSystem ? { ...payload, email: undefined, roles: undefined } : payload,
        );
      }
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
          <Field label={isSystem ? "아이디 (변경 불가)" : "이메일"}>
            <Input
              type={isSystem ? "text" : "email"}
              value={form.email}
              disabled={isSystem}
              onChange={(e) => set("email", e.target.value)}
            />
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
                {/* 선택값은 상위 부서부터 경로로: "개발 › 234" */}
                <SelectValue placeholder="부서 선택">
                  {form.departmentId
                    ? departments.find((d) => d.id === form.departmentId)?.path
                    : "미배정"}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">미배정</SelectItem>
                {departments.map((d) => (
                  <SelectTreeItem key={d.id} value={String(d.id)} depth={d.depth}>
                    {d.name}
                  </SelectTreeItem>
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
          {isSystem && (
            <p className="text-xs text-muted-foreground">관리 전용 계정의 권한(시스템 관리자)은 바꿀 수 없습니다.</p>
          )}
          <div className="flex flex-wrap gap-2">
            {(isSystem ? form.roles : ALL_ROLES).map((role) => (
              <button
                key={role}
                type="button"
                disabled={isSystem}
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
