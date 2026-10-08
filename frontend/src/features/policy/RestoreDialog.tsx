import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, Loader2, ShieldCheck, ShieldQuestion } from "lucide-react";
import { BACKUP_KIND_LABEL, backupApi, type RestoreSource } from "@/api/backups";
import { extractErrorMessage } from "@/api/client";
import { formatDateTime } from "@/lib/dateFormat";
import { useAuthStore } from "@/store/auth";
import { useMaintenanceStore } from "@/store/maintenance";
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

const CONFIRM_WORD = "복원";

type Phase = { step: "confirm" } | { step: "running" } | { step: "done" } | { step: "failed"; message: string };

/**
 * 백업 복원 확인·진행 창(시스템 관리자). 서버에서 체크섬·파일·DB 버전을 확인한 결과와 경고를 보여 주고,
 * "복원"을 직접 입력해야 실행된다. 진행 중에는 닫을 수 없고, 끝나면 모든 세션이 지워지므로 로그인 화면으로 보낸다.
 */
export default function RestoreDialog({ fileName, source, onClose }: {
  fileName: string;
  source: RestoreSource;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const setSelfRestoring = useMaintenanceStore((s) => s.setSelfRestoring);
  const [typed, setTyped] = useState("");
  const [phase, setPhase] = useState<Phase>({ step: "confirm" });
  const check = useQuery({
    queryKey: ["restoreCheck", source, fileName],
    queryFn: () => backupApi.check(fileName, source),
    retry: false,
    gcTime: 0,
  });

  useEffect(() => () => setSelfRestoring(false), [setSelfRestoring]);

  const run = async () => {
    setPhase({ step: "running" });
    setSelfRestoring(true);
    try {
      await backupApi.restore(fileName, source);
      setPhase({ step: "done" });
      // 서버가 모든 세션을 지웠다 → 로그인 화면으로
      window.setTimeout(() => {
        useAuthStore.setState({ user: null });
        window.location.href = "/login";
      }, 2500);
    } catch (e) {
      setSelfRestoring(false);
      setPhase({ step: "failed", message: extractErrorMessage(e, "복원에 실패했습니다.") });
      qc.invalidateQueries({ queryKey: ["backups"] });
    }
  };

  const busy = phase.step === "running" || phase.step === "done";
  const c = check.data;
  const older = c && c.currentVersion && c.dbVersion !== c.currentVersion;

  return (
    <Dialog open onOpenChange={(open) => !open && !busy && onClose()}>
      <DialogContent
        className="max-w-lg"
        onEscapeKeyDown={(e) => busy && e.preventDefault()}
        onPointerDownOutside={(e) => busy && e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>백업으로 복원</DialogTitle>
          <DialogDescription className="break-all">{fileName}</DialogDescription>
        </DialogHeader>

        {phase.step === "running" && (
          <div className="space-y-3 py-4 text-center" role="status">
            <Loader2 className="mx-auto h-8 w-8 animate-spin text-primary" />
            <p className="font-medium">복원하는 중입니다. 창을 닫지 마세요.</p>
            <p className="text-sm text-muted-foreground">
              지금 상태를 먼저 백업한 뒤 복원합니다. 그동안 다른 사용자에게는 "시스템 점검 중" 안내가 보입니다.
            </p>
          </div>
        )}

        {phase.step === "done" && (
          <div className="space-y-3 py-4 text-center" role="status">
            <CheckCircle2 className="mx-auto h-8 w-8 text-emerald-600" />
            <p className="font-medium">복원했습니다.</p>
            <p className="text-sm text-muted-foreground">모든 로그인이 끝났습니다. 로그인 화면으로 이동합니다…</p>
          </div>
        )}

        {phase.step === "failed" && (
          <p role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {phase.message}
          </p>
        )}

        {phase.step === "confirm" && (
          <div className="space-y-4">
            {check.isLoading && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> 백업 파일을 확인하는 중…
              </p>
            )}
            {check.isError && (
              <p role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                {extractErrorMessage(check.error, "이 파일로는 복원할 수 없습니다.")}
              </p>
            )}
            {c && (
              <>
                <dl className="grid grid-cols-[6rem_1fr] gap-x-3 gap-y-1.5 rounded-md bg-muted px-3 py-2 text-sm">
                  <dt className="text-muted-foreground">백업 시점</dt>
                  <dd>{formatDateTime(c.createdAt)}</dd>
                  <dt className="text-muted-foreground">종류</dt>
                  <dd>{BACKUP_KIND_LABEL[c.kind]}</dd>
                  <dt className="text-muted-foreground">DB 버전</dt>
                  <dd>
                    {c.dbVersion}
                    {older && (
                      <span className="ml-1 text-muted-foreground">(지금 {c.currentVersion}, 복원한 뒤 지금 버전으로 올립니다)</span>
                    )}
                  </dd>
                  <dt className="text-muted-foreground">파일 검사</dt>
                  <dd className="flex items-center gap-1">
                    {c.checksumVerified ? (
                      <><ShieldCheck className="h-4 w-4 text-emerald-600" /> 체크섬 일치(손상·변조 없음)</>
                    ) : (
                      <><ShieldQuestion className="h-4 w-4 text-amber-600" /> 백업 정보 파일이 없어 체크섬은 확인하지 못함</>
                    )}
                  </dd>
                </dl>
                <div role="alert" className="space-y-1 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  <p className="flex items-center gap-2 font-semibold">
                    <AlertTriangle className="h-4 w-4 shrink-0" /> 이 시점 이후의 모든 변경 사항이 사라집니다.
                  </p>
                  <p>
                    복원하기 전에 지금 상태를 "복원 전" 백업으로 자동 저장하니 되돌릴 수 있습니다. 복원이 끝나면 모든
                    사용자(본인 포함)가 다시 로그인해야 합니다.
                  </p>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="restore-confirm">확인을 위해 <b>{CONFIRM_WORD}</b>을 입력하세요</Label>
                  <Input id="restore-confirm" value={typed} autoComplete="off" onChange={(e) => setTyped(e.target.value)} />
                </div>
              </>
            )}
          </div>
        )}

        <DialogFooter>
          {!busy && (
            <Button variant="outline" onClick={onClose}>
              {phase.step === "failed" ? "닫기" : "취소"}
            </Button>
          )}
          {phase.step === "confirm" && (
            <Button variant="destructive" disabled={!c || typed.trim() !== CONFIRM_WORD} onClick={run}>
              복원
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
