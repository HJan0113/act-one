import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Loader2, LogIn, LogOut, Play, Save, UserCircle, Wand2 } from "lucide-react";

import { ActionButton } from "./components/ActionButton";
import { StatusBadge, type StatusTone } from "./components/StatusBadge";
import {
  AppShell,
  ChapterList,
  ConversionProgress,
  InputPanel,
  ProjectList,
  ScriptPreviewPanel,
  Sidebar,
  TopBar,
  type ChapterOutline,
  type ProgressStep,
  type ValidationCheck
} from "./components/workspace";
import { isAuthExpiredError } from "./services/apiClient";
import { fetchCurrentUser, login as loginWithPassword, logout as logoutSession } from "./services/authService";
import { removeStoredAuth, readStoredAuth, writeStoredAuth } from "./services/authStorage";
import {
  createProjectFromText,
  exportDraftYaml as exportDraftYamlFile,
  generateYaml as generateScriptYaml,
  getDraftDetail,
  listDrafts as listProjectDrafts,
  listMyProjects,
  previewChapters as previewNovelChapters,
  saveDraft as saveScriptDraft
} from "./services/scriptService";
import type { AuthSession, ScriptDraftListItem, ScriptProjectListItem } from "./types/api";

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
  const workflowSteps = useMemo(
    () =>
      createWorkflowSteps({
        characterCount,
        draftId,
        isValid,
        message,
        previewLoading,
        saveLoading,
        yamlDraft,
        yamlLoading,
        yamlMessage
      }),
    [characterCount, draftId, isValid, message, previewLoading, saveLoading, yamlDraft, yamlLoading, yamlMessage]
  );
  const topBarStatus = useMemo(() => getTopBarStatus({ isValid, novelText, previewLoading, yamlDraft, yamlLoading }), [
    isValid,
    novelText,
    previewLoading,
    yamlDraft,
    yamlLoading
  ]);

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
    } else {
      setIsValid(false);
      setChapterCount(0);
      setMessage(initialChapterMessage);
    }
  }

  function handleYamlDraftChange(value: string) {
    setYamlDraft(value);
    setDraftId("");
    setValidationStatus(value.trim() ? "edited" : "pending");
    setValidationError("");
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
    try {
      const data = await requestWithAuth((token) => previewNovelChapters(novelText, token));
      setIsValid(data.valid);
      setChapterCount(data.chapterCount);
      setMessage(data.message);
    } catch (error) {
      setIsValid(false);
      setMessage(error instanceof Error ? error.message : "章节解析失败。");
    } finally {
      setPreviewLoading(false);
    }
  }

  async function generateYaml() {
    if (!canGenerate) return;
    setYamlLoading(true);
    setValidationStatus("generating");
    setYamlMessage("正在生成 YAML 初稿...");
    try {
      const data = await requestWithAuth((token) =>
        generateScriptYaml(
          {
            author,
            logline,
            text: novelText,
            title
          },
          token
        )
      );
      setYamlDraft(data.yaml);
      setSchemaVersion(data.schemaVersion);
      setGeneratedTitle(data.title);
      setDraftId("");
      setValidationError("");
      setChapterCount(data.chapterCount);
      setIsValid(true);
      setMessage(`已识别 ${data.chapterCount} 个章节。`);
      setYamlMessage("YAML 初稿生成成功，可继续复制或编辑。");
      setValidationStatus("generated");
    } catch (error) {
      setValidationStatus("error");
      setYamlMessage(error instanceof Error ? error.message : "YAML 生成失败。");
    } finally {
      setYamlLoading(false);
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
    setYamlMessage(initialYamlMessage);
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
      await loadDrafts(data.projectId);
    } catch (error) {
      setYamlMessage(error instanceof Error ? error.message : "草稿保存失败。");
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
      setValidationStatus(data.validationStatus);
      setValidationError(data.validationError || "");
      setYamlMessage(
        data.validationStatus === "valid"
          ? "已加载草稿，YAML 校验通过。"
          : `已加载草稿，状态：${data.validationStatus}。`
      );
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

  return (
    <AppShell
      sidebar={
        <Sidebar
          author={author}
          chapterCount={visibleChapterCount}
          isValid={isValid}
          projectId={projectId}
          title={generatedTitle || title}
        >
          <ChapterList
            activeChapterIndex={selectedChapter?.index || activeChapterIndex}
            chapterCount={visibleChapterCount}
            chapters={chapterOutline}
            onSelectChapter={setActiveChapterIndex}
          />
          <ProjectList
            activeProjectId={projectId}
            loading={projectLoading}
            onRefresh={() => loadProjects()}
            onSelectProject={selectProject}
            projects={projects}
          />
          <ConversionProgress steps={workflowSteps} />
        </Sidebar>
      }
      scriptPanel={
        <ScriptPreviewPanel
          activeDraftId={draftId}
          canExport={canExport}
          canRefreshDrafts={canRefreshDrafts}
          canSave={canSave}
          draftId={draftId}
          draftLoading={draftLoading}
          drafts={drafts}
          generatedTitle={generatedTitle}
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
                生成 YAML
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
      <InputPanel
        author={author}
        chapterCount={visibleChapterCount}
        characterCount={characterCount}
        isValid={isValid}
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
    </AppShell>
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

function createWorkflowSteps({
  characterCount,
  draftId,
  isValid,
  message,
  previewLoading,
  saveLoading,
  yamlDraft,
  yamlLoading,
  yamlMessage
}: {
  characterCount: number;
  draftId: string;
  isValid: boolean;
  message: string;
  previewLoading: boolean;
  saveLoading: boolean;
  yamlDraft: string;
  yamlLoading: boolean;
  yamlMessage: string;
}): ProgressStep[] {
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
      detail: yamlLoading ? "结构化生成中" : yamlMessage,
      label: "YAML 初稿",
      status: yamlLoading ? "active" : yamlDraft.trim() ? "complete" : "idle"
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
  yamlDraft,
  yamlLoading
}: {
  isValid: boolean;
  novelText: string;
  previewLoading: boolean;
  yamlDraft: string;
  yamlLoading: boolean;
}): { label: string; tone: StatusTone } {
  if (yamlLoading) {
    return { label: "正在转换", tone: "active" };
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
