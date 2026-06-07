import { readResponseError, requestApi, withAuthHeader } from "./apiClient";
import type {
  ChapterPreviewData,
  ScriptDraftDetail,
  ScriptDraftListItem,
  ScriptDraftSaveData,
  ScriptDraftSaveRequest,
  ScriptProjectCreateRequest,
  ScriptProjectData,
  ScriptProjectListItem,
  ScriptYamlData,
  ScriptYamlGenerateRequest
} from "../types/api";

export function previewChapters(text: string, token?: string) {
  return requestApi<ChapterPreviewData>(
    "/api/novels/chapters/preview",
    {
      body: JSON.stringify({ text }),
      headers: { "Content-Type": "application/json" },
      method: "POST",
      token
    },
    "章节解析失败"
  );
}

export function generateYaml(request: ScriptYamlGenerateRequest, token?: string) {
  return requestApi<ScriptYamlData>(
    "/api/script/drafts/yaml/from-text",
    {
      body: JSON.stringify(request),
      headers: { "Content-Type": "application/json" },
      method: "POST",
      token
    },
    "YAML 生成失败"
  );
}

export function createProjectFromText(request: ScriptProjectCreateRequest, token?: string) {
  return requestApi<ScriptProjectData>(
    "/api/script/projects/from-text",
    {
      body: JSON.stringify(request),
      headers: { "Content-Type": "application/json" },
      method: "POST",
      token
    },
    "项目创建失败"
  );
}

export function listMyProjects(token?: string) {
  return requestApi<ScriptProjectListItem[]>(
    "/api/script/projects/my",
    {
      token
    },
    "项目列表加载失败"
  );
}

export function listDrafts(projectId: string, token?: string) {
  return requestApi<ScriptDraftListItem[]>(
    `/api/script/drafts?projectId=${encodeURIComponent(projectId)}`,
    {
      token
    },
    "草稿列表加载失败"
  );
}

export function saveDraft(request: ScriptDraftSaveRequest, token?: string) {
  return requestApi<ScriptDraftSaveData>(
    "/api/script/drafts",
    {
      body: JSON.stringify(request),
      headers: { "Content-Type": "application/json" },
      method: "POST",
      token
    },
    "草稿保存失败"
  );
}

export function getDraftDetail(draftId: string, token?: string) {
  return requestApi<ScriptDraftDetail>(
    `/api/script/drafts/${encodeURIComponent(draftId)}`,
    {
      token
    },
    "草稿加载失败"
  );
}

export async function exportDraftYaml(draftId: string, fallbackFilename: string, token?: string) {
  const response = await fetch(`/api/script/drafts/${encodeURIComponent(draftId)}/export.yaml`, {
    headers: withAuthHeader(undefined, token)
  });
  if (!response.ok) {
    throw new Error(await readResponseError(response, "YAML 导出失败"));
  }
  return {
    blob: await response.blob(),
    filename: readDownloadFilename(response, fallbackFilename)
  };
}

function readDownloadFilename(response: Response, fallback: string) {
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (encoded?.[1]) {
    return decodeURIComponent(encoded[1]);
  }
  const simple = disposition.match(/filename="?([^";]+)"?/i);
  return simple?.[1] || fallback;
}
