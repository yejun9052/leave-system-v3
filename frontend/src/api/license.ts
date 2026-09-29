import { api, unwrap } from "./client";

export interface LicenseStatus {
  enforced: boolean;
  valid: boolean;
  reason: string;
  licensee: string | null;
  expiresAt: string | null;
  daysLeft: number;
  maxUsers: number;
}

export const licenseApi = {
  status: () => unwrap<LicenseStatus>(api.get("/license")),
};
