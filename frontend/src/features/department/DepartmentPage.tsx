import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2,
  ChevronRight,
  ChevronDown,
  Plus,
  Pencil,
  Trash2,
  MoveRight,
  Users,
} from "lucide-react";
import { departmentApi } from "@/api/departments";
import type { Department } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
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
import { extractErrorMessage } from "@/api/client";

type DialogMode =
  | { type: "create"; parentId: number | null }
  | { type: "edit"; dept: Department }
  | { type: "move"; dept: Department }
  | null;

export default function DepartmentPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();
  const [dialog, setDialog] = useState<DialogMode>(null);

  const { data: tree = [], isLoading } = useQuery({
    queryKey: ["departments", "tree"],
    queryFn: departmentApi.tree,
  });
  const { data: flat = [] } = useQuery({
    queryKey: ["departments", "flat"],
    queryFn: departmentApi.flat,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["departments"] });

  const removeMutation = useMutation({
    mutationFn: (id: number) => departmentApi.remove(id),
    onSuccess: () => {
      toast({ title: "부서가 삭제되었습니다.", variant: "success" });
      invalidate();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const onDelete = async (dept: Department) => {
    const ok = await confirm({
      title: `'${dept.name}' 부서를 삭제할까요?`,
      confirmText: "삭제",
      destructive: true,
    });
    if (ok) removeMutation.mutate(dept.id);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">부서 관리</h1>
          <p className="text-sm text-muted-foreground">부서 계층 구조를 관리하고 상·하위로 이동합니다.</p>
        </div>
        <Button onClick={() => setDialog({ type: "create", parentId: null })}>
          <Plus className="h-4 w-4" /> 최상위 부서
        </Button>
      </div>

      <Card>
        <CardContent className="p-2 sm:p-4">
          {isLoading ? (
            <p className="p-4 text-sm text-muted-foreground">불러오는 중…</p>
          ) : tree.length === 0 ? (
            <p className="p-4 text-sm text-muted-foreground">등록된 부서가 없습니다.</p>
          ) : (
            <ul className="space-y-1">
              {tree.map((d) => (
                <DeptNode
                  key={d.id}
                  dept={d}
                  depth={0}
                  onAddChild={(parentId) => setDialog({ type: "create", parentId })}
                  onEdit={(dept) => setDialog({ type: "edit", dept })}
                  onMove={(dept) => setDialog({ type: "move", dept })}
                  onDelete={onDelete}
                />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {dialog && (
        <DepartmentDialog
          mode={dialog}
          flat={flat}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null);
            invalidate();
          }}
        />
      )}
    </div>
  );
}

function DeptNode({
  dept,
  depth,
  onAddChild,
  onEdit,
  onMove,
  onDelete,
}: {
  dept: Department;
  depth: number;
  onAddChild: (parentId: number) => void;
  onEdit: (d: Department) => void;
  onMove: (d: Department) => void;
  onDelete: (d: Department) => void;
}) {
  const [open, setOpen] = useState(true);
  const hasChildren = dept.children && dept.children.length > 0;

  return (
    <li>
      <div
        className="group flex items-center gap-2 rounded-lg px-2 py-2 hover:bg-accent"
        style={{ paddingLeft: `${depth * 20 + 8}px` }}
      >
        <button
          className="text-muted-foreground disabled:opacity-0"
          onClick={() => setOpen((o) => !o)}
          disabled={!hasChildren}
        >
          {hasChildren ? (
            open ? (
              <ChevronDown className="h-4 w-4" />
            ) : (
              <ChevronRight className="h-4 w-4" />
            )
          ) : (
            <ChevronRight className="h-4 w-4 opacity-0" />
          )}
        </button>
        <Building2 className="h-4 w-4 text-primary" />
        <span className="font-medium">{dept.name}</span>
        {dept.leadName && (
          <Badge variant="secondary" className="gap-1">
            팀장 {dept.leadName}
          </Badge>
        )}
        <Badge variant="outline" className="gap-1 text-muted-foreground">
          <Users className="h-3 w-3" /> {dept.memberCount}
        </Badge>

        <div className="ml-auto flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
          <Button size="icon" variant="ghost" title="하위 부서 추가" onClick={() => onAddChild(dept.id)}>
            <Plus className="h-4 w-4" />
          </Button>
          <Button size="icon" variant="ghost" title="수정" onClick={() => onEdit(dept)}>
            <Pencil className="h-4 w-4" />
          </Button>
          <Button size="icon" variant="ghost" title="이동" onClick={() => onMove(dept)}>
            <MoveRight className="h-4 w-4" />
          </Button>
          <Button size="icon" variant="ghost" title="삭제" onClick={() => onDelete(dept)}>
            <Trash2 className="h-4 w-4 text-destructive" />
          </Button>
        </div>
      </div>
      {hasChildren && open && (
        <ul className="space-y-1">
          {dept.children.map((c) => (
            <DeptNode
              key={c.id}
              dept={c}
              depth={depth + 1}
              onAddChild={onAddChild}
              onEdit={onEdit}
              onMove={onMove}
              onDelete={onDelete}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

function DepartmentDialog({
  mode,
  flat,
  onClose,
  onSaved,
}: {
  mode: NonNullable<DialogMode>;
  flat: Department[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const [name, setName] = useState(mode.type === "edit" ? mode.dept.name : "");
  const [newParentId, setNewParentId] = useState<string>(
    mode.type === "move" && mode.dept.parentId ? String(mode.dept.parentId) : "root",
  );

  const title =
    mode.type === "create" ? "부서 추가" : mode.type === "edit" ? "부서 수정" : "부서 이동";

  const save = useMutation({
    mutationFn: async () => {
      if (mode.type === "create") {
        return departmentApi.create({ name, parentId: mode.parentId });
      }
      if (mode.type === "edit") {
        return departmentApi.update(mode.dept.id, {
          name,
          leadId: mode.dept.leadId, // 팀장은 사용자 관리에서 지정 (여기서는 보존)
          sortOrder: mode.dept.sortOrder,
        });
      }
      return departmentApi.move(mode.dept.id, newParentId === "root" ? null : Number(newParentId));
    },
    onSuccess: () => {
      toast({ title: "저장되었습니다.", variant: "success" });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  // 이동 시: 자기 자신은 상위 후보에서 제외
  const parentOptions = flat.filter((d) => mode.type !== "move" || d.id !== mode.dept.id);

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>

        {mode.type !== "move" && (
          <div className="space-y-2">
            <Label>부서명</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="예: 개발팀" />
          </div>
        )}

        {mode.type === "move" && (
          <div className="space-y-2">
            <Label>이동할 상위 부서</Label>
            <Select value={newParentId} onValueChange={setNewParentId}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="root">최상위</SelectItem>
                {parentOptions.map((d) => (
                  <SelectItem key={d.id} value={String(d.id)}>
                    {d.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button
            onClick={async () => {
              const ok = await confirm({
                title:
                  mode.type === "create"
                    ? `'${name}' 부서를 추가할까요?`
                    : mode.type === "edit"
                      ? `'${name}' 부서로 저장할까요?`
                      : `'${mode.dept.name}' 부서를 이동할까요?`,
                confirmText: mode.type === "create" ? "추가" : mode.type === "edit" ? "저장" : "이동",
              });
              if (ok) save.mutate();
            }}
            disabled={save.isPending || (mode.type !== "move" && !name.trim())}
          >
            저장
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
