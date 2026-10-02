import { api, unwrap } from "./client";

export interface Holiday {
  date: string;
  name: string;
}

export interface HolidaySyncResult {
  year: number;
  added: Holiday[];
  renamed: { date: string; before: string; after: string }[];
  /** 기존에 있었지만 이번 API 응답에는 없던 날짜(삭제하지 않음) */
  missingInApi: Holiday[];
  adjustedRequests: number;
  restoredDays: number;
}

export const holidayApi = {
  list: (year: number) => unwrap<Holiday[]>(api.get("/holidays", { params: { year } })),
  /** 공휴일 API 에서 해당 연도를 받아 저장 + 새 공휴일이 걸친 기존 휴가 자동 조정 */
  sync: (year: number) =>
    unwrap<HolidaySyncResult>(api.post("/holidays/sync", null, { params: { year } })),
};
