import { requestApi } from "./apiClient";
import type { AuthSession, CurrentUserData, LoginRequest } from "../types/api";

export function login(request: LoginRequest) {
  return requestApi<AuthSession>(
    "/api/auth/login",
    {
      body: JSON.stringify(request),
      headers: { "Content-Type": "application/json" },
      method: "POST"
    },
    "登录失败"
  );
}

export function fetchCurrentUser(token: string) {
  return requestApi<CurrentUserData>(
    "/api/user/me",
    {
      token
    },
    "登录状态校验失败"
  );
}

export async function logout(token: string) {
  await fetch("/api/auth/logout", {
    headers: { Authorization: token },
    method: "POST"
  });
}
