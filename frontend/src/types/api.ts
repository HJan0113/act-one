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
  logline: string;
  text: string;
  title: string;
};

export type ScriptYamlData = {
  chapterCount: number;
  schemaVersion: string;
  title: string;
  yaml: string;
};

export type ScriptProjectCreateRequest = {
  description: string;
  text: string;
  title: string;
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
