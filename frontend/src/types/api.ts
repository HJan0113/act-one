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
  resumeTaskId?: string;
  text: string;
  title: string;
};

export type ScriptAiUsageSummary = {
  callCount: number;
  completionTokens: number;
  estimated: boolean;
  promptTokens: number;
  totalTokens: number;
};

export type ScriptYamlData = {
  chapterCount: number;
  fallback?: boolean;
  fallbackReason?: string | null;
  generationMode?: string | null;
  schemaVersion: string;
  title: string;
  usageSummary?: ScriptAiUsageSummary | null;
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
  errorType?: string | null;
  failedStage?: string | null;
  resumeStage?: string | null;
  retryable?: boolean | null;
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
  errorType?: string | null;
  failedStage?: string | null;
  message?: string | null;
  modelId?: string | null;
  progress?: number | null;
  projectId: string;
  resumeStage?: string | null;
  retryable?: boolean | null;
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

export type ScriptRhythmOverallMetrics = {
  actionCharCount: number;
  actionRatio: number;
  averageConflictScore: number;
  averageTurningScore: number;
  dialogueCharCount: number;
  dialogueCount: number;
  dialogueRatio: number;
  imbalanceSceneCount: number;
  purposeCoverage: number;
  sceneCount: number;
};

export type ScriptSceneRhythmMetric = {
  actionCharCount: number;
  actionRatio: number;
  conflictScore: number;
  dialogueCharCount: number;
  dialogueCount: number;
  dialogueRatio: number;
  paceLevel: string;
  purposeScore: number;
  sceneId: string;
  summary: string;
  title: string;
  turningScore: number;
  warnings: string[];
};

export type ScriptRhythmAnalysisData = {
  assessmentMode: string;
  characterDensity: ScriptCharacterDensityMetric[];
  draftId: string;
  overall: ScriptRhythmOverallMetrics;
  scenes: ScriptSceneRhythmMetric[];
  suggestions: string[];
};

export type ScriptDialoguePatchData = {
  emotion?: string | null;
  line: string;
  speaker: string;
};

export type ScriptScenePatchData = {
  action?: string | null;
  aiExpansion?: boolean | null;
  dialogues?: ScriptDialoguePatchData[] | null;
  location?: string | null;
  riskWarnings?: string[] | null;
  sceneId: string;
  scenePurpose?: string | null;
  summary?: string | null;
  time?: string | null;
  title?: string | null;
};

export type ScriptSceneRefineRequest = {
  draftId: string;
  instruction: string;
  sceneId: string;
};

export type ScriptSceneRefineData = {
  draftId: string;
  patchedYaml: string;
  sceneId: string;
  scenePatch: ScriptScenePatchData;
  validationError?: string | null;
  validationStatus: string;
};

export type ScriptCharacterDensityMetric = {
  characterId: string;
  characterName: string;
  dialogueCount: number;
  sceneCount: number;
  sceneIds: string[];
};

export type ShotSuggestion = {
  action: string;
  description: string;
  emotion: string;
  keyProp: string;
  shotId: string;
  shotType: string;
  sound: string;
  subject: string;
  tone: string;
};

export type ScriptStoryboardData = {
  assessmentMode: string;
  draftId: string;
  sceneId: string;
  shots: ShotSuggestion[];
  suggestions: string[];
};

export type ScriptDemoNovelData = {
  author: string;
  logline: string;
  text: string;
  title: string;
};
