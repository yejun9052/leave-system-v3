import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CalendarClock, CircleAlert, Save } from "lucide-react";
import { backupApi, type BackupSettings, type BackupSettingsForm } from "@/api/backups";
import { extractErrorMessage } from "@/api/client";
import { formatDateTime } from "@/lib/dateFormat";
import { cn } from "@/lib/utils";
import { useServerForm } from "@/lib/useServerForm";
import ChangedElsewhereNotice from "@/components/ChangedElsewhereNotice";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";

/**
 * 화면에 보이는 숫자 칸(월 → 일 → 시 → 분 순). index 는 cron 6칸(초 분 시 일 월 요일) 중 위치.
 * 비우면 매월·매일처럼 모든 값(*). 초는 화면에 두지 않고 저장된 값을 그대로 둔다.
 * 월 칸만 간격이다: 3 → 3개월마다(1·4·7·10월, cron *&#47;3). 특정 달 하나만 고르는 칸이 아니다.
 */
const NUMBER_FIELDS = [
  { index: 4, name: "개월마다", hint: "1~12 · 비우면 매월", every: "매월", unit: "개월" },
  { index: 3, name: "일", hint: "1~31", every: "매일", unit: "일" },
  { index: 2, name: "시", hint: "0~23", every: "매시", unit: "시간" },
  { index: 1, name: "분", hint: "0~59", every: "매분", unit: "분" },
] as const;

/** 요일 버튼 순서(월~일)와 cron 이름 */
const DAY_CODES = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const DAY_LABELS = ["월", "화", "수", "목", "금", "토", "일"];

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

/** 숫자 칸에 보일 값: 숫자·쉼표·범위만 그대로 보이고, *·L·0/6 같은 값은 빈 칸(설명은 placeholder 로). */
const numberValue = (part: string) => (/^[\d,-]+$/.test(part) ? part : "");

/** 월 칸(간격)에 보일 값: 1월부터 N개월 간격(*&#47;N, 1/N)이면 N, 매월(*)이나 특정 달(1,7)이면 빈 칸 */
const monthEveryValue = (part: string) => /^(?:\*|1)\/(\d{1,2})$/.exec(part)?.[1] ?? "";

/** 월 칸 입력 → cron: 비우면 매월(*), N 이면 1월부터 N개월마다(*&#47;N). 12 넘으면 12 */
function monthEveryCron(input: string): string {
  const digits = input.replace(/\D/g, "");
  if (digits === "") return "*";
  return `*/${Math.min(12, Number(digits))}`;
}

/** 월 칸이 비었을 때 회색 글씨: 매월, 또는 예전에 직접 입력한 특정 달("1·7월만") */
function monthPlaceholder(part: string): string {
  if (part === "" || part === "*" || part === "?") return "매월";
  if (/^(?:\*|1)\/\d{1,2}$/.test(part)) return "매월";
  return `${part.replace(/,/g, "·")}월만`;
}

/** 빈 칸일 때 회색 글씨: "매월", "말일", "6시간마다" 등. 숫자로 못 읽는 값은 cron 그대로. */
function numberPlaceholder(part: string, field: (typeof NUMBER_FIELDS)[number]): string {
  if (part === "" || part === "*" || part === "?") return field.every;
  if (part === "L") return "말일";
  const step = /^(?:\*|\d+)\/(\d+)$/.exec(part);
  return step ? `${step[1]}${field.unit}마다` : part;
}

/** cron 요일 칸 하나(MON, 1, 0·7=일) → 0(월)~6(일). 못 읽으면 -1. */
function dayIndex(token: string): number {
  if (/^[0-7]$/.test(token)) return (Number(token) + 6) % 7;
  return DAY_CODES.indexOf(token);
}

/** 요일 칸 → 고른 요일(0=월). 모든 요일(*)이면 빈 집합, 버튼으로 나타낼 수 없는 값이면 null. */
function parseDays(part: string): Set<number> | null {
  if (part === "" || part === "*" || part === "?") return new Set();
  const days = new Set<number>();
  for (const token of part.toUpperCase().split(",")) {
    const [a, b, extra] = token.split("-");
    const from = dayIndex(a);
    const to = b === undefined ? from : dayIndex(b);
    if (extra !== undefined || from < 0 || to < from) return null;
    for (let d = from; d <= to; d++) days.add(d);
  }
  return days;
}

/** 고른 요일 → 요일 칸. 아무것도 안 고르거나 다 고르면 *, 3일 이상 이어지면 MON-FRI 처럼 범위로. */
function formatDays(days: Set<number>): string {
  if (days.size === 0 || days.size === 7) return "*";
  const sorted = [...days].sort((x, y) => x - y);
  const parts: string[] = [];
  for (let i = 0; i < sorted.length; ) {
    let j = i;
    while (j + 1 < sorted.length && sorted[j + 1] === sorted[j] + 1) j++;
    if (j - i >= 2) parts.push(`${DAY_CODES[sorted[i]]}-${DAY_CODES[sorted[j]]}`);
    else for (let k = i; k <= j; k++) parts.push(DAY_CODES[sorted[k]]);
    i = j + 1;
  }
  return parts.join(",");
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
 * 정책 → 백업 → 자동 백업 설정. 실행 시각은 월·일·시·분 숫자 칸과 요일 버튼으로 고치고, 저장은 cron 6칸(초 분 시 일 월 요일)으로 한다.
 * 고치는 동안 서버가 해석한 설명·다음 실행 시각·겹침 경고를 바로 보여 주고, 저장하면 재시작 없이 새 시각으로 예약된다.
 */
export default function BackupSettingsCard() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["backupSettings"], queryFn: backupApi.settings });
  const { form, setForm, changedElsewhere, reload, reset } = useServerForm(data ? toForm(data) : undefined);

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
      reset(toForm(saved));
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
    // 빈 칸은 모든 값(*)으로, 화면에 없는 초는 비어 있을 때만 0으로 채운다
    const next = fields.map((p, k) => (p !== "" ? p : k === 0 ? "0" : "*"));
    next[i] = value === "" ? "*" : value;
    set("cron", next.join(" "));
  };
  // 월 칸은 간격: 비우면 매월, N 이면 N개월마다
  const setMonthEvery = (input: string) => setField(4, monthEveryCron(input) === "*" ? "" : monthEveryCron(input));
  const days = parseDays(fields[5]);
  const toggleDay = (d: number) => {
    const next = new Set(days ?? []);
    if (next.has(d)) next.delete(d);
    else next.add(d);
    setField(5, formatDays(next));
  };
  const lastDay = fields[3] === "L";
  // 일 칸 아래 이름: 그 달 며칠에 실행하는지("15일에 실행", "말일에 실행", "매일 실행")
  const dayPart = fields[3];
  const dayCaption =
    dayPart === "L"
      ? "말일에 실행"
      : /^[\d,-]+$/.test(dayPart)
        ? `${dayPart.replace(/,/g, "·").replace(/-/g, "~")}일에 실행`
        : dayPart === "" || dayPart === "*" || dayPart === "?"
          ? days && days.size > 0 ? "고른 요일에 실행" : "매일 실행"
          : `${numberPlaceholder(dayPart, NUMBER_FIELDS[1])} 실행`;

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
        {changedElsewhere && <ChangedElsewhereNotice onReload={reload} />}
        <div className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <Label>실행 시각</Label>
            <span className="text-xs text-muted-foreground">서버 시간(한국)</span>
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

          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4" role="group" aria-label="실행 날짜와 시각">
            {NUMBER_FIELDS.map((f) => {
              const part = fields[f.index];
              return (
                <div key={f.name} className="flex flex-col items-center gap-1 rounded-lg border bg-muted/30 p-2">
                  <label className="flex w-full flex-col items-center gap-1">
                    <Input
                      value={f.index === 4 ? monthEveryValue(part) : numberValue(part)}
                      placeholder={f.index === 4 ? monthPlaceholder(part) : numberPlaceholder(part, f)}
                      onChange={(e) =>
                        f.index === 4
                          ? setMonthEvery(e.target.value)
                          : setField(f.index, e.target.value.replace(/[^\d,-]/g, ""))
                      }
                      inputMode="numeric"
                      aria-label={f.name}
                      className="h-11 text-center text-lg font-semibold tabular-nums placeholder:text-sm placeholder:font-normal"
                      spellCheck={false}
                    />
                    <span className="text-sm font-medium">{f.index === 3 ? dayCaption : f.name}</span>
                  </label>
                  {f.index === 3 ? (
                    <button
                      type="button"
                      onClick={() => setField(3, lastDay ? "" : "L")}
                      aria-pressed={lastDay}
                      className={cn(
                        "rounded-full border px-2 text-[11px] leading-5 transition-colors",
                        lastDay ? "border-primary bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-accent",
                      )}
                    >
                      말일
                    </button>
                  ) : (
                    <span className="text-[11px] leading-5 text-muted-foreground">{f.hint}</span>
                  )}
                </div>
              );
            })}
          </div>

          <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="요일">
            <span className="mr-1 text-sm font-medium">요일</span>
            {DAY_LABELS.map((label, d) => {
              const on = days?.has(d) ?? false;
              return (
                <button
                  key={label}
                  type="button"
                  onClick={() => toggleDay(d)}
                  aria-pressed={on}
                  className={cn(
                    "h-8 w-8 rounded-full border text-sm transition-colors",
                    on ? "border-primary bg-primary text-primary-foreground" : "hover:bg-accent",
                  )}
                >
                  {label}
                </button>
              );
            })}
            <span className="ml-1 text-xs text-muted-foreground">
              {days === null ? `직접 입력한 요일: ${fields[5]}` : days.size === 0 ? "고르지 않으면 요일 상관없이" : ""}
            </span>
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
            <summary className="cursor-pointer select-none">입력하는 법 · cron 으로 직접 입력</summary>
            <ul className="mt-1.5 space-y-0.5 pl-4">
              <li>칸을 비우면 매월·매일처럼 모든 값입니다. 여러 개는 쉼표(예: 시 <code>2,14</code>), 범위는 <code>-</code>(예: 일 <code>1-7</code>).</li>
              <li>개월마다 칸은 간격입니다: <code>3</code> · 일 <code>1</code> = 1·4·7·10월 1일(3개월마다 한 번씩, 1년에 4번). 비우거나 <code>1</code>이면 매월.</li>
              <li>예: 월 비움 · 일 비움 · 시 <code>0</code> · 분 <code>10</code> = 매일 00:10 / 일 <code>1</code> · 시 <code>4</code> · 분 <code>0</code> = 매월 1일 04:00</li>
              <li>백업은 1시간보다 자주 실행할 수 없습니다.</li>
            </ul>
            <div className="mt-2 flex items-center gap-2">
              <span className="shrink-0">cron 6칸(초 분 시 일 월 요일)</span>
              <Input
                value={form.cron}
                onChange={(e) => set("cron", e.target.value)}
                aria-label="cron 직접 입력"
                className="h-8 font-mono text-xs"
                spellCheck={false}
              />
            </div>
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
