import { api, unwrap } from "./client";

export type BackupKind = "MANUAL" | "AUTO" | "PRE_RESTORE" | "IMPORTED";

export const BACKUP_KIND_LABEL: Record<BackupKind, string> = {
  MANUAL: "수동",
  AUTO: "자동",
  PRE_RESTORE: "복원 전",
  IMPORTED: "가져온 파일",
};

export interface BackupFile {
  fileName: string;
  /** 만든 시각(한국 시간). 가져온 파일은 파일 수정 시각 */
  createdAt: string;
  /** 바이트 */
  size: number;
  kind: BackupKind;
  /** 백업 정보 파일(.json)에 적힌 DB 버전. 없으면 null(복원 전 확인 때 백업 안에서 읽음) */
  dbVersion: string | null;
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
  /** 서버 관리자가 import/ 폴더에 넣은 외부 파일(최신순) */
  imports: BackupFile[];
  /** 지금 앱 DB 버전 */
  dbVersion: string | null;
}

export type BackupFrequency = "DAILY" | "WEEKLY" | "HOURLY";

export const BACKUP_FREQUENCY_LABEL: Record<BackupFrequency, string> = {
  DAILY: "매일",
  WEEKLY: "매주",
  HOURLY: "N시간마다",
};

/** 1 = 월요일 … 7 = 일요일 */
export const WEEKDAY_LABEL = ["", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"];

export interface BackupSettingsForm {
  enabled: boolean;
  frequency: BackupFrequency;
  dayOfWeek: number;
  /** "02:00" */
  runTime: string;
  intervalHours: number;
  /** 보관 기간(개월, 1~24): 이 기간 안의 자동 백업은 모두 남고, 더 오래된 자동 백업은 지워진다 */
  keepMonths: number;
}

export interface BackupSettings extends BackupSettingsForm {
  /** "매일 02:00" */
  scheduleLabel: string;
  /** 다음 실행 시각(꺼져 있으면 null) */
  nextRunAt: string | null;
  /** 다른 자동 작업과 같은 시각이면 안내(막지는 않음) */
  warnings: string[];
}

export type RestoreSource = "backup" | "import";

/** 복원 전 확인 결과 */
export interface RestoreCheck {
  fileName: string;
  kind: BackupKind;
  createdAt: string;
  size: number;
  /** 백업의 DB 버전 */
  dbVersion: string;
  /** 지금 앱 DB 버전 */
  currentVersion: string | null;
  /** 백업 정보 파일의 체크섬과 맞춰 봤는지(정보 파일이 없으면 false) */
  checksumVerified: boolean;
}

export interface RestoreResult {
  fileName: string;
  /** 복원 직전 상태의 백업(되돌릴 때 씀) */
  preRestoreFile: string;
  fromVersion: string;
  toVersion: string;
}

const path = (fileName: string) => `/backups/${encodeURIComponent(fileName)}`;

export const backupApi = {
  overview: () => unwrap<BackupOverview>(api.get("/backups")),
  /** 지금 백업. 끝날 때까지 기다린다 */
  run: () => unwrap<BackupFile>(api.post("/backups")),
  /** 내려받기 주소(시스템 관리자만) */
  downloadUrl: (fileName: string) => `/api${path(fileName)}/download`,
  /** 백업 삭제(시스템 관리자만). 수동·복원 전 백업은 자동 정리되지 않아 여기서 지운다 */
  remove: (fileName: string) => unwrap<void>(api.delete(path(fileName))),
  settings: () => unwrap<BackupSettings>(api.get("/backups/settings")),
  updateSettings: (form: BackupSettingsForm) => unwrap<BackupSettings>(api.put("/backups/settings", form)),
  /** 복원 전 확인(시스템 관리자만): 체크섬·파일 형식·DB 버전 */
  check: (fileName: string, source: RestoreSource) =>
    unwrap<RestoreCheck>(api.get(`${path(fileName)}/check`, { params: { source } })),
  /** 복원(시스템 관리자만). 끝나면 모든 로그인 세션이 지워진다(본인 포함) */
  restore: (fileName: string, source: RestoreSource) =>
    unwrap<RestoreResult>(api.post(`${path(fileName)}/restore`, { confirm: "복원" }, { params: { source } })),
};

/** 점검 모드(복원 중)인지. 로그인 없이 응답하며 axios 오류 처리(로그인 이동 등)를 거치지 않는다. */
export async function fetchMaintenance(): Promise<boolean> {
  const res = await fetch("/api/backups/status", { credentials: "same-origin", cache: "no-store" });
  const body = (await res.json()) as { data?: { maintenance?: boolean } };
  return body.data?.maintenance === true;
}
