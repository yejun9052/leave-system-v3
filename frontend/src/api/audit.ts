import { api, unwrap } from "./client";
import type { Page } from "@/types";

export interface AuditLog {
  id: number;
  actorId: number | null;
  actorName: string;
  action: string;
  entityType: string;
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
