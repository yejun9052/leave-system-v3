import { useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2,
  ChevronRight,
  ChevronDown,
  GripVertical,
  Plus,
  Pencil,
  Trash2,
  MoveRight,
  Users,
} from "lucide-react";
import { departmentApi } from "@/api/departments";
import { flattenDepartments } from "@/lib/departmentTree";
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
  SelectTreeItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { extractErrorMessage } from "@/api/client";
import { cn } from "@/lib/utils";

type DialogMode =
  | { type: "create"; parentId: number | null }
  | { type: "edit"; dept: Department }
  | { type: "move"; dept: Department }
  | null;

/** 끌어 놓을 곳: 부서 id(그 부서의 하위로) 또는 "root"(최상위로) */
type DropTarget = number | "root";
type DragState = { dept: Department; x: number; y: number; target: DropTarget | null };

/** 부서와 그 하위 부서 id 전부(자기 아래로는 옮길 수 없다) */
function subtreeIds(dept: Department): Set<number> {
  const ids = new Set<number>();
  const walk = (d: Department) => {
    ids.add(d.id);
    (d.children ?? []).forEach(walk);
  };
  walk(dept);
  return ids;
}

export default function DepartmentPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const [dialog, setDialog] = useState<DialogMode>(null);
  // 끌기를 끝낸 직후 같은 행에 생기는 클릭은 명단 열기로 보지 않는다
  const dragEndedAt = useRef(0);

  const { data: tree = [], isLoading } = useQuery({
    queryKey: ["departments", "tree"],
    queryFn: departmentApi.tree,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["departments"] });

  const nameById = useMemo(() => new Map(flattenDepartments(tree).map((d) => [d.id, d.name])), [tree]);
  const [drag, setDrag] = useState<DragState | null>(null);

  const moveMutation = useMutation({
    mutationFn: ({ id, parentId }: { id: number; parentId: number | null }) => departmentApi.move(id, parentId),
    onSuccess: () => {
      toast({ title: "부서를 옮겼습니다.", variant: "success" });
      invalidate();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const onDrop = async (dept: Department, target: DropTarget) => {
    const to = target === "root" ? "최상위" : `'${nameById.get(target)}' 아래`;
    const ok = await confirm({
      title: "부서를 옮길까요?",
      description: `'${dept.name}' → ${to}\n하위 부서도 함께 옮겨집니다.`,
      confirmText: "옮기기",
    });
    if (ok) moveMutation.mutate({ id: dept.id, parentId: target === "root" ? null : target });
  };

  /** 부서를 누르면 그 부서 이름으로 검색한 사용자 관리 화면으로 넘어간다(확인 후) */
  const onOpenMembers = async (dept: Department) => {
    if (Date.now() - dragEndedAt.current < 300) return;
    const ok = await confirm({
      title: `'${dept.name}' 부서 명단을 확인하시겠습니까?`,
      description: `사용자 관리로 이동해 '${dept.name}' 검색 결과를 보여 줍니다.`,
      confirmText: "확인",
    });
    if (ok) navigate(`/admin/employees?${new URLSearchParams({ keyword: dept.name })}`);
  };

  /**
   * 손잡이를 눌러 끌기 시작. 마우스·터치 모두 포인터 이벤트로 처리하고, 놓은 부서의 하위로(위에 뜨는 "최상위" 칸이면
   * 최상위로) 옮긴다. 자기 자신·하위 부서·지금 상위 부서 위에서는 놓을 수 없다. Esc 로 취소.
   */
  const startDrag = (dept: Department, e: React.PointerEvent) => {
    if (e.button !== 0 || moveMutation.isPending) return;
    e.preventDefault();
    const startX = e.clientX;
    const startY = e.clientY;
    const blocked = subtreeIds(dept);
    let started = false;
    let target: DropTarget | null = null;

    const targetAt = (x: number, y: number): DropTarget | null => {
      const value = document.elementFromPoint(x, y)?.closest<HTMLElement>("[data-drop]")?.dataset.drop;
      if (!value) return null;
      if (value === "root") return dept.parentId == null ? null : "root";
      const id = Number(value);
      return blocked.has(id) || id === dept.parentId ? null : id;
    };
    const onMove = (ev: PointerEvent) => {
      if (!started && Math.hypot(ev.clientX - startX, ev.clientY - startY) < 5) return;
      started = true;
      target = targetAt(ev.clientX, ev.clientY);
      setDrag({ dept, x: ev.clientX, y: ev.clientY, target });
      // 화면 위·아래 끝으로 끌면 페이지를 스크롤한다
      if (ev.clientY < 60) window.scrollBy(0, -12);
      else if (ev.clientY > window.innerHeight - 60) window.scrollBy(0, 12);
    };
    const finish = (drop: boolean) => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointercancel", onCancel);
      window.removeEventListener("keydown", onKey);
      setDrag(null);
      if (started) dragEndedAt.current = Date.now();
      if (drop && started && target != null) void onDrop(dept, target);
    };
    const onUp = () => finish(true);
    const onCancel = () => finish(false);
    const onKey = (ev: KeyboardEvent) => {
      if (ev.key === "Escape") finish(false);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onCancel);
    window.addEventListener("keydown", onKey);
  };

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
          <p className="text-sm text-muted-foreground">
            부서 계층 구조를 관리합니다. 부서를 누르면 명단을, 왼쪽 손잡이를 끌어 다른 부서 위에 놓으면 그 하위로 옮깁니다.
          </p>
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
                  drag={drag}
                  onDragStart={startDrag}
                  onOpen={onOpenMembers}
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

      {drag && (
        <>
          {/* 최상위로 빼는 칸: 화면 위에 떠 있어 목록 위치가 바뀌지 않는다 */}
          {drag.dept.parentId != null && (
            <div
              data-drop="root"
              className={cn(
                "fixed left-1/2 top-4 z-50 -translate-x-1/2 rounded-full border-2 border-dashed bg-background px-6 py-3 text-sm font-medium shadow-lg",
                drag.target === "root" ? "border-primary bg-primary/10 text-primary" : "text-muted-foreground",
              )}
            >
              여기에 놓으면 최상위 부서로
            </div>
          )}
          {/* 끌고 있는 부서 이름과 놓을 곳 */}
          <div
            className="pointer-events-none fixed z-50 flex items-center gap-2 rounded-md border bg-background px-3 py-1.5 text-sm font-medium shadow-lg"
            style={{ left: drag.x + 12, top: drag.y + 12 }}
          >
            <Building2 className="h-4 w-4 text-primary" />
            {drag.dept.name}
            {drag.target != null && (
              <span className="text-xs font-normal text-muted-foreground">
                → {drag.target === "root" ? "최상위" : nameById.get(drag.target)}
              </span>
            )}
          </div>
        </>
      )}

      {dialog && (
        <DepartmentDialog
          mode={dialog}
          tree={tree}
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
  drag,
  onDragStart,
  onOpen,
  onAddChild,
  onEdit,
  onMove,
  onDelete,
}: {
  dept: Department;
  depth: number;
  drag: DragState | null;
  onDragStart: (d: Department, e: React.PointerEvent) => void;
  onOpen: (d: Department) => void;
  onAddChild: (parentId: number) => void;
  onEdit: (d: Department) => void;
  onMove: (d: Department) => void;
  onDelete: (d: Department) => void;
}) {
  const [open, setOpen] = useState(true);
  const hasChildren = dept.children && dept.children.length > 0;
  const isSource = drag?.dept.id === dept.id;
  const isTarget = drag?.target === dept.id;

  return (
    <li className={cn(isSource && "opacity-40")}>
      <div
        data-drop={dept.id}
        className={cn(
          "group flex items-center gap-2 rounded-lg px-2 py-2",
          !drag && "hover:bg-accent",
          isTarget && "bg-primary/10 ring-2 ring-primary",
          !drag && "cursor-pointer",
        )}
        style={{ paddingLeft: `${depth * 20 + 8}px` }}
        title="눌러서 부서 명단 보기"
        onClick={(e) => {
          // 펼치기·손잡이·관리 버튼을 누른 경우는 제외
          if ((e.target as HTMLElement).closest("button, [role=button]")) return;
          onOpen(dept);
        }}
      >
        <span
          role="button"
          aria-label={`${dept.name} 끌어서 옮기기`}
          title="끌어서 다른 부서 아래로 옮기기"
          className="cursor-grab touch-none text-muted-foreground/60 hover:text-foreground active:cursor-grabbing"
          onPointerDown={(e) => onDragStart(dept, e)}
        >
          <GripVertical className="h-4 w-4" />
        </span>
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

        <div className={cn("ml-auto flex items-center gap-1 opacity-0 transition-opacity", !drag && "group-hover:opacity-100")}>
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
              drag={drag}
              onDragStart={onDragStart}
              onOpen={onOpen}
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
  tree,
  onClose,
  onSaved,
}: {
  mode: NonNullable<DialogMode>;
  tree: Department[];
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

  // 이동 시: 자기 자신과 그 하위 부서는 상위 후보에서 제외(트리 순서·들여쓰기로 표시)
  const parentOptions = flattenDepartments(tree, mode.type === "move" ? mode.dept.id : undefined);

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
                {/* 선택값은 상위 부서부터 경로로: "개발 › 234" */}
                <SelectValue>
                  {newParentId === "root"
                    ? "최상위"
                    : parentOptions.find((d) => String(d.id) === newParentId)?.path}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="root">최상위</SelectItem>
                {parentOptions.map((d) => (
                  <SelectTreeItem key={d.id} value={String(d.id)} depth={d.depth}>
                    {d.name}
                  </SelectTreeItem>
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
