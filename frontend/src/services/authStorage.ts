import type { AuthSession } from "../types/api";

const authStorageKey = "act-one-auth";

export function readStoredAuth(): AuthSession | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(authStorageKey);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<AuthSession>;
    if (parsed.token && parsed.userId && parsed.username && parsed.role) {
      return {
        avatar: parsed.avatar || "",
        role: parsed.role,
        token: parsed.token,
        userId: parsed.userId,
        username: parsed.username
      };
    }
  } catch {
    window.localStorage.removeItem(authStorageKey);
  }
  return null;
}

export function writeStoredAuth(session: AuthSession) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(authStorageKey, JSON.stringify(session));
  }
}

export function removeStoredAuth() {
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(authStorageKey);
  }
}
