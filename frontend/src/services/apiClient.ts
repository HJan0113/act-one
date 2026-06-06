import type { ApiResult } from "../types/api";

type ApiRequestOptions = RequestInit & {
  token?: string;
};

export async function requestApi<T>(url: string, init: ApiRequestOptions | undefined, fallbackMessage: string): Promise<T> {
  const { token, ...requestInit } = init || {};
  const headers = withAuthHeader(requestInit.headers, token);
  const response = await fetch(url, { ...requestInit, headers });
  return readApiData<T>(response, fallbackMessage);
}

export async function readApiData<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`${fallbackMessage}：${response.status}`);
  }
  const result = (await response.json()) as ApiResult<T>;
  if (result.code !== "0" || result.data === undefined || result.data === null) {
    throw new Error(result.message || `${fallbackMessage}。`);
  }
  return result.data;
}

export async function readResponseError(response: Response, fallbackMessage: string) {
  try {
    const result = (await response.json()) as ApiResult<unknown>;
    return result.message || `${fallbackMessage}：${response.status}`;
  } catch {
    return `${fallbackMessage}：${response.status}`;
  }
}

export function withAuthHeader(source: HeadersInit | undefined, token?: string) {
  const headers = new Headers(source);
  if (token) {
    headers.set("Authorization", token);
  }
  return headers;
}

export function isAuthExpiredError(error: unknown) {
  return error instanceof Error && (error.message.includes("未登录") || error.message.includes("登录已过期"));
}
