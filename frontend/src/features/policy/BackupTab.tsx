import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DatabaseBackup, Download, FolderInput, History, Loader2, Trash2 } from "lucide-react";
import { BACKUP_KIND_LABEL, backupApi, type BackupFile, type BackupKind, type RestoreSource } from "@/api/backups";
import { extractErrorMessage } from "@/api/client";
import { formatDateTime } from "@/lib/dateFormat";
import { useAuthStore } from "@/store/auth";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useConfirm } from "@/components/ui/confirm";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import BackupSettingsCard from "./BackupSettingsCard";
import RestoreDialog from "./RestoreDialog";

const KIND_BADGE: Record<BackupKind, "secondary" | "outline" | "warning" | "default"> = {
  MANUAL: "outline",
  AUTO: "secondary",
  PRE_RESTORE: "warning",
  IMPORTED: "default",
};

/** 바이트를 읽기 쉬운 크기로: 1.2 MB, 35.0 GB */
function formatBytes(bytes: number): string {
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return i === 0 ? `${value} B` : `${value.toFixed(1)} ${units[i]}`;
}

/**
 * 정책 → 백업: 자동 백업 설정, 지금 백업(DB 전체를 서버 백업 폴더에 파일로), 백업 목록과 import 폴더의 가져온 파일.
 * 내려받기·삭제·복원은 시스템 관리자만. 백업 실행·삭제·내려받기·복원은 이벤트 로그에 남는다.
 */
export default function BackupTab() {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const isSystemAdmin = useAuthStore((s) => s.hasAnyRole("SYSTEM_ADMIN"));
  const { data, isLoading, error } = useQuery({ queryKey: ["backups"], queryFn: backupApi.overview });
  const [downloading, setDownloading] = useState<string | null>(null);
  const [restoring, setRestoring] = useState<{ fileName: string; source: RestoreSource } | null>(null);

  /** 받기: 서비스 워커가 /api 이동을 가로채지 않도록 리포트처럼 fetch 로 받아 저장한다 */
  const download = async (fileName: string) => {
    setDownloading(fileName);
    try {
      const res = await fetch(backupApi.downloadUrl(fileName), { credentials: "same-origin" });
      if (!res.ok) throw new Error("다운로드 실패");
      const href = URL.createObjectURL(await res.blob());
      const a = document.createElement("a");
      a.href = href;
      a.download = fileName;
      a.click();
      URL.revokeObjectURL(href);
    } catch {
      toast({ title: "백업 파일을 내려받지 못했습니다.", variant: "destructive" });
    } finally {
      setDownloading(null);
    }
  };

  const run = useMutation({
    mutationFn: backupApi.run,
    onSuccess: (file) => {
      toast({ title: "백업을 만들었습니다.", description: `${file.fileName} (${formatBytes(file.size)})`, variant: "success" });
      qc.invalidateQueries({ queryKey: ["backups"] });
    },
    onError: (e) => {
      toast({ title: extractErrorMessage(e), variant: "destructive" });
      qc.invalidateQueries({ queryKey: ["backups"] });
    },
  });

  const remove = useMutation({
    mutationFn: backupApi.remove,
    onSuccess: () => {
      toast({ title: "백업을 지웠습니다.", variant: "success" });
      qc.invalidateQueries({ queryKey: ["backups"] });
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const askRemove = async (f: BackupFile) => {
    const ok = await confirm({
      title: "이 백업을 지울까요?",
      description: `${formatDateTime(f.createdAt)} · ${BACKUP_KIND_LABEL[f.kind]} 백업\n${f.fileName}\n\n지운 백업으로는 복원할 수 없습니다.`,
      confirmText: "삭제",
      destructive: true,
    });
    if (ok) remove.mutate(f.fileName);
  };

  if (isLoading) return <p className="text-sm text-muted-foreground">불러오는 중…</p>;
  if (error || !data) {
    return <p className="text-sm text-destructive">{extractErrorMessage(error, "백업 정보를 불러오지 못했습니다.")}</p>;
  }

  const busy = run.isPending || data.running;

  return (
    <div className="space-y-6">
      <BackupSettingsCard />

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
          <div className="space-y-1">
            <CardTitle className="flex items-center gap-2 text-base">
              <DatabaseBackup className="h-4 w-4" /> DB 백업
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              백업 폴더 <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{data.dir}</code>
              <span className="ml-2">남은 공간 {formatBytes(data.usableBytes)}</span>
              {data.dbVersion && <span className="ml-2">지금 DB 버전 {data.dbVersion}</span>}
            </p>
          </div>
          <Button onClick={() => run.mutate()} disabled={busy}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <DatabaseBackup className="h-4 w-4" />}
            {busy ? "백업 중…" : "지금 백업"}
          </Button>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-xs text-muted-foreground">
            모든 데이터(직원·휴가·정책·이벤트 로그)를 파일 하나로 저장합니다. 로그인 세션은 저장하지 않습니다. 백업 파일에는
            직원 정보가 들어 있으니 내려받은 파일은 안전한 곳에 보관하세요.
            {!isSystemAdmin && " 내려받기·삭제·복원은 시스템 관리자만 할 수 있습니다."}
          </p>
          <BackupTable
            files={data.files}
            empty="아직 백업이 없습니다."
            actions={isSystemAdmin ? (f) => (
              <>
                <Button variant="outline" size="sm" onClick={() => download(f.fileName)} disabled={downloading !== null}
                  aria-label={`${f.fileName} 내려받기`}>
                  {downloading === f.fileName ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                  받기
                </Button>
                <Button variant="outline" size="sm" onClick={() => setRestoring({ fileName: f.fileName, source: "backup" })}
                  aria-label={`${f.fileName}으로 복원`}>
                  <History className="h-4 w-4" /> 복원
                </Button>
                <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive"
                  onClick={() => askRemove(f)} disabled={remove.isPending} aria-label={`${f.fileName} 삭제`}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              </>
            ) : undefined}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FolderInput className="h-4 w-4" /> 가져온 파일
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-xs text-muted-foreground">
            서버 관리자가 백업 폴더 안의 <code className="rounded bg-muted px-1 text-xs">import</code> 폴더에 직접 넣은
            백업 파일입니다(다른 서버의 백업 등). 백업 안의 명령이 그대로 실행되므로 화면에서는 올리거나 지울 수 없고, 서버에
            접근할 수 있는 사람만 넣을 수 있습니다. 이름은 영문·숫자·<code>._-</code>만 쓰고 <code>.dump</code>로 끝나야 합니다.
          </p>
          <BackupTable
            files={data.imports}
            empty="import 폴더에 파일이 없습니다."
            actions={isSystemAdmin ? (f) => (
              <Button variant="outline" size="sm" onClick={() => setRestoring({ fileName: f.fileName, source: "import" })}
                aria-label={`${f.fileName}으로 복원`}>
                <History className="h-4 w-4" /> 복원
              </Button>
            ) : undefined}
          />
        </CardContent>
      </Card>

      {restoring && (
        <RestoreDialog fileName={restoring.fileName} source={restoring.source} onClose={() => setRestoring(null)} />
      )}
    </div>
  );
}

function BackupTable({ files, empty, actions }: {
  files: BackupFile[];
  empty: string;
  /** 줄마다 관리 버튼(시스템 관리자). 없으면 관리 칸을 그리지 않는다 */
  actions?: (f: BackupFile) => React.ReactNode;
}) {
  const columns = actions ? 5 : 4;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>만든 시각</TableHead>
          <TableHead>종류</TableHead>
          <TableHead>DB 버전</TableHead>
          <TableHead className="text-right">크기</TableHead>
          {actions && <TableHead className="text-right">관리</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {files.length === 0 ? (
          <TableRow>
            <TableCell colSpan={columns} className="py-8 text-center text-sm text-muted-foreground">{empty}</TableCell>
          </TableRow>
        ) : (
          files.map((f) => (
            <TableRow key={f.fileName}>
              <TableCell>
                <div>{formatDateTime(f.createdAt)}</div>
                <div className="break-all text-xs text-muted-foreground">{f.fileName}</div>
              </TableCell>
              <TableCell>
                <Badge variant={KIND_BADGE[f.kind]}>{BACKUP_KIND_LABEL[f.kind]}</Badge>
              </TableCell>
              <TableCell className="text-sm">
                {f.dbVersion ?? <span className="text-muted-foreground" title="복원 전 확인 때 백업 안에서 읽습니다">-</span>}
              </TableCell>
              <TableCell className="text-right tabular-nums">{formatBytes(f.size)}</TableCell>
              {actions && (
                <TableCell>
                  <div className="flex justify-end gap-1.5">{actions(f)}</div>
                </TableCell>
              )}
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
