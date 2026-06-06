export type ApiResult<T> = {
  code: string;
  data?: T;
  message?: string;
  requestId?: string;
};

export type AuthSession = {
  avatar: string;
  role: string;
  token: string;
  userId: string;
  username: string;
};

export type CurrentUserData = {
  avatar: string;
  role: string;
  userId: string;
  username: string;
};

export type LoginRequest = {
  password: string;
  username: string;
};

export type NovelChapterData = {
  charCount: number;
  content: string;
  id: string;
  index: number;
  title: string;
};

export type ChapterPreviewData = {
  chapterCount: number;
  chapters?: NovelChapterData[];
  message: string;
  valid: boolean;
};

export type ScriptYamlGenerateRequest = {
  author: string;
  forceNewTask?: boolean;
  logline: string;
  projectId?: string;
  text: string;
  title: string;
};

export type ScriptYamlData = {
  chapterCount: number;
  fallback?: boolean;
  fallbackReason?: string | null;
  generationMode?: string | null;
  schemaVersion: string;
  title: string;
  validationError?: string | null;
  validationStatus?: string | null;
  yaml: string;
};

export type ScriptYamlStreamEventName =
  | "meta"
  | "progress"
  | "message"
  | "validation"
  | "finish"
  | "cancel"
  | "error"
  | "done";

export type ScriptYamlStreamPayload = {
  message?: string | null;
  progress?: number | null;
  result?: ScriptYamlData | null;
  stage?: string | null;
  taskId?: string | null;
  valid?: boolean | null;
  validationError?: string | null;
  validationStatus?: string | null;
};

export type ScriptProjectCreateRequest = {
  description: string;
  text: string;
  title: string;
};

export type ScriptConversionTaskData = {
  createTime?: string;
  draftId?: string | null;
  endTime?: string | null;
  errorMessage?: string | null;
  message?: string | null;
  modelId?: string | null;
  progress?: number | null;
  projectId: string;
  schemaVersion?: string | null;
  startTime?: string | null;
  status: string;
  taskId: string;
  updateTime?: string;
};

export type ScriptProjectData = {
  chapterCount: number;
  chapters?: NovelChapterData[];
  projectId: string;
  title: string;
};

export type ScriptProjectListItem = {
  createTime?: string;
  description?: string | null;
  projectId: string;
  status: string;
  title: string;
  updateTime?: string;
};

export type ScriptDraftSaveRequest = {
  projectId: string;
  schemaVersion: string;
  title: string;
  yaml: string;
};

export type ScriptDraftSaveData = {
  draftId: string;
  projectId: string;
  schemaVersion: string;
  title: string;
  validationError?: string | null;
  validationStatus: string;
};

export type ScriptDraftListItem = {
  createTime?: string;
  draftId: string;
  projectId: string;
  schemaVersion: string;
  title: string;
  updateTime?: string;
  validationStatus: string;
};

export type ScriptDraftDetail = ScriptDraftListItem & {
  validationError?: string | null;
  yaml: string;
};
