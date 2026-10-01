import { api, unwrap } from "./client";
import type { Page } from "@/types";

export interface AuditLog {
  id: number;
  actorId: number | null;
  actorName: string;
  action: string;
  /** 서버가 정한 한글 표시 이름(검색과 같은 이름). 모르는 코드면 코드 그대로 */
  actionLabel: string;
  entityType: string;
  entityLabel: string | null;
  entityId: string | null;
  detail: string;
  success: boolean;
  createdAt: string;
}

export const auditApi = {
  list: (keyword: string, page = 0, size = 30) =>
    unwrap<Page<AuditLog>>(
      api.get("/audit-logs", { params: { keyword: keyword || undefined, page, size } }),
    ),
};
