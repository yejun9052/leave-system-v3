import { api, unwrap } from "./client";
import type { Department } from "@/types";

export interface DepartmentCreate {
  name: string;
  parentId?: number | null;
  leadId?: number | null;
  sortOrder?: number;
}

export interface DepartmentUpdate {
  name: string;
  /** 보내지 않으면 부서장을 바꾸지 않는다(부서 관리 화면은 보내지 않음, 부서장은 사용자 관리에서 정함) */
  leadId?: number | null;
  sortOrder?: number;
}

export const departmentApi = {
  tree: () => unwrap<Department[]>(api.get("/departments", { params: { view: "tree" } })),
  flat: () => unwrap<Department[]>(api.get("/departments", { params: { view: "flat" } })),
  create: (body: DepartmentCreate) => unwrap<Department>(api.post("/departments", body)),
  update: (id: number, body: DepartmentUpdate) =>
    unwrap<Department>(api.put(`/departments/${id}`, body)),
  move: (id: number, newParentId: number | null) =>
    unwrap<Department>(api.patch(`/departments/${id}/move`, { newParentId })),
  remove: (id: number) => unwrap<void>(api.delete(`/departments/${id}`)),
};
