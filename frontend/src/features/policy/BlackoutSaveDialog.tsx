import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Loader2 } from "lucide-react";
import {
  BLACKOUT_CONFLICT_LABEL,
  policyRulesApi,
  type Blackout,
  type BlackoutImpact,
  type BlackoutImpactItem,
} from "@/api/policy";
import { extractErrorMessage } from "@/api/client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { LEAVE_STATUS_LABEL } from "@/types";

const ACTION_LABEL: Record<BlackoutImpactItem["action"], string> = {
  REJECT: "자동 반려",
  CANCEL: "자동 취소",
  KEEP: "유지",
};

/**
 * 연차 사용 금지 기간 추가·수정. 저장 전에 영향받는 휴가 명단과 건수를 보여 주고 확인을 받는다.
 * <ul>
 *   <li>추가: 금지 기간과 겹치는 휴가를 정책(승인된 휴가만 유지 / 모두 취소)대로 처리</li>
 *   <li>늘려 수정: 새로 늘어난 날짜와 겹치는 휴가만 처리. 원래 기간에 남은 휴가는 경고와 함께 명단만 보여 준다</li>
 * </ul>
 * 처리된 휴가마다 당사자에게 메일·알림이 간다(서버).
 *
 * @param editing 수정할 금지 기간(추가면 없음)
 * @param initial 추가할 때 입력한 값. 있으면 바로 확인 단계부터 보여 준다
 */
export default function BlackoutSaveDialog({
  editing,
  initial,
  onClose,
}: {
  editing?: Blackout;
  initial?: Omit<Blackout, "id">;
  onClose: () => void;
}) {
  const { toast } = useToast();
  const qc = useQueryClient();
  const [startDate, setStartDate] = useState(editing?.startDate ?? initial?.startDate ?? "");
  const [endDate, setEndDate] = useState(editing?.endDate ?? initial?.endDate ?? "");
  const [name, setName] = useState(editing?.name ?? initial?.name ?? "");
  const [impact, setImpact] = useState<BlackoutImpact | null>(null);
  const [checking, setChecking] = useState(false);

  const check = async (s = startDate, e = endDate) => {
    if (!s || !e || e < s) {
      toast({ title: "종료일이 시작일보다 빠릅니다.", variant: "destructive" });
      return;
    }
    setChecking(true);
    try {
      setImpact(await policyRulesApi.blackoutImpact(s, e, editing?.id));
    } catch (err) {
      toast({ title: extractErrorMessage(err), variant: "destructive" });
    } finally {
      setChecking(false);
    }
  };

  // 추가 화면에서 값을 넘겨받았으면 바로 영향을 확인한다(처음 한 번)
  useEffect(() => {
    if (initial) void check(initial.startDate, initial.endDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = useMutation({
    mutationFn: () => {
      const body = { startDate, endDate, name: name.trim() };
      return editing ? policyRulesApi.updateBlackout(editing.id, body) : policyRulesApi.createBlackout(body);
    },
    onSuccess: () => {
      const rejected = impact?.affected.filter((a) => a.action === "REJECT").length ?? 0;
      const cancelled = impact?.affected.filter((a) => a.action === "CANCEL").length ?? 0;
      toast({
        title: editing ? "사용 금지 기간을 고쳤습니다." : "사용 금지 기간을 추가했습니다.",
        description:
          rejected + cancelled > 0
            ? `자동 반려 ${rejected}건 · 자동 취소 ${cancelled}건. 당사자에게 메일과 알림을 보냈습니다.`
            : undefined,
        variant: "success",
      });
      for (const key of ["blackouts", "leaveList", "pendingApprovals", "calendarEvents", "calendarDay", "myBalance"]) {
        qc.invalidateQueries({ queryKey: [key] });
      }
      onClose();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const affectedCount = impact?.affected.length ?? 0;

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{editing ? "사용 금지 기간 수정" : "사용 금지 기간 추가"}</DialogTitle>
          <DialogDescription>
            이 기간에는 연차를 신청할 수 없습니다. 저장하면 겹치는 기존 휴가를 정책대로 처리하고, 처리된 휴가마다
            당사자에게 메일과 알림을 보냅니다.
          </DialogDescription>
        </DialogHeader>

        {!impact ? (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-3">
              <div className="space-y-1">
                <Label className="text-xs">시작</Label>
                <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">종료</Label>
                <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              </div>
              <div className="min-w-[200px] flex-1 space-y-1">
                <Label className="text-xs">명칭</Label>
                <Input value={name} onChange={(e) => setName(e.target.value)} />
              </div>
            </div>
            {checking && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> 겹치는 휴가를 확인하는 중…
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-4">
            <div className="rounded-md bg-muted px-3 py-2 text-sm">
              <b>{name}</b> · {startDate} ~ {endDate}
              <span className="ml-2 text-muted-foreground">정책: {BLACKOUT_CONFLICT_LABEL[impact.mode]}</span>
            </div>

            <section className="space-y-2">
              <h3 className="text-sm font-semibold">
                저장하면 처리되는 휴가 {affectedCount}건
                {affectedCount > 0 && (
                  <span className="ml-2 font-normal text-muted-foreground">
                    (자동 반려 {impact.affected.filter((a) => a.action === "REJECT").length}건 · 자동 취소{" "}
                    {impact.affected.filter((a) => a.action === "CANCEL").length}건)
                  </span>
                )}
              </h3>
              {affectedCount === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {editing && !impact.extended
                    ? "기간을 늘리지 않아 처리할 휴가가 없습니다."
                    : "금지 기간과 겹쳐 처리할 휴가가 없습니다."}
                </p>
              ) : (
                <ImpactTable items={impact.affected} />
              )}
              {affectedCount > 0 && (
                <p className="text-xs text-muted-foreground">
                  일부 날짜만 겹쳐도 신청 건 전체를 처리합니다. 사유는 "연차 사용 금지 기간 지정"이고, 겹치지 않는 날이 있으면
                  다시 신청하라는 안내가 메일에 들어갑니다.
                </p>
              )}
            </section>

            {impact.kept.length > 0 && (
              <section className="space-y-2">
                {impact.extended ? (
                  <p className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    이전 정책으로 유지된 휴가는 그대로 남아 있습니다. 취소가 필요하면 관리자가 직접 취소해 주세요.
                  </p>
                ) : (
                  <h3 className="text-sm font-semibold">그대로 유지되는 휴가 {impact.kept.length}건</h3>
                )}
                <ImpactTable items={impact.kept} />
              </section>
            )}
          </div>
        )}

        <DialogFooter>
          {impact && editing ? (
            <Button variant="outline" onClick={() => setImpact(null)}>
              뒤로
            </Button>
          ) : (
            <Button variant="outline" onClick={onClose}>
              취소
            </Button>
          )}
          {!impact ? (
            <Button onClick={() => check()} disabled={!name.trim() || checking}>
              다음
            </Button>
          ) : (
            <Button
              variant={affectedCount > 0 ? "destructive" : "default"}
              onClick={() => save.mutate()}
              disabled={save.isPending}
            >
              {affectedCount > 0 ? `저장하고 ${affectedCount}건 처리` : "저장"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ImpactTable({ items }: { items: BlackoutImpactItem[] }) {
  return (
    <div className="max-h-60 overflow-y-auto rounded-md border">
      <table className="w-full text-sm">
        <thead className="sticky top-0 bg-muted text-xs text-muted-foreground">
          <tr>
            <th className="px-3 py-1.5 text-left font-medium">이름</th>
            <th className="px-3 py-1.5 text-left font-medium">부서</th>
            <th className="px-3 py-1.5 text-left font-medium">종류</th>
            <th className="px-3 py-1.5 text-left font-medium">기간</th>
            <th className="px-3 py-1.5 text-left font-medium">상태</th>
            <th className="px-3 py-1.5 text-left font-medium">처리</th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {items.map((i) => (
            <tr key={i.requestId}>
              <td className="px-3 py-1.5">{i.employeeName}</td>
              <td className="px-3 py-1.5 text-muted-foreground">{i.departmentName ?? "-"}</td>
              <td className="px-3 py-1.5">{i.leaveTypeName}</td>
              <td className="px-3 py-1.5 whitespace-nowrap">
                {i.startDate === i.endDate ? i.startDate : `${i.startDate} ~ ${i.endDate}`}
              </td>
              <td className="px-3 py-1.5">{LEAVE_STATUS_LABEL[i.status]}</td>
              <td className="px-3 py-1.5">
                <Badge variant={i.action === "KEEP" ? "secondary" : "destructive"}>{ACTION_LABEL[i.action]}</Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
