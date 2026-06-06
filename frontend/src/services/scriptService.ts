import {readResponseError, requestApi, withAuthHeader} from "./apiClient";
import type {
    ChapterPreviewData,
    ScriptConversionTaskData,
    ScriptDraftDetail,
    ScriptDraftListItem,
    ScriptDraftSaveData,
    ScriptDraftSaveRequest,
    ScriptProjectCreateRequest,
    ScriptProjectData,
    ScriptProjectListItem,
    ScriptYamlData,
    ScriptYamlGenerateRequest,
    ScriptYamlStreamEventName,
    ScriptYamlStreamPayload
} from "../types/api";

export type ScriptYamlStreamHandlers = {
  onCancel?: (payload: ScriptYamlStreamPayload) => void;
  onDone?: (payload: ScriptYamlStreamPayload) => void;
  onError?: (payload: ScriptYamlStreamPayload) => void;
  onEvent?: (eventName: ScriptYamlStreamEventName, payload: ScriptYamlStreamPayload) => void;
  onFinish?: (payload: ScriptYamlStreamPayload) => void;
  onMessage?: (payload: ScriptYamlStreamPayload) => void;
  onMeta?: (payload: ScriptYamlStreamPayload) => void;
  onProgress?: (payload: ScriptYamlStreamPayload) => void;
  onValidation?: (payload: ScriptYamlStreamPayload) => void;
};

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

export function generateYaml(request: ScriptYamlGenerateRequest, token?: string, signal?: AbortSignal) {
  return requestApi<ScriptYamlData>(
    "/api/script/drafts/yaml/from-text",
    {
      body: JSON.stringify(request),
      headers: { "Content-Type": "application/json" },
      method: "POST",
        signal,
      token
    },
    "YAML 生成失败"
  );
}

export async function streamGenerateYaml(
  request: ScriptYamlGenerateRequest,
  handlers: ScriptYamlStreamHandlers = {},
  token?: string,
  signal?: AbortSignal
) {
  const response = await fetch("/api/script/drafts/yaml/from-text/stream", {
    body: JSON.stringify(request),
    headers: withAuthHeader(
      {
        Accept: "text/event-stream",
        "Content-Type": "application/json"
      },
      token
    ),
    method: "POST",
    signal
  });

  if (!response.ok) {
    throw new Error(await readResponseError(response, "YAML 流式生成失败"));
  }
  if (!response.body) {
    throw new Error("当前浏览器不支持流式生成。");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let finalResult: ScriptYamlData | undefined;
  let streamCanceled = "";
  let streamError = "";

  const dispatchFrame = (frame: string) => {
    const parsed = readSseFrame(frame);
    if (!parsed) {
      return;
    }
    const payload = parseStreamPayload(parsed.data);
    handlers.onEvent?.(parsed.eventName, payload);
    emitStreamHandler(handlers, parsed.eventName, payload);
    if (parsed.eventName === "finish") {
      finalResult = payload.result || undefined;
    }
    if (parsed.eventName === "error") {
      streamError = payload.message || "YAML 生成失败。";
    }
    if (parsed.eventName === "cancel") {
      streamCanceled = payload.message || "生成已停止。";
    }
  };

  const processBuffer = (force = false) => {
    let match = buffer.match(/\r?\n\r?\n/);
    while (match?.index !== undefined) {
      const frame = buffer.slice(0, match.index);
      buffer = buffer.slice(match.index + match[0].length);
      dispatchFrame(frame);
      match = buffer.match(/\r?\n\r?\n/);
    }
    if (force && buffer.trim()) {
      const frame = buffer;
      buffer = "";
      dispatchFrame(frame);
    }
  };

  for (;;) {
    const {done, value} = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, {stream: true});
    processBuffer();
  }

  buffer += decoder.decode();
  processBuffer(true);

  if (streamError) {
    throw new Error(streamError);
  }
  if (streamCanceled) {
    throw createAbortError(streamCanceled);
  }
  if (!finalResult) {
    throw new Error("YAML 流式生成未返回完成事件。");
  }
  return finalResult;
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

export function cancelGenerationTask(taskId: string, token?: string) {
  return requestApi<ScriptConversionTaskData>(
    `/api/script/drafts/tasks/${encodeURIComponent(taskId)}/cancel`,
    {
      method: "POST",
      token
    },
    "停止生成失败"
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

const streamEventNames = new Set<ScriptYamlStreamEventName>([
  "cancel",
  "done",
  "error",
  "finish",
  "message",
  "meta",
  "progress",
  "validation"
]);

function readSseFrame(frame: string): { data: string; eventName: ScriptYamlStreamEventName } | null {
  let eventName: ScriptYamlStreamEventName = "message";
  const dataLines: string[] = [];

  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(":")) {
      continue;
    }
    const delimiterIndex = line.indexOf(":");
    const field = delimiterIndex === -1 ? line : line.slice(0, delimiterIndex);
    let value = delimiterIndex === -1 ? "" : line.slice(delimiterIndex + 1);
    if (value.startsWith(" ")) {
      value = value.slice(1);
    }
    if (field === "event") {
      eventName = normalizeStreamEventName(value);
    }
    if (field === "data") {
      dataLines.push(value);
    }
  }

  if (dataLines.length === 0) {
    return null;
  }
  return {
    data: dataLines.join("\n"),
    eventName
  };
}

function parseStreamPayload(data: string): ScriptYamlStreamPayload {
  if (!data || data === "[DONE]") {
    return {};
  }
  try {
    return JSON.parse(data) as ScriptYamlStreamPayload;
  } catch {
    return {message: data};
  }
}

function normalizeStreamEventName(value: string): ScriptYamlStreamEventName {
  return streamEventNames.has(value as ScriptYamlStreamEventName)
    ? (value as ScriptYamlStreamEventName)
    : "message";
}

function emitStreamHandler(
  handlers: ScriptYamlStreamHandlers,
  eventName: ScriptYamlStreamEventName,
  payload: ScriptYamlStreamPayload
) {
  switch (eventName) {
    case "cancel":
      handlers.onCancel?.(payload);
      break;
    case "done":
      handlers.onDone?.(payload);
      break;
    case "error":
      handlers.onError?.(payload);
      break;
    case "finish":
      handlers.onFinish?.(payload);
      break;
    case "meta":
      handlers.onMeta?.(payload);
      break;
    case "progress":
      handlers.onProgress?.(payload);
      break;
    case "validation":
      handlers.onValidation?.(payload);
      break;
    case "message":
    default:
      handlers.onMessage?.(payload);
      break;
  }
}

function createAbortError(message: string) {
  return new DOMException(message, "AbortError");
}
