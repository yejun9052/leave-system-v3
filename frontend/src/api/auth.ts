import { api, unwrap } from "./client";
import type { Me, TokenResponse } from "@/types";

export const authApi = {
  login: (email: string, password: string) =>
    unwrap<TokenResponse>(api.post("/auth/login", { email, password })),

  me: () => unwrap<Me>(api.get("/auth/me")),
};
