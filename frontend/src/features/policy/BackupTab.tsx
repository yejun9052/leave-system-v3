import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DatabaseBackup, Download, Loader2 } from "lucide-react";
import { BACKUP_KIND_LABEL, backupApi } from "@/api/backups";
import { extractErrorMessage } from "@/api/client";
import { formatDateTime } from "@/lib/dateFormat";
import { useAuthStore } from "@/store/auth";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";

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
 * 정책 → 백업: 지금 백업(DB 전체를 서버 백업 폴더에 파일로), 백업 목록. 내려받기는 시스템 관리자만.
 * 백업 실행·내려받기는 이벤트 로그에 남는다.
 */
export default function BackupTab() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const canDownload = useAuthStore((s) => s.hasAnyRole("SYSTEM_ADMIN"));
  const { data, isLoading, error } = useQuery({ queryKey: ["backups"], queryFn: backupApi.overview });
  const [downloading, setDownloading] = useState<string | null>(null);

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

  if (isLoading) return <p className="text-sm text-muted-foreground">불러오는 중…</p>;
  if (error || !data) {
    return <p className="text-sm text-destructive">{extractErrorMessage(error, "백업 정보를 불러오지 못했습니다.")}</p>;
  }

  const busy = run.isPending || data.running;

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
        <div className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <DatabaseBackup className="h-4 w-4" /> DB 백업
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            백업 폴더 <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{data.dir}</code>
            <span className="ml-2">남은 공간 {formatBytes(data.usableBytes)}</span>
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
          {!canDownload && " 내려받기는 시스템 관리자만 할 수 있습니다."}
        </p>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>만든 시각</TableHead>
              <TableHead>종류</TableHead>
              <TableHead className="text-right">크기</TableHead>
              {canDownload && <TableHead className="w-28 text-right">내려받기</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.files.length === 0 ? (
              <TableRow>
                <TableCell colSpan={canDownload ? 4 : 3} className="py-8 text-center text-sm text-muted-foreground">
                  아직 백업이 없습니다.
                </TableCell>
              </TableRow>
            ) : (
              data.files.map((f) => (
                <TableRow key={f.fileName}>
                  <TableCell>
                    <div>{formatDateTime(f.createdAt)}</div>
                    <div className="text-xs text-muted-foreground">{f.fileName}</div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={f.kind === "AUTO" ? "secondary" : "outline"}>{BACKUP_KIND_LABEL[f.kind]}</Badge>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatBytes(f.size)}</TableCell>
                  {canDownload && (
                    <TableCell className="text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => download(f.fileName)}
                        disabled={downloading !== null}
                      >
                        {downloading === f.fileName ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Download className="h-4 w-4" />
                        )}{" "}
                        받기
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
