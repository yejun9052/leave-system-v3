import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CalendarClock, Save } from "lucide-react";
import {
  BACKUP_FREQUENCY_LABEL,
  WEEKDAY_LABEL,
  backupApi,
  type BackupFrequency,
  type BackupSettings,
  type BackupSettingsForm,
} from "@/api/backups";
import { extractErrorMessage } from "@/api/client";
import { formatDateTime } from "@/lib/dateFormat";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";

const FORM_KEYS: (keyof BackupSettingsForm)[] = [
  "enabled", "frequency", "dayOfWeek", "runTime", "intervalHours", "keepDaily", "keepWeekly", "keepMonthly",
];

function toForm(s: BackupSettings): BackupSettingsForm {
  return {
    enabled: s.enabled,
    frequency: s.frequency,
    dayOfWeek: s.dayOfWeek,
    runTime: s.runTime.slice(0, 5),
    intervalHours: s.intervalHours,
    keepDaily: s.keepDaily,
    keepWeekly: s.keepWeekly,
    keepMonthly: s.keepMonthly,
  };
}

/** 보관 개수 입력(정수, 범위 안). */
function KeepInput({ id, label, unit, max, value, onChange }: {
  id: string;
  label: string;
  unit: string;
  max: number;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id} className="text-xs">{label}</Label>
      <div className="flex items-center gap-1.5">
        <Input id={id} type="number" min={0} max={max} className="w-20" value={value}
          onChange={(e) => onChange(Math.max(0, Math.min(max, Math.trunc(Number(e.target.value) || 0))))} />
        <span className="text-sm text-muted-foreground">{unit}</span>
      </div>
    </div>
  );
}

/**
 * 정책 → 백업 → 자동 백업 설정. 저장하면 재시작 없이 바로 새 시각으로 예약된다(서버).
 * 다른 자동 작업과 같은 시각이면 경고만 보여 준다.
 */
export default function BackupSettingsCard() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["backupSettings"], queryFn: backupApi.settings });
  const [form, setForm] = useState<BackupSettingsForm | null>(null);

  useEffect(() => {
    if (data) setForm(toForm(data));
  }, [data]);

  const save = useMutation({
    mutationFn: (f: BackupSettingsForm) => backupApi.updateSettings(f),
    onSuccess: (saved) => {
      qc.setQueryData(["backupSettings"], saved);
      qc.invalidateQueries({ queryKey: ["automation"] });
      toast({
        title: "자동 백업 설정을 저장했습니다.",
        description: saved.nextRunAt ? `다음 실행: ${formatDateTime(saved.nextRunAt)}` : "자동 백업이 꺼져 있습니다.",
        variant: "success",
      });
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  if (!data || !form) return <p className="text-sm text-muted-foreground">불러오는 중…</p>;

  const saved = toForm(data);
  const dirty = FORM_KEYS.some((k) => form[k] !== saved[k]);
  const set = <K extends keyof BackupSettingsForm>(key: K, value: BackupSettingsForm[K]) =>
    setForm((f) => (f ? { ...f, [key]: value } : f));

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="flex items-center gap-2 text-base">
          <CalendarClock className="h-4 w-4" /> 자동 백업
        </CardTitle>
        <div className="flex items-center gap-2">
          <span className={cn("text-sm font-medium", form.enabled ? "text-primary" : "text-muted-foreground")}>
            {form.enabled ? "ON" : "OFF"}
          </span>
          <Switch checked={form.enabled} onCheckedChange={(v) => set("enabled", v)} aria-label="자동 백업 켜기" />
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label className="text-xs">주기</Label>
            <Select value={form.frequency} onValueChange={(v) => set("frequency", v as BackupFrequency)}>
              <SelectTrigger className="w-36" aria-label="주기"><SelectValue /></SelectTrigger>
              <SelectContent>
                {(Object.keys(BACKUP_FREQUENCY_LABEL) as BackupFrequency[]).map((f) => (
                  <SelectItem key={f} value={f}>{BACKUP_FREQUENCY_LABEL[f]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {form.frequency === "WEEKLY" && (
            <div className="space-y-1">
              <Label className="text-xs">요일</Label>
              <Select value={String(form.dayOfWeek)} onValueChange={(v) => set("dayOfWeek", Number(v))}>
                <SelectTrigger className="w-28" aria-label="요일"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                    <SelectItem key={d} value={String(d)}>{WEEKDAY_LABEL[d]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          {form.frequency === "HOURLY" ? (
            <div className="space-y-1">
              <Label htmlFor="backup-hours" className="text-xs">간격</Label>
              <div className="flex items-center gap-1.5">
                <Input id="backup-hours" type="number" min={1} max={24} className="w-20" value={form.intervalHours}
                  onChange={(e) => set("intervalHours", Math.max(1, Math.min(24, Math.trunc(Number(e.target.value) || 1))))} />
                <span className="text-sm text-muted-foreground">시간마다 (0시부터 정각)</span>
              </div>
            </div>
          ) : (
            <div className="space-y-1">
              <Label htmlFor="backup-time" className="text-xs">시각</Label>
              <Input id="backup-time" type="time" className="w-32" value={form.runTime}
                onChange={(e) => e.target.value && set("runTime", e.target.value)} />
            </div>
          )}
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium">보관 개수 (자동 백업만)</p>
          <div className="flex flex-wrap gap-5">
            <KeepInput id="keep-daily" label="일간" unit="일" max={60} value={form.keepDaily}
              onChange={(v) => set("keepDaily", v)} />
            <KeepInput id="keep-weekly" label="주간" unit="주" max={52} value={form.keepWeekly}
              onChange={(v) => set("keepWeekly", v)} />
            <KeepInput id="keep-monthly" label="월간" unit="개월" max={24} value={form.keepMonthly}
              onChange={(v) => set("keepMonthly", v)} />
          </div>
          <p className="text-xs text-muted-foreground">
            새 자동 백업이 성공한 뒤에만 정리합니다. 날짜·주(월~일)·월마다 마지막 백업을, 백업이 있는 최근 N개만큼
            남기고 셋 중 하나라도 해당하면 지우지 않습니다. 가장 최근 자동 백업은 항상 남깁니다. 수동·복원 전 백업은
            자동으로 지우지 않으니 필요 없으면 목록에서 직접 지워 주세요.
          </p>
        </div>

        <div className="rounded-md border bg-muted/40 px-3 py-2 text-sm">
          저장된 설정: <b>{data.enabled ? data.scheduleLabel : "꺼짐"}</b>
          {data.nextRunAt && (
            <span className="ml-2 text-muted-foreground">다음 실행 {formatDateTime(data.nextRunAt)}</span>
          )}
        </div>
        {data.warnings.map((w) => (
          <p key={w} className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {w}
          </p>
        ))}

        <div className="flex justify-end">
          <Button onClick={() => save.mutate(form)} disabled={!dirty || save.isPending}>
            <Save className="h-4 w-4" /> 저장
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
