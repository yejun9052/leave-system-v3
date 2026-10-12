import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CalendarClock, CircleAlert, Save } from "lucide-react";
import { backupApi, type BackupSettings, type BackupSettingsForm } from "@/api/backups";
import { extractErrorMessage } from "@/api/client";
import { formatDateTime } from "@/lib/dateFormat";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";

/** cron 6칸: 이름과 쓸 수 있는 값 안내 */
const FIELDS = [
  { name: "초", hint: "0" },
  { name: "분", hint: "0-59" },
  { name: "시", hint: "0-23 · 0/6" },
  { name: "일", hint: "1-31 · L(말일)" },
  { name: "월", hint: "1-12" },
  { name: "요일", hint: "MON-SUN · 1-5" },
] as const;

/** 자주 쓰는 실행 시각 */
const PRESETS = [
  { label: "매일 02:00", cron: "0 0 2 * * *" },
  { label: "평일 22:00", cron: "0 0 22 * * MON-FRI" },
  { label: "매주 월요일 03:00", cron: "0 0 3 * * MON" },
  { label: "매월 1일 04:00", cron: "0 0 4 1 * *" },
  { label: "매월 말일 04:00", cron: "0 0 4 L * *" },
  { label: "6시간마다", cron: "0 0 0/6 * * *" },
];

const normalize = (cron: string) => cron.trim().split(/\s+/).join(" ");

/** 6칸으로 나눈다(모자라면 빈 칸, 넘치면 마지막 칸에 붙인다). */
function splitCron(cron: string): string[] {
  const parts = cron.trim() === "" ? [] : cron.trim().split(/\s+/);
  const head = parts.slice(0, 5);
  const rest = parts.slice(5).join(" ");
  return [...head, rest, "", "", "", "", "", ""].slice(0, 6);
}

function toForm(s: BackupSettings): BackupSettingsForm {
  return { enabled: s.enabled, cron: s.cron, keepMonths: s.keepMonths };
}

/** 입력이 멈춘 뒤 ms 만큼 지나면 값을 넘긴다. */
function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

/**
 * 정책 → 백업 → 자동 백업 설정. 실행 시각은 cron 6칸(초 분 시 일 월 요일)을 숫자로 직접 보고 고친다.
 * 고치는 동안 서버가 해석한 설명·다음 실행 시각·겹침 경고를 바로 보여 주고, 저장하면 재시작 없이 새 시각으로 예약된다.
 */
export default function BackupSettingsCard() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["backupSettings"], queryFn: backupApi.settings });
  const [form, setForm] = useState<BackupSettingsForm | null>(null);

  useEffect(() => {
    if (data) setForm(toForm(data));
  }, [data]);

  const cron = normalize(form?.cron ?? "");
  const debouncedCron = useDebounced(cron, 300);
  const preview = useQuery({
    queryKey: ["backupCronPreview", debouncedCron],
    queryFn: () => backupApi.previewSchedule(debouncedCron),
    enabled: !!form && debouncedCron !== "",
    placeholderData: (prev) => prev,
  });

  const save = useMutation({
    mutationFn: (f: BackupSettingsForm) => backupApi.updateSettings({ ...f, cron: normalize(f.cron) }),
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

  const set = <K extends keyof BackupSettingsForm>(key: K, value: BackupSettingsForm[K]) =>
    setForm((f) => (f ? { ...f, [key]: value } : f));
  const fields = splitCron(form.cron);
  const setField = (i: number, value: string) => {
    const next = [...fields];
    next[i] = value.replace(/\s+/g, "");
    set("cron", next.join(" "));
  };

  const dirty = form.enabled !== data.enabled || form.keepMonths !== data.keepMonths || cron !== data.cron;
  // 미리보기가 지금 입력과 같은 값을 해석했을 때만 믿는다
  const current = preview.data && preview.data.cron === cron ? preview.data : null;
  const cronError = cron === "" ? "실행 시각을 입력해 주세요." : current?.error ?? null;
  const checking = cron !== "" && !current;

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
        <div className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <Label>실행 시각</Label>
            <span className="text-xs text-muted-foreground">cron 6칸 · 서버 시간(한국)</span>
          </div>

          <div className="flex flex-wrap gap-1.5">
            {PRESETS.map((p) => (
              <button
                key={p.cron}
                type="button"
                onClick={() => set("cron", p.cron)}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs transition-colors",
                  cron === p.cron ? "border-primary bg-primary text-primary-foreground" : "hover:bg-accent",
                )}
              >
                {p.label}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-3 gap-2 sm:grid-cols-6" role="group" aria-label="cron 칸">
            {FIELDS.map((f, i) => (
              <label key={f.name} className="flex flex-col items-center gap-1 rounded-lg border bg-muted/30 p-2">
                <Input
                  value={fields[i]}
                  onChange={(e) => setField(i, e.target.value)}
                  aria-label={`cron ${f.name}`}
                  className="h-11 text-center font-mono text-lg font-semibold tabular-nums"
                  spellCheck={false}
                />
                <span className="text-sm font-medium">{f.name}</span>
                <span className="text-[11px] leading-tight text-muted-foreground">{f.hint}</span>
              </label>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <span className="shrink-0 text-xs text-muted-foreground">직접 입력</span>
            <Input
              value={form.cron}
              onChange={(e) => set("cron", e.target.value)}
              aria-label="cron 직접 입력"
              className="font-mono"
              spellCheck={false}
            />
          </div>

          <div
            className={cn(
              "space-y-2 rounded-md border px-3 py-2.5 text-sm",
              cronError ? "border-destructive/40 bg-destructive/5" : "bg-muted/40",
              checking && "opacity-70",
            )}
            aria-live="polite"
          >
            {cronError ? (
              <p className="flex items-start gap-2 text-destructive">
                <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" /> {cronError}
              </p>
            ) : current ? (
              <>
                <p>
                  <b>{current.scheduleLabel}</b>
                  {!form.enabled && <span className="ml-2 text-muted-foreground">(꺼져 있어 실행하지 않음)</span>}
                </p>
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-xs text-muted-foreground">다음 실행</span>
                  {current.nextRuns.map((r) => (
                    <span key={r} className="rounded bg-background px-1.5 py-0.5 font-mono text-xs tabular-nums">
                      {formatDateTime(r).slice(0, 16)}
                    </span>
                  ))}
                </div>
              </>
            ) : (
              <p className="text-muted-foreground">확인하는 중…</p>
            )}
          </div>
          {!cronError &&
            current?.warnings.map((w) => (
              <p key={w} className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {w}
              </p>
            ))}

          <details className="text-xs text-muted-foreground">
            <summary className="cursor-pointer select-none">cron 쓰는 법</summary>
            <ul className="mt-1.5 space-y-0.5 pl-4">
              <li><code>*</code> 모든 값 · <code>,</code> 여러 개(예: 요일 <code>MON,THU</code>) · <code>-</code> 범위(예: <code>MON-FRI</code>)</li>
              <li><code>/</code> 간격(예: 시 <code>0/6</code> = 0·6·12·18시) · <code>L</code> 말일(일 칸)</li>
              <li>예: <code>0 10 0 * * *</code> 매일 00:10 · <code>0 0 3 * * MON</code> 매주 월요일 03:00</li>
              <li>백업은 1시간보다 자주 실행할 수 없고, 초는 0처럼 숫자 하나로 둡니다.</li>
            </ul>
          </details>
        </div>

        <div className="space-y-2">
          <Label htmlFor="keep-months">보관 기간 (자동 백업)</Label>
          <div className="flex items-center gap-1.5">
            <span className="text-sm">최대</span>
            <Input id="keep-months" type="number" min={1} max={24} className="w-20" value={form.keepMonths}
              onChange={(e) => set("keepMonths", Math.max(1, Math.min(24, Math.trunc(Number(e.target.value) || 1))))} />
            <span className="text-sm">개월 동안 저장 (1~24)</span>
          </div>
          <p className="text-xs text-muted-foreground">
            이 기간 안의 자동 백업은 모두 남기고, 더 오래된 자동 백업은 새 자동 백업이 성공한 뒤 지웁니다. 가장 최근 자동
            백업은 항상 남깁니다. 수동·복원 전 백업은 자동으로 지우지 않으니 필요 없으면 목록에서 직접 지워 주세요.
          </p>
        </div>

        <div className="rounded-md border bg-muted/40 px-3 py-2 text-sm">
          저장된 설정: <b>{data.enabled ? data.scheduleLabel : "꺼짐"}</b>
          <span className="ml-2 font-mono text-xs text-muted-foreground">{data.cron}</span>
          {data.nextRunAt && (
            <span className="ml-2 text-muted-foreground">다음 실행 {formatDateTime(data.nextRunAt)}</span>
          )}
        </div>

        <div className="flex justify-end">
          <Button onClick={() => save.mutate(form)} disabled={!dirty || !!cronError || checking || save.isPending}>
            <Save className="h-4 w-4" /> 저장
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
