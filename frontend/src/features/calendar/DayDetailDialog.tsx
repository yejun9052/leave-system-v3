import { useQuery } from "@tanstack/react-query";
import { CalendarPlus, Loader2 } from "lucide-react";
import { calendarApi, type DayLeaveDto } from "@/api/calendar";
import { policyRulesApi } from "@/api/policy";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatKoreanDate } from "./useDateSelection";

const SCOPE_LABEL = { COMPANY: "전사", DEPARTMENT: "부서", PERSONAL: "개인" } as const;

/** 휴가 단위 표시: 종일 · 오전 반차 · 반반차 · 시간차 2시간 */
function portionLabel(l: DayLeaveDto): string {
  switch (l.portion) {
    case "FULL":
      return "종일";
    case "HALF":
      return l.leaveTypeName.includes("반차") ? l.leaveTypeName : "반차";
    case "QUARTER":
      return "반반차";
    case "HOURLY":
      return l.hours != null ? `시간차 ${l.hours}시간` : "시간차";
  }
}

function StatusBadge({ status }: { status: DayLeaveDto["status"] }) {
  switch (status) {
    case "APPROVED":
      return <Badge variant="success">승인</Badge>;
    case "CANCEL_REQUESTED":
      return <Badge variant="secondary">승인 · 취소 요청 중</Badge>;
    case "LEAD_APPROVED":
      return <Badge variant="warning">결재 대기(1차 승인)</Badge>;
    default:
      return <Badge variant="warning">결재 대기</Badge>;
  }
}

function shortPeriod(start: string, end: string): string {
  const md = (d: string) => `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`;
  return start === end ? md(start) : `${md(start)}~${md(end)}`;
}

/** 날짜 상세: 그날 휴가자(부서별)·등록 일정·공휴일. 휴가·일정이 몰린 날 확인용. */
export default function DayDetailDialog({
  date,
  canApply,
  onApply,
  onClose,
}: {
  date: string;
  canApply: boolean;
  onApply: (date: string) => void;
  onClose: () => void;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["calendarDay", date],
    queryFn: () => calendarApi.day(date),
  });
  // 그날 걸린 블랙아웃(연차 사용 제한) 기간: 캘린더와 같은 목록 캐시를 쓴다
  const { data: blackouts = [] } = useQuery({ queryKey: ["blackouts"], queryFn: policyRulesApi.blackouts });
  const dayBlackouts = blackouts.filter((b) => b.startDate <= date && b.endDate >= date);

  const leaves = data?.leaves ?? [];
  const events = data?.events ?? [];
  const peopleCount = new Set(leaves.map((l) => l.employeeId)).size;
  const byDepartment = new Map<string, DayLeaveDto[]>();
  for (const l of leaves) {
    const key = l.departmentName ?? "부서 없음";
    byDepartment.set(key, [...(byDepartment.get(key) ?? []), l]);
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{formatKoreanDate(date)}</DialogTitle>
          <DialogDescription className="flex flex-wrap items-center gap-2">
            <span>휴가 {peopleCount}명</span>
            <span>·</span>
            <span>일정 {events.length}건</span>
            {data?.holidayName && <Badge variant="destructive">{data.holidayName}</Badge>}
            {dayBlackouts.map((b) => (
              <Badge key={b.id} variant="destructive" title="이 기간에는 연차를 신청할 수 없습니다">
                연차 제한 · {b.name}
              </Badge>
            ))}
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <div className="flex justify-center py-8">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="space-y-5">
            <section className="space-y-3">
              <h3 className="text-sm font-semibold">휴가</h3>
              {leaves.length === 0 ? (
                <p className="text-sm text-muted-foreground">이날 휴가자가 없습니다.</p>
              ) : (
                [...byDepartment.entries()].map(([dept, items]) => (
                  <div key={dept} className="space-y-1">
                    <p className="text-xs font-medium text-muted-foreground">
                      {dept} · {items.length}건
                    </p>
                    <ul className="divide-y rounded-md border">
                      {items.map((l) => (
                        <li key={l.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                          <span
                            className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
                            style={{ backgroundColor: l.leaveTypeColor }}
                          />
                          <div className="min-w-0 flex-1">
                            <p className="font-medium">
                              {l.employeeName}
                              {l.mine && (
                                <Badge variant="default" className="ml-2 px-1.5 py-0">
                                  나
                                </Badge>
                              )}
                              <span className="ml-2 text-xs font-normal text-muted-foreground">
                                {l.departmentName ?? "-"}
                              </span>
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {l.portion === "FULL" ? `${l.leaveTypeName} · 종일` : portionLabel(l)}
                              {l.startDate !== l.endDate && ` (${shortPeriod(l.startDate, l.endDate)})`}
                            </p>
                          </div>
                          <StatusBadge status={l.status} />
                        </li>
                      ))}
                    </ul>
                  </div>
                ))
              )}
            </section>

            <section className="space-y-2">
              <h3 className="text-sm font-semibold">회사 일정</h3>
              {events.length === 0 ? (
                <p className="text-sm text-muted-foreground">등록된 일정이 없습니다.</p>
              ) : (
                <ul className="divide-y rounded-md border">
                  {events.map((e) => (
                    <li key={e.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                      <span
                        className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
                        style={{ backgroundColor: e.colorHex }}
                      />
                      <span className="min-w-0 flex-1 truncate">{e.title}</span>
                      <span className="text-xs text-muted-foreground">
                        {SCOPE_LABEL[e.scope]} · {shortPeriod(e.start, e.end)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            닫기
          </Button>
          {canApply && (
            <Button onClick={() => onApply(date)}>
              <CalendarPlus className="h-4 w-4" /> 이 날짜로 신청하기
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
