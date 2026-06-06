import type {FormEvent, ReactNode} from "react";
import {useEffect, useMemo, useRef, useState} from "react";
import {
    CircleStop,
    FolderOpen,
    History,
    Loader2,
    LogIn,
    LogOut,
    Play,
    RefreshCcw,
    Save,
    UserCircle,
    Wand2
} from "lucide-react";

import {ActionButton} from "./components/ActionButton";
import {StatusBadge, type StatusTone} from "./components/StatusBadge";
import {
    AppShell,
    ChapterList,
    type ChapterOutline,
    ConversionProgress,
    InputPanel,
    type ProgressStep,
    ScriptPreviewPanel,
    Sidebar,
    TaskConsole,
    type TaskStatus,
    type TaskSummary,
    TopBar,
    type ValidationCheck,
    type WorkspaceView
} from "./components/workspace";
import {isAuthExpiredError} from "./services/apiClient";
import {fetchCurrentUser, login as loginWithPassword, logout as logoutSession} from "./services/authService";
import {readStoredAuth, removeStoredAuth, writeStoredAuth} from "./services/authStorage";
import {
    cancelGenerationTask as cancelScriptGenerationTask,
    createProjectFromText,
    exportDraftYaml as exportDraftYamlFile,
    getDraftDetail,
    listDrafts as listProjectDrafts,
    listMyProjects,
    previewChapters as previewNovelChapters,
    saveDraft as saveScriptDraft,
    streamGenerateYaml as streamGenerateScriptYaml
} from "./services/scriptService";
import type {AuthSession, ScriptDraftListItem, ScriptProjectListItem, ScriptYamlStreamPayload} from "./types/api";

const placeholderYaml = `schema_version: "1.0"
kind: "screenplay_draft"
metadata:
  title: "未命名剧本"
  author: "未填写"
  logline: "待补充"
source:
  type: "novel"
  chapter_count: 3
  generation_mode: "rule_based_scaffold"
characters: []
acts:
  - id: "act_1"
    title: "第一幕"
    chapter_range:
      start: 1
      end: 1
scenes: []`;

const initialChapterMessage = "输入至少 3 个章节后，先解析章节，再进入 AI 生成。";
const initialYamlMessage = "生成后将在这里展示可编辑 YAML 初稿。";
const defaultLoginUsername = "admin";
const defaultLoginPassword = "admin";
const chapterHeadingPattern = /^第\s*([0-9一二三四五六七八九十百千万两〇零]+)\s*[章节回集部卷幕].*$/gm;

type ConversionTaskState = Omit<TaskSummary, "canCancel">;

function App() {
  const [authSession, setAuthSession] = useState<AuthSession | null>(() => readStoredAuth());
  const [authChecking, setAuthChecking] = useState(false);
  const [authError, setAuthError] = useState("");
  const [loginUsername, setLoginUsername] = useState(defaultLoginUsername);
  const [loginPassword, setLoginPassword] = useState(defaultLoginPassword);
  const [loginLoading, setLoginLoading] = useState(false);
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [logline, setLogline] = useState("");
  const [novelText, setNovelText] = useState("");
  const [message, setMessage] = useState(initialChapterMessage);
  const [yamlMessage, setYamlMessage] = useState(initialYamlMessage);
  const [chapterCount, setChapterCount] = useState(0);
  const [schemaVersion, setSchemaVersion] = useState("");
  const [generatedTitle, setGeneratedTitle] = useState("");
  const [projectId, setProjectId] = useState("");
  const [projects, setProjects] = useState<ScriptProjectListItem[]>([]);
  const [draftId, setDraftId] = useState("");
  const [drafts, setDrafts] = useState<ScriptDraftListItem[]>([]);
  const [isValid, setIsValid] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [yamlLoading, setYamlLoading] = useState(false);
  const [saveLoading, setSaveLoading] = useState(false);
  const [exportLoading, setExportLoading] = useState(false);
  const [projectLoading, setProjectLoading] = useState(false);
  const [draftLoading, setDraftLoading] = useState(false);
  const [yamlDraft, setYamlDraft] = useState("");
  const [activeChapterIndex, setActiveChapterIndex] = useState(1);
  const [validationStatus, setValidationStatus] = useState("pending");
  const [validationError, setValidationError] = useState("");
  const [generationMode, setGenerationMode] = useState("");
  const [fallbackReason, setFallbackReason] = useState("");
    const [conversionTask, setConversionTask] = useState<ConversionTaskState>(() => createInitialTask());
    const [activeView, setActiveView] = useState<WorkspaceView>("source");
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const generationAbortRef = useRef<AbortController | null>(null);

  const canPreview = useMemo(() => novelText.trim().length > 0 && !previewLoading, [novelText, previewLoading]);
  const canGenerate = useMemo(() => novelText.trim().length > 0 && !yamlLoading, [novelText, yamlLoading]);
  const canSave = useMemo(() => yamlDraft.trim().length > 0 && !saveLoading, [yamlDraft, saveLoading]);
  const canExport = useMemo(() => draftId.length > 0 && !exportLoading, [draftId, exportLoading]);
  const canRefreshDrafts = useMemo(() => projectId.length > 0 && !draftLoading, [projectId, draftLoading]);
  const chapterOutline = useMemo(() => parseChapterOutline(novelText), [novelText]);
  const selectedChapter = useMemo(
    () => chapterOutline.find((chapter) => chapter.index === activeChapterIndex) || chapterOutline[0],
    [activeChapterIndex, chapterOutline]
  );
  const characterCount = useMemo(() => novelText.trim().length, [novelText]);
  const visibleChapterCount = chapterCount || chapterOutline.length;
  const schemaChecks = useMemo(() => createSchemaChecks(yamlDraft, validationStatus, validationError), [
    validationError,
    validationStatus,
    yamlDraft
  ]);
    const taskSummary = useMemo<TaskSummary>(
        () => ({
            ...conversionTask,
            canCancel: yamlLoading && Boolean(generationAbortRef.current)
        }),
        [conversionTask, yamlLoading]
    );
  const workflowSteps = useMemo(
    () =>
      createWorkflowSteps({
        characterCount,
          conversionTask,
        draftId,
        isValid,
        message,
        previewLoading,
        saveLoading,
          validationStatus,
        yamlDraft,
        yamlLoading,
        yamlMessage
      }),
      [
          characterCount,
          conversionTask,
          draftId,
          isValid,
          message,
          previewLoading,
          saveLoading,
          validationStatus,
          yamlDraft,
          yamlLoading,
          yamlMessage
      ]
  );
    const topBarStatus = useMemo(
        () => getTopBarStatus({
            isValid,
            novelText,
            previewLoading,
            taskStatus: conversionTask.status,
            yamlDraft,
            yamlLoading
        }),
        [conversionTask.status, isValid, novelText, previewLoading, yamlDraft, yamlLoading]
    );

  useEffect(() => {
    const token = authSession?.token;
    if (!token) {
      setAuthChecking(false);
      return;
    }
    const activeToken = token;

    let ignored = false;
    async function refreshCurrentUser() {
      setAuthChecking(true);
      try {
        const data = await fetchCurrentUser(activeToken);
        if (ignored) {
          return;
        }
        const nextSession = { ...data, token: activeToken };
        setAuthSession(nextSession);
        writeStoredAuth(nextSession);
        setAuthError("");
        await loadProjects(activeToken);
      } catch (error) {
        if (!ignored) {
          clearAuthSession(error instanceof Error ? error.message : "登录状态已过期，请重新登录。");
        }
      } finally {
        if (!ignored) {
          setAuthChecking(false);
        }
      }
    }

    refreshCurrentUser();
    return () => {
      ignored = true;
    };
  }, [authSession?.token]);

  function saveAuthSession(session: AuthSession) {
    setAuthSession(session);
    writeStoredAuth(session);
  }

  function clearAuthSession(message = "") {
    setAuthSession(null);
    removeStoredAuth();
    setAuthError(message);
    setLoginUsername(defaultLoginUsername);
    setLoginPassword(defaultLoginPassword);
    setProjects([]);
    setProjectId("");
    setDraftId("");
    setDrafts([]);
      setConversionTask(createInitialTask());
  }

  async function requestWithAuth<T>(operation: (token?: string) => Promise<T>, tokenOverride?: string): Promise<T> {
    const token = tokenOverride || authSession?.token;
    try {
      return await operation(token);
    } catch (error) {
      if (isAuthExpiredError(error)) {
        clearAuthSession("登录状态已过期，请重新登录。");
      }
      throw error;
    }
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const username = loginUsername.trim();
    const password = loginPassword.trim();
    if (!username || !password) {
      setAuthError("用户名或密码不能为空。");
      return;
    }
    setLoginLoading(true);
    setAuthError("");
    try {
      const data = await loginWithPassword({ password, username });
      saveAuthSession(data);
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "登录失败。");
    } finally {
      setLoginLoading(false);
    }
  }

  async function logout() {
    const token = authSession?.token;
    if (token) {
      try {
        await logoutSession(token);
      } catch {
        // 本地清理优先，远端会话失败不阻塞退出。
      }
    }
    clearAuthSession("");
  }

  function handleNovelTextChange(value: string) {
    setNovelText(value);
    setProjectId("");
    setDraftId("");
    setDrafts([]);
    setActiveChapterIndex(1);
    if (value.trim()) {
      setIsValid(false);
      setChapterCount(0);
      setMessage("文本已更新，请重新解析章节。");
        setConversionTask({
            ...createInitialTask(),
            currentStage: "等待章节解析",
            message: "文本已更新，请先解析章节再开始 AI 转换。",
            progress: 8,
            status: "ready"
        });
    } else {
      setIsValid(false);
      setChapterCount(0);
      setMessage(initialChapterMessage);
        setConversionTask(createInitialTask());
    }
  }

  function handleYamlDraftChange(value: string) {
    setYamlDraft(value);
    setDraftId("");
    setValidationStatus(value.trim() ? "edited" : "pending");
    setValidationError("");
    setGenerationMode(extractGenerationMode(value));
    if (extractGenerationMode(value) !== "rule_based_fallback") {
      setFallbackReason("");
    }
    setYamlMessage(value.trim() ? "YAML 已进入可编辑状态，保存后会生成新的草稿记录。" : initialYamlMessage);
  }

  async function importNovelFile(file: File) {
    try {
      const text = await file.text();
      handleNovelTextChange(text);
      if (!title.trim()) {
        setTitle(file.name.replace(/\.(txt|md|markdown)$/i, ""));
      }
      setMessage(`已导入「${file.name}」，请解析章节。`);
    } catch {
      setIsValid(false);
      setMessage("文件读取失败，请确认文件是可访问的纯文本或 Markdown。");
    }
  }

  async function previewChapters() {
    if (!canPreview) return;
    setPreviewLoading(true);
      setActiveView("pipeline");
      setConversionTask((current) => ({
          ...current,
          currentStage: "章节解析",
          errorMessage: "",
          message: "正在识别章节标题、正文边界和章节数量。",
          progress: Math.max(current.progress, 12),
          status: "running"
      }));
    try {
      const data = await requestWithAuth((token) => previewNovelChapters(novelText, token));
      setIsValid(data.valid);
      setChapterCount(data.chapterCount);
      setMessage(data.message);
        setConversionTask((current) => ({
            ...current,
            currentStage: data.valid ? "可开始 AI 转换" : "章节解析未通过",
            errorMessage: data.valid ? "" : data.message,
            message: data.valid ? "章节解析通过，可以开始生成剧本 YAML。" : data.message,
            progress: data.valid ? 20 : 14,
            status: data.valid ? "ready" : "failed"
        }));
    } catch (error) {
        const errorMessage = error instanceof Error ? error.message : "章节解析失败。";
      setIsValid(false);
        setMessage(errorMessage);
        setConversionTask((current) => ({
            ...current,
            currentStage: "章节解析失败",
            errorMessage,
            message: "请检查章节格式后重试。",
            progress: Math.max(current.progress, 10),
            status: "failed"
        }));
    } finally {
      setPreviewLoading(false);
    }
  }

  async function generateYaml() {
    if (!canGenerate) return;
      const resumeTaskId =
          conversionTask.status === "failed" && isBackendTaskId(conversionTask.taskId)
              ? conversionTask.taskId
              : "";
      const controller = new AbortController();
      const taskId = createLocalTaskId();
      let streamTaskId = resumeTaskId || taskId;
      generationAbortRef.current = controller;
      setActiveView("pipeline");
    setYamlLoading(true);
    setValidationStatus("generating");
    setYamlMessage("正在生成 YAML 初稿...");
      setConversionTask({
          currentStage: "AI Pipeline 编排",
          errorMessage: "",
          message: resumeTaskId ? "正在从失败任务的安全阶段继续。" : "流式生成已提交，正在等待后端 meta / progress 事件。",
          progress: 4,
          status: "running",
          taskId: streamTaskId
      });

      const updateTaskFromStream = (payload: ScriptYamlStreamPayload, status: TaskStatus = "running") => {
          if (payload.taskId) {
              streamTaskId = payload.taskId;
          }
          setConversionTask((current) => ({
              ...current,
              currentStage: pipelineStageLabel(payload.stage) || current.currentStage,
              errorMessage: status === "failed" ? payload.message || current.errorMessage : "",
              message: payload.message || current.message,
              progress: nextTaskProgress(payload.progress, current.progress),
              status,
              taskId: payload.taskId || current.taskId || streamTaskId
          }));
      };

    try {
        const activeProjectId = await ensureProject();
      const data = await requestWithAuth((token) =>
        streamGenerateScriptYaml(
          {
            author,
            forceNewTask: !resumeTaskId,
            logline,
            projectId: activeProjectId,
            resumeTaskId: resumeTaskId || undefined,
            text: novelText,
            title
          },
            {
                onCancel: (payload) => {
                    updateTaskFromStream(payload, "canceled");
                    setValidationStatus("pending");
                    setYamlMessage(payload.message || "生成已停止，未写入新的 YAML 草稿。");
                },
                onError: (payload) => {
                    updateTaskFromStream(payload, "failed");
                    setYamlMessage(payload.message || "YAML 生成失败。");
                },
                onFinish: (payload) => {
                    updateTaskFromStream(payload, "success");
                },
                onMessage: (payload) => {
                    updateTaskFromStream(payload);
                    if (payload.message) {
                        setYamlMessage(payload.message);
                    }
                },
                onMeta: updateTaskFromStream,
                onProgress: (payload) => {
                    updateTaskFromStream(payload);
                    if (payload.message) {
                        setYamlMessage(payload.message);
                    }
                },
                onValidation: (payload) => {
                    updateTaskFromStream(payload);
                    if (payload.validationStatus) {
                        setValidationStatus(payload.validationStatus);
                        setValidationError(payload.validationError || "");
                        setYamlMessage(
                            payload.validationStatus === "valid"
                                ? "Schema 校验通过，等待完成事件。"
                                : payload.validationError || "Schema 校验未通过，等待自动修复或兜底结果。"
                        );
                    }
                }
            },
            token,
            controller.signal
        )
      );
      const nextGenerationMode = data.generationMode || extractGenerationMode(data.yaml);
        const nextYamlMessage = createGenerationMessage(nextGenerationMode, Boolean(data.fallback), data.fallbackReason || "");
      setYamlDraft(data.yaml);
      setSchemaVersion(data.schemaVersion);
      setGeneratedTitle(data.title);
      setDraftId("");
      setGenerationMode(nextGenerationMode);
      setFallbackReason(data.fallbackReason || "");
      setValidationError(data.validationError || "");
      setChapterCount(data.chapterCount);
      setIsValid(true);
      setMessage(`已识别 ${data.chapterCount} 个章节。`);
        setYamlMessage(nextYamlMessage);
      setValidationStatus(data.validationStatus || "generated");
        setConversionTask({
            currentStage: data.validationStatus === "valid" ? "Schema 校验通过" : "YAML 已生成",
            errorMessage: data.validationError || "",
            message: nextYamlMessage,
            progress: data.validationStatus === "valid" ? 100 : 96,
            status: data.validationStatus === "invalid" ? "failed" : "success",
            taskId: streamTaskId
        });
        setActiveView("result");
    } catch (error) {
        const aborted = isAbortError(error);
        const errorMessage = aborted ? "生成已停止，未写入新的 YAML 草稿。" : error instanceof Error ? error.message : "YAML 生成失败。";
        setValidationStatus(aborted ? "pending" : "error");
        setYamlMessage(errorMessage);
        setConversionTask((current) => ({
            ...current,
            currentStage: aborted ? "已停止" : "生成失败",
            errorMessage: aborted ? "" : errorMessage,
            message: aborted ? errorMessage : "请检查模型配置、网络或输入文本后重试。",
            progress: aborted ? Math.max(current.progress, 20) : Math.max(current.progress, 42),
            status: aborted ? "canceled" : "failed",
            taskId: streamTaskId
        }));
        setActiveView(aborted ? "source" : "pipeline");
    } finally {
        generationAbortRef.current = null;
      setYamlLoading(false);
    }
  }

    async function stopGeneration() {
        if (!generationAbortRef.current) return;
        const controller = generationAbortRef.current;
        const taskId = conversionTask.taskId;
        setYamlMessage("正在停止生成请求...");
        setConversionTask((current) => ({
            ...current,
            currentStage: "停止中",
            errorMessage: "",
            message: isBackendTaskId(taskId) ? "已发送后端取消请求，正在释放生成任务。" : "正在释放前端生成连接。",
            progress: Math.max(current.progress, 20),
            status: "canceled"
        }));
        try {
            if (isBackendTaskId(taskId)) {
                const data = await requestWithAuth((token) => cancelScriptGenerationTask(taskId, token));
                setConversionTask((current) => ({
                    ...current,
                    currentStage: pipelineStageLabel(extractTaskStage(data.message)) || "已停止",
                    errorMessage: "",
                    message: data.message || "任务已取消，后续阶段结果会被忽略。",
                    progress: nextTaskProgress(data.progress, current.progress),
                    status: "canceled",
                    taskId: data.taskId
                }));
            }
        } catch (error) {
            setYamlMessage(error instanceof Error ? error.message : "停止生成失败，已中止前端连接。");
        } finally {
            controller.abort();
        }
    }

  async function copyYaml() {
    if (!yamlDraft) return;
    try {
      await navigator.clipboard.writeText(yamlDraft);
      setYamlMessage("YAML 已复制到剪贴板。");
    } catch {
      setYamlMessage("复制失败，请手动选中 YAML 内容复制。");
    }
  }

  async function ensureProject() {
    if (projectId) {
      return projectId;
    }
    const data = await requestWithAuth((token) =>
      createProjectFromText(
        {
          description: logline,
          text: novelText,
          title: generatedTitle || title
        },
        token
      )
    );
    setProjectId(data.projectId);
    setGeneratedTitle((current) => current || data.title);
    setChapterCount(data.chapterCount);
    setProjects((current) => [
      {
        projectId: data.projectId,
        status: "draft",
        title: data.title
      },
      ...current.filter((project) => project.projectId !== data.projectId)
    ]);
    return data.projectId;
  }

  async function loadProjects(tokenOverride?: string) {
    setProjectLoading(true);
    try {
      const data = await requestWithAuth((token) => listMyProjects(token), tokenOverride);
      setProjects(data);
      if (data.length === 0) {
        setProjectId("");
        setDraftId("");
        setDrafts([]);
          setConversionTask(createInitialTask());
        return;
      }
      const nextProject = projectId ? data.find((project) => project.projectId === projectId) || data[0] : data[0];
      setProjectId(nextProject.projectId);
      setGeneratedTitle(nextProject.title);
      await loadDrafts(nextProject.projectId, tokenOverride);
    } catch (error) {
      setYamlMessage(error instanceof Error ? error.message : "项目列表加载失败。");
    } finally {
      setProjectLoading(false);
    }
  }

  async function selectProject(nextProjectId: string) {
    const project = projects.find((item) => item.projectId === nextProjectId);
    setProjectId(nextProjectId);
    setGeneratedTitle(project?.title || "");
    setDraftId("");
    setYamlDraft("");
    setValidationStatus("pending");
    setValidationError("");
    setGenerationMode("");
    setFallbackReason("");
    setYamlMessage(initialYamlMessage);
      setConversionTask({
          ...createInitialTask(),
          currentStage: "已切换项目",
          message: "已加载项目上下文，可选择历史草稿或重新生成。",
          progress: 18,
          status: "ready",
          taskId: nextProjectId
      });
      setActiveView("archive");
    await loadDrafts(nextProjectId);
  }

  async function loadDrafts(nextProjectId = projectId, tokenOverride?: string) {
    if (!nextProjectId) return;
    setDraftLoading(true);
    try {
      const data = await requestWithAuth((token) => listProjectDrafts(nextProjectId, token), tokenOverride);
      setDrafts(data);
    } catch (error) {
      setYamlMessage(error instanceof Error ? error.message : "草稿列表加载失败。");
    } finally {
      setDraftLoading(false);
    }
  }

  async function saveDraft() {
    if (!canSave) return;
    setSaveLoading(true);
    setYamlMessage("正在保存 YAML 草稿...");
    try {
      const activeProjectId = await ensureProject();
      const data = await requestWithAuth((token) =>
        saveScriptDraft(
          {
            projectId: activeProjectId,
            schemaVersion: schemaVersion || "1.0",
            title: generatedTitle || title || "未命名剧本",
            yaml: yamlDraft
          },
          token
        )
      );
      setDraftId(data.draftId);
      setProjectId(data.projectId);
      setGeneratedTitle(data.title);
      setSchemaVersion(data.schemaVersion);
      setValidationStatus(data.validationStatus);
      setValidationError(data.validationError || "");
      setYamlMessage(
        data.validationStatus === "valid"
          ? "草稿已保存，YAML 校验通过。"
          : `草稿已保存，校验未通过：${data.validationError || "请查看 Schema 校验面板。"}`
      );
        setConversionTask((current) => ({
            ...current,
            currentStage: data.validationStatus === "valid" ? "草稿已归档" : "草稿已保存但需修正",
            errorMessage: data.validationError || "",
            message:
                data.validationStatus === "valid"
                    ? "生成、校验、保存和导出链路已闭环。"
                    : "草稿已入库，请根据 Schema 校验信息修正。",
            progress: data.validationStatus === "valid" ? 100 : 92,
            status: data.validationStatus === "valid" ? "success" : "failed",
            taskId: current.taskId || data.projectId
        }));
        setActiveView("archive");
      await loadDrafts(data.projectId);
    } catch (error) {
        const errorMessage = error instanceof Error ? error.message : "草稿保存失败。";
        setYamlMessage(errorMessage);
        setConversionTask((current) => ({
            ...current,
            currentStage: "草稿保存失败",
            errorMessage,
            message: "YAML 仍保留在编辑器中，可修正后再次保存。",
            progress: Math.max(current.progress, 84),
            status: "failed"
        }));
    } finally {
      setSaveLoading(false);
    }
  }

  async function loadDraftDetail(nextDraftId: string) {
    if (!nextDraftId) return;
    setDraftLoading(true);
    try {
      const data = await requestWithAuth((token) => getDraftDetail(nextDraftId, token));
      setYamlDraft(data.yaml);
      setDraftId(data.draftId);
      setProjectId(data.projectId);
      setGeneratedTitle(data.title);
      setSchemaVersion(data.schemaVersion);
      setGenerationMode(extractGenerationMode(data.yaml));
      setFallbackReason("");
      setValidationStatus(data.validationStatus);
      setValidationError(data.validationError || "");
      setYamlMessage(
        data.validationStatus === "valid"
          ? "已加载草稿，YAML 校验通过。"
          : `已加载草稿，状态：${data.validationStatus}。`
      );
        setConversionTask({
            currentStage: "已加载历史草稿",
            errorMessage: data.validationError || "",
            message: "可继续编辑、保存或导出当前 YAML。",
            progress: data.validationStatus === "valid" ? 100 : 88,
            status: data.validationStatus === "valid" ? "success" : "ready",
            taskId: data.draftId
        });
        setActiveView("result");
    } catch (error) {
      setYamlMessage(error instanceof Error ? error.message : "草稿加载失败。");
    } finally {
      setDraftLoading(false);
    }
  }

  async function exportDraftYaml() {
    if (!canExport) return;
    setExportLoading(true);
    setYamlMessage("正在导出 YAML 文件...");
    try {
      const exported = await requestWithAuth((token) =>
        exportDraftYamlFile(draftId, `${generatedTitle || title || "script-draft"}-${draftId}.yaml`, token)
      );
      const url = URL.createObjectURL(exported.blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = exported.filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setYamlMessage("YAML 文件已导出。");
    } catch (error) {
      setYamlMessage(error instanceof Error ? error.message : "YAML 导出失败。");
    } finally {
      setExportLoading(false);
    }
  }

  if (!authSession) {
    return (
      <LoginGate
        authChecking={authChecking}
        error={authError}
        loading={loginLoading}
        onPasswordChange={setLoginPassword}
        onSubmit={handleLogin}
        onUsernameChange={setLoginUsername}
        password={loginPassword}
        username={loginUsername}
      />
    );
  }

    const scriptPanel = (
        <ScriptPreviewPanel
            activeDraftId={draftId}
            canExport={canExport}
            canRefreshDrafts={canRefreshDrafts}
            canSave={canSave}
            taskSummary={taskSummary}
            draftId={draftId}
            draftLoading={draftLoading}
            drafts={drafts}
            generatedTitle={generatedTitle}
            fallbackReason={fallbackReason}
            generationMode={generationMode}
            onExportYaml={exportDraftYaml}
            onCopyYaml={copyYaml}
            onLoadDraft={loadDraftDetail}
            onRefreshDrafts={() => loadDrafts()}
            onSaveDraft={saveDraft}
            onYamlChange={handleYamlDraftChange}
            projectId={projectId}
            saveLoading={saveLoading}
            schemaChecks={schemaChecks}
            schemaVersion={schemaVersion}
            exportLoading={exportLoading}
            validationError={validationError}
            validationStatus={validationStatus}
            yaml={yamlDraft}
            yamlMessage={yamlMessage}
            yamlPlaceholder={placeholderYaml}
        />
    );

    return (
        <AppShell
            activeView={activeView}
            onNavigate={setActiveView}
            onToggleSidebar={() => setSidebarCollapsed((current) => !current)}
            sidebarCollapsed={sidebarCollapsed}
            sidebar={
                <Sidebar
                    author={author}
                    chapterCount={visibleChapterCount}
                    isValid={isValid}
                    projectId={projectId}
                    title={generatedTitle || title}
                >
                    <TaskConsole onCancel={stopGeneration} summary={taskSummary}/>
                    <ConversionProgress steps={workflowSteps}/>
                </Sidebar>
      }
      topBar={
        <TopBar
          actions={
            <>
              <ActionButton
                disabled={!canPreview}
                icon={previewLoading ? <Loader2 aria-hidden="true" className="spin" size={16} /> : <Play aria-hidden="true" size={16} />}
                onClick={previewChapters}
                variant="secondary"
              >
                解析章节
              </ActionButton>
              <ActionButton
                disabled={!canGenerate}
                icon={yamlLoading ? <Loader2 aria-hidden="true" className="spin" size={16} /> : <Wand2 aria-hidden="true" size={16} />}
                onClick={generateYaml}
                variant="primary"
              >
                  开始生成
              </ActionButton>
                <ActionButton
                    disabled={!taskSummary.canCancel}
                    icon={<CircleStop aria-hidden="true" size={16}/>}
                    onClick={stopGeneration}
                    variant="ghost"
                >
                    停止生成
              </ActionButton>
              <ActionButton
                disabled={!canSave}
                icon={saveLoading ? <Loader2 aria-hidden="true" className="spin" size={16} /> : <Save aria-hidden="true" size={16} />}
                onClick={saveDraft}
                variant="quiet"
              >
                保存草稿
              </ActionButton>
              <span className="user-chip" title={authSession.username}>
                {authSession.avatar ? <img alt="" src={authSession.avatar} /> : <UserCircle aria-hidden="true" size={16} />}
                <span>{authSession.username}</span>
                <small>{authSession.role}</small>
              </span>
              <ActionButton icon={<LogOut aria-hidden="true" size={16} />} onClick={logout} variant="ghost">
                退出
              </ActionButton>
            </>
          }
          status={<StatusBadge label={authChecking ? "校验登录" : topBarStatus.label} tone={authChecking ? "active" : topBarStatus.tone} />}
        />
      }
    >
            {activeView === "source" ? (
                <div className="page-split page-split--source">
                    <InputPanel
                        author={author}
                        chapterCount={visibleChapterCount}
                        characterCount={characterCount}
                        isValid={isValid}
                        locked={yamlLoading}
                        logline={logline}
                        message={message}
                        novelText={novelText}
                        onAuthorChange={setAuthor}
                        onFileImport={importNovelFile}
                        onLoglineChange={setLogline}
                        onNovelTextChange={handleNovelTextChange}
                        onTitleChange={setTitle}
                        selectedChapter={selectedChapter}
                        title={title}
                    />
                    <div className="studio-panel page-side-panel">
                        <ChapterList
                            activeChapterIndex={selectedChapter?.index || activeChapterIndex}
                            chapterCount={visibleChapterCount}
                            chapters={chapterOutline}
                            onSelectChapter={setActiveChapterIndex}
                        />
                    </div>
                </div>
            ) : null}

            {activeView === "pipeline" ? (
                <div className="page-grid page-grid--pipeline">
                    <div className="studio-panel page-side-panel">
                        <TaskConsole onCancel={stopGeneration} summary={taskSummary}/>
                    </div>
                    <div className="studio-panel page-side-panel">
                        <ConversionProgress steps={workflowSteps}/>
                    </div>
                    <section className="studio-panel task-details" aria-label="后续任务能力">
                        <div className="panel-heading">
                            <div>
                                <p className="panel-kicker">Task Runtime</p>
                                <h2>任务化生成准备区</h2>
                            </div>
                            <StatusBadge label={taskSummary.currentStage}
                                         tone={taskSummary.status === "failed" ? "danger" : "info"}/>
                        </div>
                        <div className="task-details__body">
                            <p>{taskSummary.errorMessage || taskSummary.message}</p>
                            <div className="task-detail-list">
                                <span>此页实时显示后端 `meta / progress / validation / finish / error` 事件。</span>
                                <span>停止生成当前会中止前端流式连接，后端任务取消会在下一项接入。</span>
                                <span>阶段结果暂存、失败恢复和超长文本分块会继续挂在此页。</span>
                            </div>
                        </div>
                    </section>
                </div>
            ) : null}

            {activeView === "result" ? scriptPanel : null}

            {activeView === "archive" ? (
                <div className="archive-grid">
                    <section className="studio-panel archive-panel" aria-label="我的项目">
                        <div className="panel-heading">
                            <div>
                                <p className="panel-kicker">Project Library</p>
                                <h2>我的项目</h2>
                            </div>
                            <ActionButton
                                disabled={projectLoading}
                                icon={<RefreshCcw aria-hidden="true" className={projectLoading ? "spin" : ""}
                                                  size={15}/>}
                                onClick={() => loadProjects()}
                                size="sm"
                                variant="secondary"
                            >
                                刷新
                            </ActionButton>
                        </div>
                        <div className="archive-list">
                            {projects.length > 0 ? (
                                projects.map((project) => (
                                    <button
                                        className="archive-item"
                                        data-active={project.projectId === projectId}
                                        key={project.projectId}
                                        onClick={() => selectProject(project.projectId)}
                                        type="button"
                                    >
                                        <FolderOpen aria-hidden="true" size={18}/>
                                        <span>
                      <strong>{project.title}</strong>
                      <small>{project.status || "draft"}</small>
                    </span>
                                    </button>
                                ))
                            ) : (
                                <EmptyArchiveState icon={<FolderOpen aria-hidden="true" size={22}/>} title="暂无项目"
                                                   text="保存草稿后会形成项目入口。"/>
                            )}
                        </div>
                    </section>
                    <section className="studio-panel archive-panel" aria-label="草稿记录">
                        <div className="panel-heading">
                            <div>
                                <p className="panel-kicker">Draft History</p>
                                <h2>保存记录</h2>
                            </div>
                            <ActionButton
                                disabled={!canRefreshDrafts}
                                icon={<RefreshCcw aria-hidden="true" className={draftLoading ? "spin" : ""} size={15}/>}
                                onClick={() => loadDrafts()}
                                size="sm"
                                variant="secondary"
                            >
                                刷新
                            </ActionButton>
                        </div>
                        <div className="archive-list">
                            {drafts.length > 0 ? (
                                drafts.map((draft) => (
                                    <button
                                        className="archive-item"
                                        data-active={draft.draftId === draftId}
                                        key={draft.draftId}
                                        onClick={() => loadDraftDetail(draft.draftId)}
                                        type="button"
                                    >
                                        <History aria-hidden="true" size={18}/>
                                        <span>
                      <strong>{draft.title}</strong>
                      <small>
                        {draft.schemaVersion} / {draft.validationStatus}
                      </small>
                    </span>
                                    </button>
                                ))
                            ) : (
                                <EmptyArchiveState icon={<History aria-hidden="true" size={22}/>} title="暂无保存记录"
                                                   text="保存后可在此重新加载草稿版本。"/>
                            )}
                        </div>
                    </section>
                </div>
            ) : null}
    </AppShell>
  );
}

function EmptyArchiveState({icon, text, title}: { icon: ReactNode; text: string; title: string }) {
    return (
        <div className="archive-empty">
            <span>{icon}</span>
            <strong>{title}</strong>
            <p>{text}</p>
        </div>
    );
}

type LoginGateProps = {
  authChecking: boolean;
  error: string;
  loading: boolean;
  onPasswordChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUsernameChange: (value: string) => void;
  password: string;
  username: string;
};

function LoginGate({
  authChecking,
  error,
  loading,
  onPasswordChange,
  onSubmit,
  onUsernameChange,
  password,
  username
}: LoginGateProps) {
  return (
    <main className="app-shell auth-shell">
      <section className="login-frame" aria-labelledby="login-title">
        <div className="login-brand">
          <span className="product-mark" aria-hidden="true">
            <Wand2 size={18} />
          </span>
          <div>
            <p className="top-bar__eyebrow">Act One</p>
            <h1 id="login-title">AI 小说转剧本工作台</h1>
          </div>
        </div>
        <form className="login-form" onSubmit={onSubmit}>
          <label className="field-control">
            <span>账号</span>
            <input
              autoComplete="username"
              disabled={loading || authChecking}
              maxLength={64}
              onChange={(event) => onUsernameChange(event.target.value)}
              placeholder="admin"
              value={username}
            />
          </label>
          <label className="field-control">
            <span>密码</span>
            <input
              autoComplete="current-password"
              disabled={loading || authChecking}
              maxLength={128}
              onChange={(event) => onPasswordChange(event.target.value)}
              placeholder={defaultLoginPassword}
              type="password"
              value={password}
            />
          </label>
          {error ? <p className="auth-message">{error}</p> : null}
          <ActionButton
            className="login-submit"
            disabled={loading || authChecking}
            icon={loading || authChecking ? <Loader2 aria-hidden="true" className="spin" size={16} /> : <LogIn aria-hidden="true" size={16} />}
            type="submit"
            variant="primary"
          >
            {authChecking ? "校验中" : "登录"}
          </ActionButton>
        </form>
      </section>
    </main>
  );
}

function parseChapterOutline(text: string): ChapterOutline[] {
  const matches = Array.from(text.matchAll(chapterHeadingPattern));
  if (matches.length === 0) {
    return [];
  }

  return matches.map((match, index) => {
    const nextMatch = matches[index + 1];
    const title = match[0].trim();
    const contentStart = (match.index || 0) + match[0].length;
    const contentEnd = nextMatch?.index ?? text.length;
    const content = text.slice(contentStart, contentEnd).trim();
    const normalizedExcerpt = content.replace(/\s+/g, " ").slice(0, 160);

    return {
      excerpt: normalizedExcerpt,
      id: `chapter-${index + 1}`,
      index: index + 1,
      title,
      wordCount: countReadableCharacters(content)
    };
  });
}

function countReadableCharacters(value: string) {
  return value.replace(/\s+/g, "").length;
}

function createSchemaChecks(yaml: string, validationStatus: string, validationError: string): ValidationCheck[] {
  const source = yaml.trim();
  const pending = source.length === 0;
  const checked = validationStatus === "valid" || validationStatus === "invalid";

  return [
    {
      detail: pending ? "等待生成 YAML" : checked ? (validationError || "后端校验通过") : "保存后执行后端校验",
      label: "后端校验",
      state: pending ? "pending" : validationStatus === "valid" ? "pass" : validationStatus === "invalid" ? "fail" : "pending"
    },
    {
      detail: pending ? "等待生成 YAML" : "schema_version: 1.0",
      label: "版本字段",
      state: pending ? "pending" : /schema_version:\s*["']?1\.0["']?/i.test(source) ? "pass" : "fail"
    },
    {
      detail: pending ? "等待生成 YAML" : "kind: screenplay_draft",
      label: "内容类型",
      state: pending ? "pending" : /kind:\s*["']?screenplay_draft["']?/i.test(source) ? "pass" : "fail"
    },
    {
      detail: pending ? "等待生成 YAML" : "metadata / source / acts",
      label: "顶层结构",
      state:
        pending
          ? "pending"
          : /metadata:\s*\n/i.test(source) && /source:\s*\n/i.test(source) && /acts:\s*\n/i.test(source)
            ? "pass"
            : "fail"
    },
    {
      detail: pending ? "等待生成 YAML" : "scenes 数组可继续打磨",
      label: "场景承载",
      state: pending ? "pending" : /scenes:\s*\n\s*-\s+id:/i.test(source) ? "pass" : "fail"
    }
  ];
}

function extractGenerationMode(yaml: string) {
  const match = yaml.match(/generation_mode:\s*["']?([a-z_]+)["']?/i);
  return match?.[1] || "";
}

function createGenerationMessage(generationMode: string, fallback: boolean, fallbackReason: string) {
  const label = generationModeLabel(generationMode);
  if (fallback) {
    return fallbackReason
      ? `${label} 已生成：${fallbackReason}`
      : `${label} 已生成：AI Pipeline 不可用，已显式降级。`;
  }
  return `${label} 生成成功，可继续复制或编辑。`;
}

function generationModeLabel(generationMode: string) {
  if (generationMode === "ai_pipeline") {
    return "AI Pipeline";
  }
  if (generationMode === "rule_based_fallback") {
    return "规则兜底";
  }
  if (generationMode === "rule_based_scaffold") {
    return "规则脚手架";
  }
  return "生成模式";
}

function createInitialTask(): ConversionTaskState {
    return {
        currentStage: "等待输入",
        errorMessage: "",
        message: "导入或粘贴小说正文后开始转换。",
        progress: 0,
        status: "idle",
        taskId: ""
    };
}

function createLocalTaskId() {
    return `local-${Date.now().toString(36)}`;
}

function isBackendTaskId(taskId: string) {
    return Boolean(taskId) && !taskId.startsWith("local-") && !taskId.startsWith("actone-");
}

function extractTaskStage(message?: string | null) {
    return message?.match(/stage=([A-Z_]+)/)?.[1] || "";
}

const pipelineStageLabels: Record<string, string> = {
    CHAPTER_SUMMARY: "章节摘要",
    COMPLETED: "转换完成",
    CONSISTENCY_CHECK: "一致性校验",
    DIALOGUE_GENERATION: "对白生成",
    GLOBAL_STORY_EXTRACTION: "全局故事抽取",
    INIT: "任务初始化",
    PSYCHOLOGICAL_VISUALIZATION: "心理影视化",
    SCENE_PLANNING: "场景规划",
    SCRIPT_YAML_GENERATION: "YAML 生成",
    SOURCE_TRACE: "源文追溯",
    YAML_REPAIR: "YAML 自动修复",
    YAML_VALIDATION: "Schema 校验"
};

function pipelineStageLabel(stage?: string | null) {
    if (!stage) {
        return "";
    }
    return pipelineStageLabels[stage] || stage;
}

function nextTaskProgress(value: number | null | undefined, current: number) {
    if (typeof value !== "number" || !Number.isFinite(value)) {
        return current;
    }
    const normalized = Math.max(0, Math.min(100, Math.round(value)));
    return Math.max(current, normalized);
}

function isAbortError(error: unknown) {
    return error instanceof DOMException && error.name === "AbortError" || error instanceof Error && error.name === "AbortError";
}

function createWorkflowSteps({
  characterCount,
                                 conversionTask,
  draftId,
  isValid,
  message,
  previewLoading,
  saveLoading,
                                 validationStatus,
  yamlDraft,
  yamlLoading,
  yamlMessage
}: {
  characterCount: number;
    conversionTask: ConversionTaskState;
  draftId: string;
  isValid: boolean;
  message: string;
  previewLoading: boolean;
  saveLoading: boolean;
    validationStatus: string;
  yamlDraft: string;
  yamlLoading: boolean;
  yamlMessage: string;
}): ProgressStep[] {
    const hasYaml = yamlDraft.trim().length > 0;
    const taskFailed = conversionTask.status === "failed";
    const taskCanceled = conversionTask.status === "canceled";

  return [
    {
      detail: characterCount > 0 ? `${characterCount} 字符已输入` : "等待小说文本",
      label: "小说输入",
      status: characterCount > 0 ? "complete" : "active"
    },
    {
      detail: previewLoading ? "解析服务处理中" : message,
      label: "章节解析",
      status: previewLoading ? "active" : isValid ? "complete" : characterCount > 0 ? "warning" : "idle"
    },
    {
        detail: yamlLoading ? conversionTask.message : hasYaml ? "摘要、抽取、规划和改写已返回 YAML" : "等待开始 AI Pipeline",
        label: "AI 编排",
        status: yamlLoading ? "active" : hasYaml ? "complete" : taskFailed ? "error" : taskCanceled ? "warning" : "idle"
    },
      {
          detail: hasYaml ? yamlMessage : "等待 YAML 生成",
          label: "校验修复",
          status:
              validationStatus === "valid"
                  ? "complete"
                  : validationStatus === "invalid" || validationStatus === "error"
                      ? "error"
                      : hasYaml
                          ? "warning"
                          : "idle"
    },
    {
      detail: draftId ? `已保存 ${draftId}` : "等待保存",
      label: "草稿归档",
      status: saveLoading ? "active" : draftId ? "complete" : "idle"
    }
  ];
}

function getTopBarStatus({
  isValid,
  novelText,
  previewLoading,
                             taskStatus,
  yamlDraft,
  yamlLoading
}: {
  isValid: boolean;
  novelText: string;
  previewLoading: boolean;
    taskStatus: TaskStatus;
  yamlDraft: string;
  yamlLoading: boolean;
}): { label: string; tone: StatusTone } {
  if (yamlLoading) {
    return { label: "正在转换", tone: "active" };
  }
    if (taskStatus === "failed") {
        return {label: "任务失败", tone: "danger"};
    }
    if (taskStatus === "canceled") {
        return {label: "已停止", tone: "warning"};
    }
  if (yamlDraft.trim()) {
    return { label: "YAML 已生成", tone: "success" };
  }
  if (previewLoading) {
    return { label: "正在解析", tone: "active" };
  }
  if (isValid) {
    return { label: "章节可转换", tone: "success" };
  }
  if (novelText.trim()) {
    return { label: "待解析", tone: "warning" };
  }
  return { label: "待输入", tone: "neutral" };
}

export default App;
