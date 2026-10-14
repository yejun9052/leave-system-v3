import { useEffect, useRef, useState } from "react";
import { Minus, Plus } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/**
 * 실행 시각 고르기: 매시간·매일·매주·매월·매년 탭과 버튼 격자로 cron 6칸(초 분 시 일 월 요일)을 만든다.
 * 버튼으로 나타낼 수 없는 값(6시간마다의 "0/6" 은 시 목록으로 풀어 보여 주지만, 분이 여러 개인 값 등)은 "직접 입력" 탭으로 연다.
 * 설명·다음 실행·오류는 서버 미리보기가 맡는다(이 부품은 cron 을 만들기만 한다).
 */

export type CronMode = "hourly" | "daily" | "weekly" | "monthly" | "yearly" | "custom";

const MODES: { value: CronMode; label: string }[] = [
  { value: "hourly", label: "매시간" },
  { value: "daily", label: "매일" },
  { value: "weekly", label: "매주" },
  { value: "monthly", label: "매월" },
  { value: "yearly", label: "매년" },
  { value: "custom", label: "직접 입력" },
];

/** 요일 버튼 순서(일~토)와 cron 이름. 0=일 */
const DAY_CODES = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];
const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const ALL_MINUTES = Array.from({ length: 60 }, (_, i) => i);
const COMMON_MINUTES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55];
const DAYS_OF_MONTH = Array.from({ length: 31 }, (_, i) => i + 1);
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);

/** 버튼으로 고른 값. dom 은 날짜 목록 또는 말일("L") */
export interface CronParts {
  second: string;
  minute: number;
  hours: number[];
  days: number[];
  dom: number[] | "L";
  months: number[];
}

const DEFAULTS: CronParts = { second: "0", minute: 0, hours: [2], days: [1], dom: [1], months: [1] };

const isAny = (part: string) => part === "*" || part === "?";

/** "1,3", "1-5", "*&#47;6", "0/6" 를 숫자 목록으로(min~max). 못 읽으면 null. */
export function parseList(part: string, min: number, max: number): number[] | null {
  const out = new Set<number>();
  for (const token of part.split(",")) {
    const step = /^(\*|\d{1,2})\/(\d{1,2})$/.exec(token);
    const range = /^(\d{1,2})-(\d{1,2})$/.exec(token);
    if (step) {
      const from = step[1] === "*" ? min : Number(step[1]);
      const every = Number(step[2]);
      if (every < 1 || from < min || from > max) return null;
      for (let v = from; v <= max; v += every) out.add(v);
    } else if (range) {
      const [a, b] = [Number(range[1]), Number(range[2])];
      if (a < min || b > max || a > b) return null;
      for (let v = a; v <= b; v++) out.add(v);
    } else if (/^\d{1,2}$/.test(token)) {
      const v = Number(token);
      if (v < min || v > max) return null;
      out.add(v);
    } else {
      return null;
    }
  }
  return [...out].sort((x, y) => x - y);
}

/** 요일 칸 → 0(일)~6(토) 목록. 이름(MON)·숫자(0·7=일)·범위. 못 읽으면 null. */
function parseDays(part: string): number[] | null {
  const index = (t: string) => (/^[0-7]$/.test(t) ? Number(t) % 7 : DAY_CODES.indexOf(t));
  const out = new Set<number>();
  for (const token of part.toUpperCase().split(",")) {
    const [a, b, extra] = token.split("-");
    const from = index(a);
    const to = b === undefined ? from : index(b);
    if (extra !== undefined || from < 0 || to < 0) return null;
    // SAT-SUN 처럼 거꾸로 이어지면 한 바퀴 돈다
    for (let d = from; ; d = (d + 1) % 7) {
      out.add(d);
      if (d === to) break;
    }
  }
  return [...out].sort((x, y) => x - y);
}

/** 고른 요일 → 요일 칸. 3일 이상 이어지면 MON-FRI 처럼 범위로(다 고르면 SUN-SAT) */
function formatDays(days: number[]): string {
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

/** cron 6칸 → 탭과 고른 값. 버튼으로 나타낼 수 없으면 mode "custom"(값은 기본값) */
export function parseCron(cron: string): { mode: CronMode; parts: CronParts } {
  const custom = { mode: "custom" as const, parts: DEFAULTS };
  const f = cron.trim().split(/\s+/);
  if (f.length !== 6 || !/^\d{1,2}$/.test(f[0]) || !/^\d{1,2}$/.test(f[1]) || Number(f[1]) > 59) return custom;
  const [second, minute, hour, dom, month, dow] = f;
  const base = { ...DEFAULTS, second, minute: Number(minute) };

  if (hour === "*") {
    return isAny(dom) && isAny(month) && isAny(dow) ? { mode: "hourly", parts: base } : custom;
  }
  const hours = parseList(hour, 0, 23);
  if (!hours) return custom;
  const withHours = { ...base, hours };

  if (isAny(month)) {
    if (isAny(dom) && isAny(dow)) return { mode: "daily", parts: withHours };
    if (isAny(dom)) {
      const days = parseDays(dow);
      return days ? { mode: "weekly", parts: { ...withHours, days } } : custom;
    }
    if (isAny(dow)) {
      const days = dom === "L" ? "L" : parseList(dom, 1, 31);
      return days ? { mode: "monthly", parts: { ...withHours, dom: days } } : custom;
    }
    return custom;
  }
  const months = parseList(month, 1, 12);
  const days = dom === "L" ? "L" : isAny(dom) ? null : parseList(dom, 1, 31);
  return months && days && isAny(dow) ? { mode: "yearly", parts: { ...withHours, months, dom: days } } : custom;
}

/**
 * 탭과 고른 값 → cron 6칸. 다 골라도 * 로 줄이지 않고 그대로 적는다:
 * 매주에서 요일을 다 고른 값이 "매일"로 읽혀 탭이 바뀌지 않게(cron 은 탭을 다시 고를 때도 쓴다).
 */
export function buildCron(mode: Exclude<CronMode, "custom">, p: CronParts): string {
  const hours = p.hours.join(",");
  const dom = p.dom === "L" ? "L" : p.dom.join(",");
  const months = p.months.join(",");
  switch (mode) {
    case "hourly":
      return `${p.second} ${p.minute} * * * *`;
    case "daily":
      return `${p.second} ${p.minute} ${hours} * * *`;
    case "weekly":
      return `${p.second} ${p.minute} ${hours} * * ${formatDays(p.days)}`;
    case "monthly":
      return `${p.second} ${p.minute} ${hours} ${dom} * *`;
    case "yearly":
      return `${p.second} ${p.minute} ${hours} ${dom} ${months} *`;
  }
}

/** 하나를 켜고 끈다. 마지막 하나는 끄지 않는다(아무것도 고르지 않은 실행 시각은 없으므로) */
function toggle(list: number[], v: number): number[] {
  if (!list.includes(v)) return [...list, v].sort((x, y) => x - y);
  return list.length > 1 ? list.filter((x) => x !== v) : list;
}

function Cell({
  on,
  onClick,
  children,
  className,
  label,
}: {
  on: boolean;
  onClick: () => void;
  children: React.ReactNode;
  className?: string;
  label?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={on}
      aria-label={label}
      className={cn(
        "h-9 min-w-0 whitespace-nowrap rounded-md border px-1 text-xs tabular-nums transition-colors sm:min-w-12 sm:px-2 sm:text-sm",
        on ? "border-primary bg-primary font-semibold text-primary-foreground" : "bg-background hover:bg-accent",
        className,
      )}
    >
      {children}
    </button>
  );
}

function Section({ title, hint, action, children }: { title: string; hint?: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium">{title}</span>
        {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
        {action}
      </div>
      {children}
    </div>
  );
}

const pad = (n: number) => String(n).padStart(2, "0");

export default function CronScheduleBuilder({ value, onChange }: { value: string; onChange: (cron: string) => void }) {
  const parsed = parseCron(value);
  const [mode, setMode] = useState<CronMode>(parsed.mode);
  // 이 부품이 만든 값이면 탭을 그대로 두고, 밖에서 바뀐 값(최근 쓴 설정·다시 불러오기)이면 탭을 다시 고른다
  const emitted = useRef(value);
  useEffect(() => {
    if (value !== emitted.current) {
      emitted.current = value;
      setMode(parseCron(value).mode);
    }
  }, [value]);
  const [allMinutes, setAllMinutes] = useState(false);

  const emit = (cron: string) => {
    emitted.current = cron;
    onChange(cron);
  };
  const parts = parsed.parts;
  const update = (patch: Partial<CronParts>) => {
    if (mode !== "custom") emit(buildCron(mode, { ...parts, ...patch }));
  };
  const switchMode = (next: CronMode) => {
    setMode(next);
    // 직접 입력으로 가면 지금 값을 그대로 고치고, 버튼 탭으로 가면 읽을 수 있는 값은 이어 쓴다
    if (next !== "custom") emit(buildCron(next, parts));
  };

  // 고른 분이 자주 쓰는 값에 없으면(예: 07분) 전체를 보여 준다
  const showAll = allMinutes || !COMMON_MINUTES.includes(parts.minute);
  const minutes = showAll ? ALL_MINUTES : COMMON_MINUTES;
  const hoursGrid = (
    <Section title="시" hint="여러 개 고를 수 있음">
      <div className="grid w-full max-w-sm grid-cols-6 gap-1 sm:w-fit sm:max-w-none">
        {HOURS.map((h) => (
          <Cell key={h} on={parts.hours.includes(h)} onClick={() => update({ hours: toggle(parts.hours, h) })} label={`${h}시`}>
            {pad(h)}시
          </Cell>
        ))}
      </div>
    </Section>
  );
  const minutesGrid = (
    <Section
      title="분"
      hint={showAll ? "전체" : "자주 쓰는 값"}
      action={
        <button
          type="button"
          onClick={() => setAllMinutes(!showAll)}
          disabled={!COMMON_MINUTES.includes(parts.minute)}
          className="flex h-6 w-6 items-center justify-center rounded border hover:bg-accent disabled:opacity-40"
          aria-label={showAll ? "자주 쓰는 분만 보기" : "모든 분 보기"}
          title={showAll ? "자주 쓰는 분만 보기" : "모든 분 보기"}
        >
          {showAll ? <Minus className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
        </button>
      }
    >
      <div className={cn("grid w-full max-w-sm gap-1 sm:w-fit sm:max-w-none", showAll ? "grid-cols-6 sm:grid-cols-10" : "grid-cols-6")}>
        {minutes.map((m) => (
          <Cell key={m} on={parts.minute === m} onClick={() => update({ minute: m })} label={`${m}분`}>
            {pad(m)}분
          </Cell>
        ))}
      </div>
    </Section>
  );
  const domGrid = (
    <Section title="날짜" hint="여러 개 고를 수 있음">
      <div className="grid w-full max-w-sm grid-cols-7 gap-1 sm:w-fit sm:max-w-none">
        {DAYS_OF_MONTH.map((d) => (
          <Cell
            key={d}
            on={parts.dom !== "L" && parts.dom.includes(d)}
            onClick={() => update({ dom: parts.dom === "L" ? [d] : toggle(parts.dom, d) })}
            label={`${d}일`}
          >
            {d}일
          </Cell>
        ))}
        <Cell on={parts.dom === "L"} onClick={() => update({ dom: parts.dom === "L" ? [1] : "L" })} className="col-span-2">
          말일
        </Cell>
      </div>
    </Section>
  );

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-1 rounded-lg border bg-muted/40 p-1 sm:inline-grid sm:grid-cols-6" role="radiogroup" aria-label="실행 주기">
        {MODES.map((m) => (
          <button
            key={m.value}
            type="button"
            role="radio"
            aria-checked={mode === m.value}
            onClick={() => switchMode(m.value)}
            className={cn(
              "rounded-md px-3 py-1.5 text-sm transition-colors",
              mode === m.value ? "bg-primary font-semibold text-primary-foreground shadow-sm" : "hover:bg-background",
            )}
          >
            {m.label}
          </button>
        ))}
      </div>

      {mode === "custom" ? (
        <div className="space-y-2">
          <Input
            value={value}
            onChange={(e) => emit(e.target.value)}
            aria-label="cron 직접 입력"
            placeholder="0 0 2 * * *"
            className="font-mono"
            spellCheck={false}
          />
          <ul className="space-y-0.5 pl-4 text-xs text-muted-foreground [list-style:disc]">
            <li>
              6칸을 띄어 씁니다: 초 분 시 일 월 요일. 예: <code>0 0 2 * * *</code> 매일 02:00, <code>0 30 3 * * MON,THU</code> 매주
              월·목 03:30
            </li>
            <li>
              <code>*</code> 모든 값, 쉼표 여러 개(<code>2,14</code>), <code>-</code> 범위(<code>1-7</code>), <code>/</code> 간격(시{" "}
              <code>0/6</code> = 6시간마다), 일 <code>L</code> = 말일
            </li>
            <li>백업은 1시간보다 자주 실행할 수 없습니다.</li>
          </ul>
        </div>
      ) : (
        <div className="space-y-4">
          {mode === "weekly" && (
            <Section title="요일" hint="여러 개 고를 수 있음">
              {/* 휴대폰에서도 한 줄에 7개 */}
              <div className="grid max-w-xs grid-cols-7 gap-1 sm:flex sm:max-w-none">
                {DAY_LABELS.map((label, d) => (
                  <Cell
                    key={label}
                    on={parts.days.includes(d)}
                    onClick={() => update({ days: toggle(parts.days, d) })}
                    className={cn(
                      "min-w-0 px-0 sm:min-w-11",
                      !parts.days.includes(d) && d === 0 && "text-red-600",
                      !parts.days.includes(d) && d === 6 && "text-blue-600",
                    )}
                  >
                    {label}
                  </Cell>
                ))}
              </div>
            </Section>
          )}
          {mode === "yearly" && (
            <Section title="달" hint="여러 개 고를 수 있음 (예: 1·4·7·10월 = 3개월마다)">
              <div className="grid w-full max-w-sm grid-cols-6 gap-1 sm:w-fit sm:max-w-none">
                {MONTHS.map((m) => (
                  <Cell key={m} on={parts.months.includes(m)} onClick={() => update({ months: toggle(parts.months, m) })}>
                    {m}월
                  </Cell>
                ))}
              </div>
            </Section>
          )}
          {(mode === "monthly" || mode === "yearly") && domGrid}
          {mode === "hourly" ? (
            minutesGrid
          ) : (
            <div className="flex flex-wrap items-start gap-x-8 gap-y-4">
              {hoursGrid}
              {minutesGrid}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
