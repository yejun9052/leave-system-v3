import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CalendarClock, CircleAlert, Save } from "lucide-react";
import { backupApi, type BackupSettings, type BackupSettingsForm } from "@/api/backups";
import { extractErrorMessage } from "@/api/client";
import QueryError from "@/components/QueryError";
import { formatDateTime } from "@/lib/dateFormat";
import { cn } from "@/lib/utils";
import { useServerForm } from "@/lib/useServerForm";
import { useUnsavedChanges } from "@/lib/useUnsavedChanges";
import ChangedElsewhereNotice from "@/components/ChangedElsewhereNotice";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import CronScheduleBuilder from "./CronScheduleBuilder";

/** 최근 쓴 실행 시각(저장한 것, 최신이 앞). 이 브라우저에만 남는다 */
const RECENT_KEY = "annualLeave.recentBackupSchedules";
const RECENT_MAX = 5;
interface RecentSchedule {
  cron: string;
  /** 저장할 때 서버가 돌려준 설명(예: "매월 1일 04:00") */
  label: string;
}

function readRecent(): RecentSchedule[] {
  try {
    const list = JSON.parse(localStorage.getItem(RECENT_KEY) ?? "[]");
    return Array.isArray(list)
      ? list.filter((r) => typeof r?.cron === "string" && typeof r?.label === "string").slice(0, RECENT_MAX)
      : [];
  } catch {
    return [];
  }
}

/** 저장한 설정을 맨 앞에 넣는다(이미 있으면 앞으로 옮김, 5개 넘으면 오래된 것부터 뺌) */
function pushRecent(list: RecentSchedule[], item: RecentSchedule): RecentSchedule[] {
  const next = [item, ...list.filter((r) => r.cron !== item.cron)].slice(0, RECENT_MAX);
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  } catch {
    // 저장소를 쓸 수 없는 환경(사생활 보호 모드 등)이면 이번 화면에서만 기억한다
  }
  return next;
}

const normalize = (cron: string) => cron.trim().split(/\s+/).join(" ");

/** 보관 기간 칸 값이 1~24 개월(정수)인지 */
function isValidKeepMonths(text: string): boolean {
  const months = Number(text);
  return text.trim() !== "" && Number.isInteger(months) && months >= 1 && months <= 24;
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
 * 정책 → 백업 → 자동 백업 설정. 실행 시각은 매시간·매일·매주·매월·매년 탭의 버튼 격자나 직접 입력으로 고치고
 * (CronScheduleBuilder), 저장은 cron 6칸(초 분 시 일 월 요일)으로 한다.
 * 고치는 동안 서버가 해석한 설명·다음 실행 시각·겹침 경고를 바로 보여 주고, 저장하면 재시작 없이 새 시각으로 예약된다.
 */
export default function BackupSettingsCard() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const { data, error, refetch } = useQuery({ queryKey: ["backupSettings"], queryFn: backupApi.settings });
  const { form, setForm, changedElsewhere, reload, reset } = useServerForm(data ? toForm(data) : undefined);
  const [recent, setRecent] = useState<RecentSchedule[]>(readRecent);
  // 보관 기간 칸: 비우고 다시 쓰는 중에는 글자로 두고(바로 1 이 들어가 "16"이 되지 않게), 1~24 일 때만 값에 넣는다
  const [keepText, setKeepText] = useState<string | null>(null);

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
    onSuccess: (saved, sent) => {
      reset(toForm(saved), sent);
      setRecent((list) => pushRecent(list, { cron: saved.cron, label: saved.scheduleLabel }));
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

  const dirty = !!data && !!form
    && (form.enabled !== data.enabled || form.keepMonths !== data.keepMonths || cron !== data.cron);
  // 화면을 나갈 때 "저장"을 고르면 부른다. 저장 버튼과 같은 조건(아래에서 계산)이라 렌더마다 바꿔 끼운다
  const saveOnLeave = useRef<() => Promise<boolean>>(() => Promise.resolve(false));
  useUnsavedChanges("자동 백업 설정", dirty, () => saveOnLeave.current());

  if (!data || !form) {
    if (error) return <QueryError error={error} onRetry={() => void refetch()} />;
    return <p className="text-sm text-muted-foreground">불러오는 중…</p>;
  }

  const set = <K extends keyof BackupSettingsForm>(key: K, value: BackupSettingsForm[K]) =>
    setForm((f) => (f ? { ...f, [key]: value } : f));
  // 아직 저장한 적이 없으면 지금 저장된 설정 하나라도 보여 준다
  const recentShown = recent.length > 0 ? recent : [{ cron: data.cron, label: data.scheduleLabel }];

  // 미리보기가 지금 입력과 같은 값을 해석했을 때만 믿는다
  const current = preview.data && preview.data.cron === cron ? preview.data : null;
  const cronError = cron === "" ? "실행 시각을 입력해 주세요." : current?.error ?? null;
  // 미리보기 요청 자체가 실패(서버 오류·네트워크)하면 "확인하는 중…"에 머물지 않고 알린다. 저장할 때 서버가 다시 확인한다
  const previewFailed = cron !== "" && !current && debouncedCron === cron && preview.isError;
  const checking = cron !== "" && !current && !previewFailed;
  // 실행 시각을 바꾸지 않았으면 미리보기와 상관없이 켜기·끄기·보관 기간만 저장할 수 있다
  const cronChanged = cron !== data.cron;
  const blockedByCron = cronChanged && (!!cronError || checking);
  const keepInvalid = keepText !== null && !isValidKeepMonths(keepText);
  saveOnLeave.current = async () => {
    if (blockedByCron || keepInvalid) {
      toast({
        title: keepInvalid ? "자동 백업 설정: 보관 기간을 확인해 주세요." : "자동 백업 설정: 실행 시각을 확인해 주세요.",
        variant: "destructive",
      });
      return false;
    }
    return save.mutateAsync(form).then(() => true, () => false);
  };

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
        {changedElsewhere && <ChangedElsewhereNotice onReload={reload} />}
        <div className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <Label>실행 시각</Label>
            <span className="text-xs text-muted-foreground">서버 시간(한국)</span>
          </div>

          <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="최근 쓴 설정">
            <span className="mr-1 text-xs text-muted-foreground">최근 쓴 설정</span>
            {recentShown.map((p) => (
              <button
                key={p.cron}
                type="button"
                onClick={() => set("cron", p.cron)}
                title={p.cron}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs transition-colors",
                  cron === p.cron ? "border-primary bg-primary text-primary-foreground" : "hover:bg-accent",
                )}
              >
                {p.label}
              </button>
            ))}
          </div>

          <CronScheduleBuilder value={form.cron} onChange={(c) => set("cron", c)} />

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
                <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <b>{current.scheduleLabel}</b>
                  <code className="rounded bg-background px-1.5 py-0.5 font-mono text-xs text-muted-foreground">{cron}</code>
                  {!form.enabled && <span className="text-muted-foreground">(꺼져 있어 실행하지 않음)</span>}
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
            ) : previewFailed ? (
              <p className="flex flex-wrap items-center gap-2 text-muted-foreground">
                <CircleAlert className="h-4 w-4 shrink-0" /> 실행 시각을 미리 확인하지 못했습니다. 저장할 때 다시 확인합니다.
                <Button type="button" variant="outline" size="sm" className="h-7" onClick={() => void preview.refetch()}>
                  다시 확인
                </Button>
              </p>
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

        </div>

        <div className="space-y-2">
          <Label htmlFor="keep-months">보관 기간 (자동 백업)</Label>
          <div className="flex items-center gap-1.5">
            <span className="text-sm">최대</span>
            <Input
              id="keep-months"
              type="number"
              min={1}
              max={24}
              className="w-20"
              value={keepText ?? form.keepMonths}
              aria-invalid={keepInvalid}
              onChange={(e) => {
                setKeepText(e.target.value);
                if (isValidKeepMonths(e.target.value)) set("keepMonths", Number(e.target.value));
              }}
              onBlur={() => setKeepText(null)}
            />
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
          <Button onClick={() => save.mutate(form)} disabled={!dirty || blockedByCron || keepInvalid || save.isPending}>
            <Save className="h-4 w-4" /> 저장
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
