import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bot, Save } from "lucide-react";
import { automationApi, type AutomationJob, type AutomationJobState } from "@/api/policy";
import { extractErrorMessage } from "@/api/client";
import { formatDateTime } from "@/lib/dateFormat";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";

const MONTHS = [6, 5, 4, 3, 2, 1];

const STATE_LABEL: Record<AutomationJobState, { text: string; variant: "secondary" | "success" | "outline" | "warning" }> = {
  ALWAYS: { text: "항상 실행", variant: "secondary" },
  ON: { text: "켜짐", variant: "success" },
  OFF: { text: "꺼짐", variant: "outline" },
  NOT_CONFIGURED: { text: "설정 없음", variant: "warning" },
};

/** 일이 생길 때(스케줄러가 아니라 처리하는 순간) 자동으로 가는 메일·알림 */
const EVENT_MAILS: { when: string; to: string }[] = [
  {
    when: "휴가 신청·철회·승인·반려, 취소 요청과 그 승인·반려, 인사관리자 강제 등록·강제 취소",
    to: "신청자, 결재할 팀장(없으면 인사관리자), 담당 팀장. 같은 휴가의 메일은 한 대화로 묶입니다",
  },
  { when: "연차 사용 금지 기간 추가·변경·삭제", to: "재직 중인 전 직원(숨은 참조로 한 통)" },
  { when: "회사·부서 일정 추가·변경·삭제", to: "회사 일정은 전 직원, 부서 일정은 그 부서 직원(개인 일정은 보내지 않음)" },
  { when: "병가·공가 승인으로 남은 연차 소멸", to: "본인" },
  { when: "계정 생성(임시 비밀번호), 비밀번호 재설정", to: "본인" },
];

/**
 * 정책 → 자동화: 연차 촉진 자동 발송 ON/OFF·발송 시기와 규칙, 자동 작업(스케줄러) 목록과 마지막 실행 결과,
 * 일이 생길 때 자동으로 가는 메일 안내.
 */
export default function AutomationTab() {
  const { data, isLoading } = useQuery({ queryKey: ["automation"], queryFn: automationApi.get });

  if (isLoading || !data) return <p className="text-sm text-muted-foreground">불러오는 중…</p>;

  return (
    <div className="space-y-6">
      <PromotionAutomation
        enabled={data.promotionEnabled}
        months={data.promotionMonths}
        preview={data.promotionPreview}
      />
      <JobList jobs={data.jobs} />
      <EventMails />
    </div>
  );
}

function PromotionAutomation({
  enabled: savedEnabled,
  months: savedMonths,
  preview,
}: {
  enabled: boolean;
  months: number[];
  preview: { employeeId: number; name: string; department: string | null; periodEnd: string; timeLeft: string; stageMonths: number }[];
}) {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const [enabled, setEnabled] = useState(savedEnabled);
  const [months, setMonths] = useState<number[]>(savedMonths);

  // 저장 뒤 서버 값으로 다시 맞춘다
  useEffect(() => {
    setEnabled(savedEnabled);
    setMonths(savedMonths);
  }, [savedEnabled, savedMonths]);

  const sortedMonths = [...months].sort((a, b) => b - a);
  const dirty = enabled !== savedEnabled || sortedMonths.join(",") !== savedMonths.join(",");

  const toggleMonth = (m: number) =>
    setMonths((prev) => (prev.includes(m) ? prev.filter((x) => x !== m) : [...prev, m]));

  const save = useMutation({
    mutationFn: () => automationApi.updatePromotion(enabled, sortedMonths),
    onSuccess: (r) => {
      qc.setQueryData(["automation"], r);
      qc.invalidateQueries({ queryKey: ["policy"] });
      toast({ title: r.promotionEnabled ? "자동 발송을 켰습니다." : "자동 발송 설정을 저장했습니다.", variant: "success" });
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const onSave = async () => {
    if (enabled && !savedEnabled) {
      const ok = await confirm({
        title: "연차 촉진 자동 발송을 켤까요?",
        description: "매일 09:00에 발송 시기에 들어온 직원에게 메일과 앱 알림이 자동으로 발송됩니다.",
        confirmText: "켜기",
      });
      if (!ok) return;
    }
    save.mutate();
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <Bot className="h-4 w-4 text-primary" /> 연차 촉진 자동 발송
            </CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">
              사용 기한이 다가오는 직원에게 남은 연차 안내를 정해진 시기에 자동으로 보냅니다.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className={cn("text-sm font-medium", enabled ? "text-primary" : "text-muted-foreground")}>
              {enabled ? "ON" : "OFF"}
            </span>
            <Switch checked={enabled} onCheckedChange={setEnabled} aria-label="자동 발송 켜기" />
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-2">
          <p className="text-sm font-medium">발송 시기 (사용 기한 기준)</p>
          <div className="flex flex-wrap gap-2">
            {MONTHS.map((m) => {
              const on = months.includes(m);
              return (
                <button
                  key={m}
                  type="button"
                  aria-pressed={on}
                  onClick={() => toggleMonth(m)}
                  className={cn(
                    "rounded-md border px-3 py-1.5 text-sm transition-colors",
                    on ? "border-primary bg-primary text-primary-foreground" : "bg-background hover:bg-accent",
                  )}
                >
                  {m}개월 전
                </button>
              );
            })}
          </div>
          <p className="text-xs text-muted-foreground">
            기본은 6개월 전·2개월 전(근로기준법의 1차·2차 촉진 시기)입니다. 하나 이상 골라야 합니다.
          </p>
        </div>

        <div className="rounded-md border bg-muted/40 p-4 text-sm">
          <p className="mb-2 font-medium">발송 규칙</p>
          <ul className="list-disc space-y-1 pl-5 text-muted-foreground">
            <li>
              매일 <b className="text-foreground">09:00</b>에 직원마다 사용 기한(입사 기념일 전날)을 확인해, 고른 시기에
              들어왔고 남은 연차가 있으면 안내 메일과 앱 알림을 보냅니다.
            </li>
            <li>
              <b className="text-foreground">같은 시기에 이미 보낸 직원은 건너뜁니다.</b> 관리자가 촉진 · 미사용 탭에서
              직접 보낸 것도 포함합니다. 예: 2개월 전 시기에 들어온 뒤 직접 보냈다면 그 시기 자동 발송은 생략합니다.
              다음 시기가 되면 다시 보냅니다.
            </li>
            <li>여러 시기에 한꺼번에 들어와 있으면(자동 발송을 늦게 켠 경우 등) 가장 가까운 시기로 한 번만 보냅니다.</li>
            <li>퇴사자·관리 전용 계정·남은 연차가 없는 직원은 보내지 않고, 메일 주소가 없으면 앱 알림만 보냅니다.</li>
            <li>보낸 기록은 촉진 · 미사용 탭의 "최근 발송"에 함께 나오며, 메일의 발송자는 "자동 발송"으로 표시됩니다.</li>
          </ul>
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium">
            지금 실행하면 보낼 대상 {preview.length}명
            <span className="ml-2 text-xs font-normal text-muted-foreground">
              (저장된 발송 시기 기준{savedEnabled ? "" : " · 자동 발송이 꺼져 있어 실제로는 보내지 않습니다"})
            </span>
          </p>
          {preview.length > 0 ? (
            <ul className="flex flex-wrap gap-2">
              {preview.map((p) => (
                <li key={p.employeeId} className="rounded-md border px-2.5 py-1 text-xs">
                  <span className="font-medium">{p.name}</span>
                  {p.department && <span className="text-muted-foreground"> · {p.department}</span>}
                  <span className="text-muted-foreground">
                    {" "}
                    · 기한 {p.periodEnd} ({p.timeLeft} 남음)
                  </span>
                  <Badge variant="secondary" className="ml-1.5">
                    {p.stageMonths}개월 전 안내
                  </Badge>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">지금 보낼 대상이 없습니다.</p>
          )}
        </div>

        <div className="flex justify-end">
          <Button onClick={onSave} disabled={!dirty || months.length === 0 || save.isPending}>
            <Save className="h-4 w-4" /> 저장
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function JobList({ jobs }: { jobs: AutomationJob[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">자동 작업 목록</CardTitle>
        <p className="text-sm text-muted-foreground">서버가 정해진 시각에 스스로 실행하는 작업과 마지막 실행 결과입니다.</p>
      </CardHeader>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>작업</TableHead>
              <TableHead className="w-[110px]">실행 시각</TableHead>
              <TableHead className="w-[100px]">상태</TableHead>
              <TableHead className="w-[320px]">마지막 실행</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {jobs.map((j) => (
              <TableRow key={j.key}>
                <TableCell>
                  <p className="font-medium">{j.name}</p>
                  <p className="text-xs text-muted-foreground">{j.description}</p>
                </TableCell>
                <TableCell className="whitespace-nowrap">{j.schedule}</TableCell>
                <TableCell>
                  <Badge variant={STATE_LABEL[j.state].variant}>{STATE_LABEL[j.state].text}</Badge>
                </TableCell>
                <TableCell className="text-sm">
                  {j.lastStartedAt ? (
                    <div className="space-y-1">
                      <p className="flex items-center gap-2 whitespace-nowrap">
                        <span className="text-muted-foreground">{formatDateTime(j.lastStartedAt)}</span>
                        <LastResult job={j} />
                      </p>
                      {j.lastMessage && <p className="text-xs text-muted-foreground">{j.lastMessage}</p>}
                    </div>
                  ) : (
                    <span className="text-muted-foreground">아직 실행 기록이 없습니다.</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function LastResult({ job }: { job: AutomationJob }) {
  if (job.lastFinishedAt == null) return <Badge variant="warning">실행 중</Badge>;
  if (job.lastSuccess === true) return <Badge variant="success">성공</Badge>;
  if (job.lastSuccess === false) return <Badge variant="destructive">실패</Badge>;
  return <Badge variant="outline">건너뜀</Badge>;
}

function EventMails() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">일이 생길 때 자동으로 가는 메일·알림</CardTitle>
        <p className="text-sm text-muted-foreground">
          정해진 시각이 아니라 처리하는 순간 자동으로 보내는 안내입니다. 메일 주소가 없으면 앱 알림만 갑니다.
        </p>
      </CardHeader>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>언제</TableHead>
              <TableHead>받는 사람</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {EVENT_MAILS.map((m) => (
              <TableRow key={m.when}>
                <TableCell className="font-medium">{m.when}</TableCell>
                <TableCell className="text-muted-foreground">{m.to}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
