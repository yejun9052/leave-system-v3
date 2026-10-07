import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { policyRulesApi, type SpecialRule } from "@/api/policy";
import { extractErrorMessage } from "@/api/client";
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

/** 경조사 규정 수정: 사유·일수·연간 사용 횟수. 연결된 휴가 종류와 정렬 순서는 그대로 둔다. */
export default function SpecialRuleEditDialog({ rule, onClose }: { rule: SpecialRule; onClose: () => void }) {
  const { toast } = useToast();
  const qc = useQueryClient();
  const [name, setName] = useState(rule.name);
  const [days, setDays] = useState(rule.days);
  const [limit, setLimit] = useState(rule.annualLimit != null ? String(rule.annualLimit) : "");

  const limitValue = limit.trim() === "" ? null : Number(limit);
  const limitInvalid = limitValue != null && (!Number.isInteger(limitValue) || limitValue < 1);

  const save = useMutation({
    mutationFn: () =>
      policyRulesApi.updateSpecial(rule.id, {
        name: name.trim(),
        days,
        leaveTypeCode: rule.leaveTypeCode,
        sortOrder: rule.sortOrder,
        annualLimit: limitValue,
      }),
    onSuccess: () => {
      toast({ title: "경조사 규정을 고쳤습니다.", variant: "success" });
      qc.invalidateQueries({ queryKey: ["specialRules"] });
      qc.invalidateQueries({ queryKey: ["leaveTypes"] });
      onClose();
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>경조사 규정 수정</DialogTitle>
          <DialogDescription>이미 신청된 휴가에는 영향이 없고, 앞으로의 신청부터 적용됩니다.</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>사유/관계</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>일수</Label>
            <Input type="number" step="0.5" className="w-28" value={days} onChange={(e) => setDays(Number(e.target.value))} />
          </div>
          <div className="space-y-2">
            <Label>연간 사용 횟수</Label>
            <Input
              type="number"
              min={1}
              className="w-28"
              value={limit}
              placeholder="제한 없음"
              onChange={(e) => setLimit(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              1년(1~12월, 휴가 시작일 기준)에 쓸 수 있는 횟수입니다. 비우면 제한이 없습니다. 결재 대기·승인·취소 요청 중인
              신청을 셉니다.
            </p>
            {limitInvalid && <p className="text-xs text-destructive">1 이상의 정수로 입력해 주세요.</p>}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button onClick={() => save.mutate()} disabled={!name.trim() || limitInvalid || save.isPending}>
            저장
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
