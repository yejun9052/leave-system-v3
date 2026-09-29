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
