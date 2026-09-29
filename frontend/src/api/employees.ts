import { api, unwrap } from "./client";
import type { Employee, EmployeeStatus, Page, Role } from "@/types";

export interface EmployeeCreate {
  email: string;
  name: string;
  employeeNo?: string;
  departmentId?: number | null;
  position?: string;
  phone?: string;
  hireDate: string;
  roles: Role[];
  initialPassword?: string;
}

export type EmployeeUpdate = Omit<EmployeeCreate, "initialPassword">;

export interface EmployeeSearchParams {
  keyword?: string;
  departmentId?: number;
  status?: EmployeeStatus;
  page?: number;
  size?: number;
}

export const employeeApi = {
  search: (params: EmployeeSearchParams) =>
    unwrap<Page<Employee>>(api.get("/employees", { params })),
  get: (id: number) => unwrap<Employee>(api.get(`/employees/${id}`)),
  create: (body: EmployeeCreate) => unwrap<Employee>(api.post("/employees", body)),
  update: (id: number, body: EmployeeUpdate) => unwrap<Employee>(api.put(`/employees/${id}`, body)),
  resign: (id: number, resignedDate?: string) =>
    unwrap<void>(api.delete(`/employees/${id}`, { params: { resignedDate } })),
  reactivate: (id: number) => unwrap<void>(api.patch(`/employees/${id}/reactivate`)),
  resetPassword: (id: number, newPassword: string) =>
    unwrap<void>(api.patch(`/employees/${id}/password`, { newPassword })),
  updateMyProfile: (body: { name: string; position?: string; phone?: string }) =>
    unwrap<Employee>(api.put("/employees/me/profile", body)),
  changeMyPassword: (currentPassword: string, newPassword: string) =>
    unwrap<void>(api.patch("/employees/me/password", { currentPassword, newPassword })),
  exportUrl: "/api/employees/export",
  importExcel: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return unwrap<{ created: number; errors: string[] }>(
      api.post("/employees/import", form, { headers: { "Content-Type": "multipart/form-data" } }),
    );
  },
};
