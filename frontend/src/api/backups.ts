import { api, unwrap } from "./client";

export type BackupKind = "MANUAL" | "AUTO";

export const BACKUP_KIND_LABEL: Record<BackupKind, string> = {
  MANUAL: "수동",
  AUTO: "자동",
};

export interface BackupFile {
  fileName: string;
  /** 파일 이름에 적힌 만든 시각(한국 시간) */
  createdAt: string;
  /** 바이트 */
  size: number;
  kind: BackupKind;
}

export interface BackupOverview {
  /** 백업 폴더(서버 기준 경로) */
  dir: string;
  /** 그 폴더가 있는 디스크의 남은 공간(바이트) */
  usableBytes: number;
  /** 지금 백업이 진행 중인지(다른 관리자가 실행한 것 포함) */
  running: boolean;
  /** 최신순 */
  files: BackupFile[];
}

export const backupApi = {
  overview: () => unwrap<BackupOverview>(api.get("/backups")),
  /** 지금 백업. 끝날 때까지 기다린다 */
  run: () => unwrap<BackupFile>(api.post("/backups")),
  /** 내려받기 주소(시스템 관리자만) */
  downloadUrl: (fileName: string) => `/api/backups/${encodeURIComponent(fileName)}/download`,
};
