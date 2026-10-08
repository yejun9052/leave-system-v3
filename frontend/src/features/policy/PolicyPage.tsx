import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Save, Trash2, PlayCircle } from "lucide-react";
import {
  policyApi,
  leaveTypeApi,
  leaveAdminApi,
  policyRulesApi,
  type Policy,
  type GrantBasis,
  type LeaveTypeInput,
  type SpecialRule,
  type Blackout,
  type BlackoutConflictMode,
  BLACKOUT_CONFLICT_LABEL,
} from "@/api/policy";
import { ANNUAL_DEDUCTION_LABEL, type AnnualDeductionMode, type LeavePortion, type LeaveType } from "@/types";
import { formatDays } from "@/lib/leaveFormat";
import { useTableSort, type SortValue } from "@/lib/useTableSort";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  SortableTableHead,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";
import { extractErrorMessage } from "@/api/client";
import { cn } from "@/lib/utils";
import HolidayTab from "./HolidayTab";
import PromotionTab from "./PromotionTab";
import SpecialRuleEditDialog from "./SpecialRuleEditDialog";
import BlackoutSaveDialog from "./BlackoutSaveDialog";
import AutomationTab from "./AutomationTab";
import BackupTab from "./BackupTab";

const PORTION_LABEL: Record<LeavePortion, string> = {
  FULL: "종일",
  HALF: "반차",
  HOURLY: "시간차",
};

const DEDUCTION_MODES: AnnualDeductionMode[] = ["DEDUCT", "EXHAUST_FIRST", "NONE"];

const DEDUCTION_MODE_DESC: Record<AnnualDeductionMode, string> = {
  DEDUCT: "쓴 일수만큼 연차에서 뺍니다. 예: 연차·반차·시간차",
  EXHAUST_FIRST: "사용 가능한 연차가 1일 미만일 때만 신청할 수 있고, 승인되면 남은 연차는 소멸됩니다. 예: 병가·공가",
  NONE: "연차와 상관없이 신청하고 연차에서 빼지 않습니다. 예: 경조사 휴가",
};

const TABS = ["policy", "types", "rules", "blackout", "holidays", "promotion", "automation", "backup"];

export default function PolicyPage() {
  // ?tab=backup 처럼 주소로 탭을 열 수 있다(알림의 "백업 확인하기")
  const [params, setParams] = useSearchParams();
  const requested = params.get("tab");
  const tab = requested && TABS.includes(requested) ? requested : "policy";
  const changeTab = (v: string) => setParams(v === "policy" ? {} : { tab: v }, { replace: true });
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">정책 · 휴가종류</h1>
        <p className="text-sm text-muted-foreground">연차 운영 정책과 휴가 종류를 설정합니다.</p>
      </div>
      <Tabs value={tab} onValueChange={changeTab}>
        <TabsList className="flex-wrap h-auto">
          <TabsTrigger value="policy">연차 정책</TabsTrigger>
          <TabsTrigger value="types">휴가 종류</TabsTrigger>
          <TabsTrigger value="rules">포상 · 경조사</TabsTrigger>
          <TabsTrigger value="blackout">블랙아웃</TabsTrigger>
          <TabsTrigger value="holidays">공휴일</TabsTrigger>
          <TabsTrigger value="promotion">촉진 · 미사용</TabsTrigger>
          <TabsTrigger value="automation">자동화</TabsTrigger>
          <TabsTrigger value="backup">백업</TabsTrigger>
        </TabsList>
        <TabsContent value="policy">
          <PolicyTab />
        </TabsContent>
        <TabsContent value="types">
          <LeaveTypeTab />
        </TabsContent>
        <TabsContent value="rules">
          <RulesTab />
        </TabsContent>
        <TabsContent value="blackout">
          <BlackoutTab />
        </TabsContent>
        <TabsContent value="holidays">
          <HolidayTab />
        </TabsContent>
        <TabsContent value="promotion">
          <PromotionTab />
        </TabsContent>
        <TabsContent value="automation">
          <AutomationTab />
        </TabsContent>
        <TabsContent value="backup">
          <BackupTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function PolicyTab() {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["policy"], queryFn: policyApi.get });
  const [form, setForm] = useState<Omit<Policy, "id"> | null>(null);

  useEffect(() => {
    if (data) {
      const { id: _id, ...rest } = data;
      void _id;
      setForm(rest);
    }
  }, [data]);

  const set = <K extends keyof Omit<Policy, "id">>(k: K, v: Omit<Policy, "id">[K]) =>
    setForm((f) => (f ? { ...f, [k]: v } : f));

  const save = useMutation({
    mutationFn: () => policyApi.update(form!),
    onSuccess: () => {
      toast({ title: "정책이 저장되었습니다.", variant: "success" });
      qc.invalidateQueries({ queryKey: ["policy"] });
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const grant = useMutation({
    mutationFn: () => leaveAdminApi.grantAll(),
    onSuccess: (r) => toast({ title: `${r.granted}명 연차 부여/재계산 완료`, variant: "success" }),
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  if (!form) return <p className="text-sm text-muted-foreground">불러오는 중…</p>;

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">부여 기준</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>연차 부여 기준</Label>
            <Select value={form.grantBasis} onValueChange={(v) => set("grantBasis", v as GrantBasis)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="HIRE_DATE">입사일 기준 (근로기준법 원칙)</SelectItem>
                <SelectItem value="FISCAL_YEAR">회계연도 기준 (일괄 부여)</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {form.grantBasis === "FISCAL_YEAR" && (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>회계 시작 월</Label>
                <Input
                  type="number"
                  min={1}
                  max={12}
                  value={form.fiscalStartMonth}
                  onChange={(e) => set("fiscalStartMonth", Number(e.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label>회계 시작 일</Label>
                <Input
                  type="number"
                  min={1}
                  max={31}
                  value={form.fiscalStartDay}
                  onChange={(e) => set("fiscalStartDay", Number(e.target.value))}
                />
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">사용 규칙</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <ToggleRow
            label="반차 사용 허용"
            checked={form.halfDayEnabled}
            onChange={(v) => set("halfDayEnabled", v)}
          />
          <ToggleRow
            label="시간차 사용"
            desc="1시간(0.125일) 단위, 한 건 1~3시간, 기본 꺼짐"
            checked={form.hourlyEnabled}
            onChange={(v) => set("hourlyEnabled", v)}
          />
          <ToggleRow
            label="마이너스 연차 허용"
            desc="잔여 연차를 초과해 신청 가능"
            checked={form.allowNegative}
            onChange={(v) => set("allowNegative", v)}
          />
          <ToggleRow
            label="다음 연차 기간 예약 허용"
            desc="다음 기산일 이후 날짜도 신청(그 기간 예상 부여 일수 안에서, 다음 기간 끝까지)"
            checked={form.nextPeriodReservationEnabled}
            onChange={(v) => set("nextPeriodReservationEnabled", v)}
          />
          <ToggleRow
            label="미사용 연차 이월"
            checked={form.carryOverEnabled}
            onChange={(v) => set("carryOverEnabled", v)}
          />
          {form.carryOverEnabled && (
            <div className="space-y-2">
              <Label>최대 이월 일수</Label>
              <Input
                type="number"
                step="0.5"
                value={form.maxCarryOverDays}
                onChange={(e) => set("maxCarryOverDays", Number(e.target.value))}
              />
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">근속 가산 연차</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>기본 연차(1년 이상)</Label>
              <Input type="number" step="0.5" value={form.baseAnnualDays}
                onChange={(e) => set("baseAnnualDays", Number(e.target.value))} />
            </div>
            <div className="space-y-2">
              <Label>상한(최대 연차)</Label>
              <Input type="number" step="0.5" value={form.maxAnnualDays}
                onChange={(e) => set("maxAnnualDays", Number(e.target.value))} />
            </div>
            <div className="space-y-2">
              <Label>가산 주기(년)</Label>
              <Input type="number" min={1} value={form.seniorityStepYears}
                onChange={(e) => set("seniorityStepYears", Number(e.target.value))} />
            </div>
            <div className="space-y-2">
              <Label>가산량(일)</Label>
              <Input type="number" step="0.5" value={form.seniorityIncrementDays}
                onChange={(e) => set("seniorityIncrementDays", Number(e.target.value))} />
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            예) 기본 15 · 주기 2년 · 가산 1일 · 상한 25 → 3년차 16일, 5년차 17일 … (근로기준법 기본값)
          </p>
          <ToggleRow label="1년 미만 월차 부여" desc="1개월 개근 시 1일" checked={form.monthlyAccrualEnabled}
            onChange={(v) => set("monthlyAccrualEnabled", v)} />
          {form.monthlyAccrualEnabled && (
            <div className="space-y-2">
              <Label>월차 상한(일)</Label>
              <Input type="number" min={0} value={form.monthlyAccrualMax}
                onChange={(e) => set("monthlyAccrualMax", Number(e.target.value))} />
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">사용 통제</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>팀 동시 부재 최대 인원 (0 = 무제한)</Label>
            <Input type="number" min={0} value={form.maxConcurrentAbsence}
              onChange={(e) => set("maxConcurrentAbsence", Number(e.target.value))} />
          </div>
          <div className="space-y-2">
            <Label>최소 사전 신청 기한(일, 0 = 제한 없음)</Label>
            <Input type="number" min={0} value={form.minAdvanceDays}
              onChange={(e) => set("minAdvanceDays", Number(e.target.value))} />
          </div>
          <div className="space-y-2">
            <Label>최대 연속 사용일 (0 = 무제한)</Label>
            <Input type="number" min={0} value={form.maxConsecutiveDays}
              onChange={(e) => set("maxConsecutiveDays", Number(e.target.value))} />
          </div>
          <div className="space-y-2">
            <Label>사용 금지 기간을 등록할 때 겹치는 기존 휴가</Label>
            <div className="grid gap-2">
              {(Object.keys(BLACKOUT_CONFLICT_LABEL) as BlackoutConflictMode[]).map((mode) => (
                <label
                  key={mode}
                  className={cn(
                    "flex cursor-pointer gap-3 rounded-md border p-3 text-sm",
                    form.blackoutConflictMode === mode && "border-primary bg-primary/5",
                  )}
                >
                  <input
                    type="radio"
                    name="blackoutConflictMode"
                    className="mt-0.5"
                    checked={form.blackoutConflictMode === mode}
                    onChange={() => set("blackoutConflictMode", mode)}
                  />
                  <span>
                    <span className="font-medium">
                      {BLACKOUT_CONFLICT_LABEL[mode]}
                      {mode === "KEEP_APPROVED" && " (기본)"}
                    </span>
                    <span className="block text-xs text-muted-foreground">
                      {mode === "KEEP_APPROVED"
                        ? "승인된 휴가(취소 요청 중 포함)는 그대로 두고, 결재 대기 휴가만 자동 반려합니다."
                        : "승인된 휴가(취소 요청 중 포함)도 자동 취소하고 연차를 돌려주며, 결재 대기 휴가는 자동 반려합니다."}
                    </span>
                  </span>
                </label>
              ))}
            </div>
            <p className="text-xs text-muted-foreground">
              경조사·공가처럼 금지 기간에도 신청할 수 있는 종류는 처리하지 않습니다. 처리된 휴가마다 당사자에게 메일과 알림이
              갑니다. 정책을 바꿔도 이미 등록된 금지 기간의 휴가는 다시 처리하지 않습니다.
            </p>
          </div>
        </CardContent>
      </Card>

      <div className="flex flex-wrap gap-2 lg:col-span-2">
        <Button
          onClick={async () => {
            const ok = await confirm({
              title: "정책을 저장할까요?",
              description: "저장하면 이후 연차 계산에 바로 적용됩니다.",
              confirmText: "저장",
            });
            if (ok) save.mutate();
          }}
          disabled={save.isPending}
        >
          <Save className="h-4 w-4" /> 정책 저장
        </Button>
        <Button
          variant="outline"
          onClick={async () => {
            const ok = await confirm({
              title: "전 직원 연차를 부여/재계산할까요?",
              description: "재직 중인 전 직원의 올해 연차를 다시 계산해 부여합니다.",
              confirmText: "부여/재계산",
            });
            if (ok) grant.mutate();
          }}
          disabled={grant.isPending}
        >
          <PlayCircle className="h-4 w-4" /> 전 직원 연차 부여/재계산
        </Button>
      </div>
    </div>
  );
}

function ToggleRow({
  label,
  desc,
  checked,
  onChange,
}: {
  label: string;
  desc?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between">
      <div>
        <p className="text-sm font-medium">{label}</p>
        {desc && <p className="text-xs text-muted-foreground">{desc}</p>}
      </div>
      <Switch checked={checked} onCheckedChange={onChange} />
    </div>
  );
}

function LeaveTypeTab() {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const { data: types = [] } = useQuery({
    queryKey: ["leaveTypes", "all"],
    queryFn: () => leaveTypeApi.list(true),
  });
  const [editing, setEditing] = useState<LeaveType | null>(null);
  const [creating, setCreating] = useState(false);
  const { sorted, sort, toggle } = useTableSort(types, {
    name: (t) => t.name,
    code: (t) => t.code,
    deduct: (t) => t.deductDays,
    portion: (t) => PORTION_LABEL[t.portion],
    annual: (t) => DEDUCTION_MODES.indexOf(t.annualDeductionMode),
    blackout: (t) => t.allowedDuringBlackout,
    active: (t) => t.active,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["leaveTypes"] });

  const remove = useMutation({
    mutationFn: (id: number) => leaveTypeApi.remove(id),
    onSuccess: () => {
      toast({ title: "삭제되었습니다.", variant: "success" });
      invalidate();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setCreating(true)}>
          <Plus className="h-4 w-4" /> 휴가 종류 추가
        </Button>
      </div>
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <SortableTableHead sortKey="name" sort={sort} onSort={toggle}>이름</SortableTableHead>
                <SortableTableHead sortKey="code" sort={sort} onSort={toggle}>코드</SortableTableHead>
                <SortableTableHead sortKey="deduct" sort={sort} onSort={toggle}>차감</SortableTableHead>
                <SortableTableHead sortKey="portion" sort={sort} onSort={toggle}>단위</SortableTableHead>
                <SortableTableHead sortKey="annual" sort={sort} onSort={toggle}>연차 차감 방식</SortableTableHead>
                <SortableTableHead sortKey="blackout" sort={sort} onSort={toggle}>금지 기간 신청</SortableTableHead>
                <SortableTableHead sortKey="active" sort={sort} onSort={toggle}>상태</SortableTableHead>
                <TableHead className="text-right">관리</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((t) => (
                <TableRow key={t.id}>
                  <TableCell>
                    <span className="inline-flex items-center gap-2">
                      <span
                        className="inline-block h-3 w-3 rounded-full"
                        style={{ backgroundColor: t.colorHex }}
                      />
                      {t.name}
                    </span>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{t.code}</TableCell>
                  <TableCell>{formatDays(t.deductDays)}</TableCell>
                  <TableCell>{PORTION_LABEL[t.portion] ?? "-"}</TableCell>
                  <TableCell>{ANNUAL_DEDUCTION_LABEL[t.annualDeductionMode] ?? "-"}</TableCell>
                  <TableCell>{t.allowedDuringBlackout ? "가능" : "-"}</TableCell>
                  <TableCell>
                    <Badge variant={t.active ? "success" : "outline"}>
                      {t.active ? "사용" : "미사용"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="ghost" onClick={() => setEditing(t)}>
                        수정
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        onClick={async () => {
                          const ok = await confirm({
                            title: `'${t.name}' 휴가 종류를 삭제할까요?`,
                            confirmText: "삭제",
                            destructive: true,
                          });
                          if (ok) remove.mutate(t.id);
                        }}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      {(creating || editing) && (
        <LeaveTypeDialog
          type={editing}
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

function LeaveTypeDialog({
  type,
  onClose,
  onSaved,
}: {
  type: LeaveType | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const isEdit = !!type;
  const [form, setForm] = useState<LeaveTypeInput>({
    code: type?.code ?? "",
    name: type?.name ?? "",
    deductDays: type?.deductDays ?? 1,
    paid: type?.paid ?? true,
    portion: type?.portion ?? "FULL",
    annualDeductionMode: type?.annualDeductionMode ?? "DEDUCT",
    allowedDuringBlackout: type?.allowedDuringBlackout ?? false,
    colorHex: type?.colorHex ?? "#4f46e5",
    sortOrder: type?.sortOrder ?? 0,
    active: type?.active ?? true,
  });
  const set = <K extends keyof LeaveTypeInput>(k: K, v: LeaveTypeInput[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const save = useMutation({
    mutationFn: () => (isEdit && type ? leaveTypeApi.update(type.id, form) : leaveTypeApi.create(form)),
    onSuccess: () => {
      toast({ title: "저장되었습니다.", variant: "success" });
      onSaved();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  return (
    // 공용 창: 바깥 클릭·Esc 로 닫힘
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="text-base">{isEdit ? "휴가 종류 수정" : "휴가 종류 추가"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>이름</Label>
              <Input value={form.name} onChange={(e) => set("name", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>코드</Label>
              <Input
                value={form.code}
                disabled={isEdit}
                onChange={(e) => set("code", e.target.value.toUpperCase())}
              />
            </div>
            <div className="space-y-2">
              <Label>차감 일수</Label>
              <Input
                type="number"
                step="0.125"
                value={form.deductDays}
                onChange={(e) => set("deductDays", Number(e.target.value))}
              />
            </div>
            <div className="space-y-2">
              <Label>색상</Label>
              <Input type="color" value={form.colorHex} onChange={(e) => set("colorHex", e.target.value)} />
            </div>
          </div>
          <ToggleRow label="유급" checked={form.paid} onChange={(v) => set("paid", v)} />
          <div className="space-y-2">
            <Label>단위</Label>
            <Select value={form.portion} onValueChange={(v) => set("portion", v as LeavePortion)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(PORTION_LABEL) as LeavePortion[]).map((p) => (
                  <SelectItem key={p} value={p}>
                    {PORTION_LABEL[p]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>연차 차감 방식</Label>
            <div className="space-y-2" role="radiogroup" aria-label="연차 차감 방식">
              {DEDUCTION_MODES.map((m) => (
                <button
                  key={m}
                  type="button"
                  role="radio"
                  aria-checked={form.annualDeductionMode === m}
                  onClick={() => set("annualDeductionMode", m)}
                  className={`w-full rounded-md border p-3 text-left transition-colors ${
                    form.annualDeductionMode === m ? "border-primary bg-primary/5" : "hover:bg-muted/50"
                  }`}
                >
                  <p className="text-sm font-medium">{ANNUAL_DEDUCTION_LABEL[m]}</p>
                  <p className="text-xs text-muted-foreground">{DEDUCTION_MODE_DESC[m]}</p>
                </button>
              ))}
            </div>
          </div>
          <ToggleRow
            label="연차 사용 금지 기간에도 신청 가능"
            desc="켜면 금지 기간에도 신청할 수 있습니다. 예: 경조사 휴가·공가"
            checked={form.allowedDuringBlackout}
            onChange={(v) => set("allowedDuringBlackout", v)}
          />
          {isEdit && (
            <ToggleRow label="사용" checked={form.active ?? true} onChange={(v) => set("active", v)} />
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button
            onClick={async () => {
              const ok = await confirm({
                title: isEdit ? `'${form.name}' 휴가 종류를 저장할까요?` : `'${form.name}' 휴가 종류를 추가할까요?`,
                confirmText: isEdit ? "저장" : "추가",
              });
              if (ok) save.mutate();
            }}
            disabled={save.isPending || !form.name.trim() || (!isEdit && !form.code?.trim())}
          >
            저장
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RulesTab() {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const { data: awards = [] } = useQuery({ queryKey: ["awardRules"], queryFn: policyRulesApi.awards });
  const { data: specials = [] } = useQuery({ queryKey: ["specialRules"], queryFn: policyRulesApi.specials });

  const [aYears, setAYears] = useState(5);
  const [aDays, setADays] = useState(3);
  const [aName, setAName] = useState("");
  const [sName, setSName] = useState("");
  const [sDays, setSDays] = useState(1);
  /** 연간 사용 횟수: 비우면 제한 없음 */
  const [sLimit, setSLimit] = useState("");
  const [editingSpecial, setEditingSpecial] = useState<SpecialRule | null>(null);
  const sLimitValue = sLimit.trim() === "" ? null : Number(sLimit);
  const sLimitInvalid = sLimitValue != null && (!Number.isInteger(sLimitValue) || sLimitValue < 1);

  const err = (e: unknown) => toast({ title: extractErrorMessage(e), variant: "destructive" });
  const addAward = useMutation({
    mutationFn: () => policyRulesApi.createAward({ years: aYears, bonusDays: aDays, name: aName || null }),
    onSuccess: () => { setAName(""); qc.invalidateQueries({ queryKey: ["awardRules"] }); },
    onError: err,
  });
  const delAward = useMutation({
    mutationFn: (id: number) => policyRulesApi.removeAward(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["awardRules"] }),
    onError: err,
  });
  const addSpecial = useMutation({
    mutationFn: () =>
      policyRulesApi.createSpecial({
        name: sName,
        days: sDays,
        leaveTypeCode: "CONDOLENCE",
        sortOrder: 0,
        annualLimit: sLimitValue,
      }),
    onSuccess: () => { setSName(""); setSLimit(""); qc.invalidateQueries({ queryKey: ["specialRules"] }); },
    onError: err,
  });
  const delSpecial = useMutation({
    mutationFn: (id: number) => policyRulesApi.removeSpecial(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["specialRules"] }),
    onError: err,
  });

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader><CardTitle className="text-base">장기근속 포상휴가</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap items-end gap-2">
            <div className="space-y-1"><Label className="text-xs">근속(년)</Label>
              <Input type="number" min={1} className="w-20" value={aYears} onChange={(e) => setAYears(Number(e.target.value))} /></div>
            <div className="space-y-1"><Label className="text-xs">포상(일)</Label>
              <Input type="number" step="0.5" className="w-20" value={aDays} onChange={(e) => setADays(Number(e.target.value))} /></div>
            <div className="space-y-1 flex-1"><Label className="text-xs">명칭</Label>
              <Input value={aName} onChange={(e) => setAName(e.target.value)} placeholder="예: 5년 근속 포상" /></div>
            <Button
              size="sm"
              onClick={async () => {
                const ok = await confirm({
                  title: "포상휴가 규칙을 추가할까요?",
                  description: `근속 ${aYears}년 도달 시 ${aDays}일을 부여하는 규칙을 추가합니다.`,
                  confirmText: "추가",
                });
                if (ok) addAward.mutate();
              }}
            ><Plus className="h-4 w-4" /> 추가</Button>
          </div>
          <RuleTable rows={awards.map((a) => ({ id: a.id, cells: [`${a.years}년`, `${a.bonusDays}일`, a.name ?? "-"], values: [a.years, a.bonusDays, a.name] }))}
            headers={["근속", "포상", "명칭"]} onDelete={async (id) => {
              const ok = await confirm({ title: "포상휴가 규칙을 삭제할까요?", confirmText: "삭제", destructive: true });
              if (ok) delAward.mutate(id);
            }} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">경조사 규정</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap items-end gap-2">
            <div className="space-y-1 flex-1"><Label className="text-xs">사유/관계</Label>
              <Input value={sName} onChange={(e) => setSName(e.target.value)} placeholder="예: 본인 결혼" /></div>
            <div className="space-y-1"><Label className="text-xs">일수</Label>
              <Input type="number" step="0.5" className="w-20" value={sDays} onChange={(e) => setSDays(Number(e.target.value))} /></div>
            <div className="space-y-1"><Label className="text-xs">연간 횟수</Label>
              <Input type="number" min={1} className="w-28" value={sLimit} placeholder="제한 없음"
                onChange={(e) => setSLimit(e.target.value)} /></div>
            <Button
              size="sm"
              onClick={async () => {
                const ok = await confirm({
                  title: `'${sName}' 경조사 규정을 추가할까요?`,
                  description: `${sDays}일이 부여되는 규정으로 추가합니다.` +
                    (sLimitValue != null ? ` 1년(1~12월)에 ${sLimitValue}회까지 쓸 수 있습니다.` : ""),
                  confirmText: "추가",
                });
                if (ok) addSpecial.mutate();
              }}
              disabled={!sName.trim() || sLimitInvalid}
            ><Plus className="h-4 w-4" /> 추가</Button>
          </div>
          {sLimitInvalid && <p className="text-xs text-destructive">연간 횟수는 1 이상의 정수로 입력해 주세요.</p>}
          <RuleTable
            rows={specials.map((s) => ({
              id: s.id,
              cells: [s.name, `${s.days}일`, s.annualLimit != null ? `${s.annualLimit}회` : "제한 없음"],
              values: [s.name, s.days, s.annualLimit ?? Number.MAX_SAFE_INTEGER],
            }))}
            headers={["사유/관계", "일수", "연간 횟수"]}
            onEdit={(id) => setEditingSpecial(specials.find((s) => s.id === id) ?? null)}
            onDelete={async (id) => {
              const ok = await confirm({ title: "경조사 규정을 삭제할까요?", confirmText: "삭제", destructive: true });
              if (ok) delSpecial.mutate(id);
            }} />
        </CardContent>
      </Card>
      {editingSpecial && <SpecialRuleEditDialog rule={editingSpecial} onClose={() => setEditingSpecial(null)} />}
    </div>
  );
}

function BlackoutTab() {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const { data: rows = [] } = useQuery({ queryKey: ["blackouts"], queryFn: policyRulesApi.blackouts });
  const today = new Date().toISOString().slice(0, 10);
  const [start, setStart] = useState(today);
  const [end, setEnd] = useState(today);
  const [name, setName] = useState("");
  /** 추가·수정 확인 창: 저장 전에 겹치는 휴가와 처리 결과를 보여 준다 */
  const [saving, setSaving] = useState<{ editing?: Blackout; initial?: Omit<Blackout, "id"> } | null>(null);
  const err = (e: unknown) => toast({ title: extractErrorMessage(e), variant: "destructive" });

  const del = useMutation({
    mutationFn: (id: number) => policyRulesApi.removeBlackout(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["blackouts"] }),
    onError: err,
  });

  return (
    <Card className="max-w-3xl">
      <CardHeader><CardTitle className="text-base">연차 사용 금지 기간 (블랙아웃)</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1"><Label className="text-xs">시작</Label>
            <Input type="date" value={start} onChange={(e) => setStart(e.target.value)} /></div>
          <div className="space-y-1"><Label className="text-xs">종료</Label>
            <Input type="date" value={end} onChange={(e) => setEnd(e.target.value)} /></div>
          <div className="space-y-1 flex-1"><Label className="text-xs">명칭</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="예: 연말 결산 기간" /></div>
          <Button
            size="sm"
            onClick={() => setSaving({ initial: { startDate: start, endDate: end, name: name.trim() } })}
            disabled={!name.trim()}
          ><Plus className="h-4 w-4" /> 추가</Button>
        </div>
        <p className="text-xs text-muted-foreground">
          추가하거나 기간을 늘리면 겹치는 기존 휴가를 정책(연차 정책 탭 › 사용 통제)대로 처리합니다. 저장 전에 명단을 보여
          드립니다.
        </p>
        <RuleTable rows={rows.map((b) => ({ id: b.id, cells: [`${b.startDate} ~ ${b.endDate}`, b.name], values: [`${b.startDate} ${b.endDate}`, b.name] }))}
          headers={["기간", "명칭"]}
          onEdit={(id) => {
            const editing = rows.find((b) => b.id === id);
            if (editing) setSaving({ editing });
          }}
          onDelete={async (id) => {
            const ok = await confirm({ title: "사용 금지 기간을 삭제할까요?", confirmText: "삭제", destructive: true });
            if (ok) del.mutate(id);
          }} />
      </CardContent>
      {saving && (
        <BlackoutSaveDialog
          editing={saving.editing}
          initial={saving.initial}
          onClose={() => {
            if (saving.initial) setName("");
            setSaving(null);
          }}
        />
      )}
    </Card>
  );
}

function RuleTable({
  headers,
  rows,
  onEdit,
  onDelete,
}: {
  headers: string[];
  /** values: 열마다 정렬에 쓸 값(숫자·날짜 등). 없으면 화면 글자로 정렬 */
  rows: { id: number; cells: string[]; values?: SortValue[] }[];
  /** 있으면 줄마다 수정 버튼 */
  onEdit?: (id: number) => void;
  onDelete: (id: number) => void;
}) {
  const accessors = Object.fromEntries(
    headers.map((_, i) => [String(i), (r: (typeof rows)[number]) => (r.values ?? r.cells)[i]]),
  );
  const { sorted, sort, toggle } = useTableSort(rows, accessors);
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {headers.map((h, i) => (
            <SortableTableHead key={h} sortKey={String(i)} sort={sort} onSort={toggle}>
              {h}
            </SortableTableHead>
          ))}
          <TableHead className="text-right">관리</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.length > 0 ? sorted.map((r) => (
          <TableRow key={r.id}>
            {r.cells.map((c, i) => <TableCell key={i}>{c}</TableCell>)}
            <TableCell className="text-right">
              {onEdit && (
                <Button size="icon" variant="ghost" aria-label="수정" onClick={() => onEdit(r.id)}>
                  <Pencil className="h-4 w-4" />
                </Button>
              )}
              <Button size="icon" variant="ghost" aria-label="삭제" onClick={() => onDelete(r.id)}>
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </TableCell>
          </TableRow>
        )) : (
          <TableRow><TableCell colSpan={headers.length + 1} className="py-6 text-center text-muted-foreground">등록된 항목이 없습니다.</TableCell></TableRow>
        )}
      </TableBody>
    </Table>
  );
}
