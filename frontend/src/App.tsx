import { useMemo, useState } from "react";
import {
  CheckCircle2,
  ClipboardCopy,
  FileText,
  Loader2,
  Play,
  RefreshCcw,
  Save,
  ScrollText,
  Wand2
} from "lucide-react";

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

type ApiResult<T> = {
  code: string;
  message?: string;
  data?: T;
};

type ChapterPreviewData = {
  valid: boolean;
  chapterCount: number;
  message: string;
};

type ScriptYamlData = {
  schemaVersion: string;
  title: string;
  chapterCount: number;
  yaml: string;
};

type ScriptProjectData = {
  projectId: string;
  title: string;
  chapterCount: number;
};

type ScriptDraftSaveData = {
  draftId: string;
  projectId: string;
  title: string;
  schemaVersion: string;
  validationStatus: string;
};

type ScriptDraftListItem = {
  draftId: string;
  projectId: string;
  title: string;
  schemaVersion: string;
  validationStatus: string;
};

type ScriptDraftDetail = ScriptDraftListItem & {
  yaml: string;
};

async function readApiData<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`${fallbackMessage}：${response.status}`);
  }
  const result = (await response.json()) as ApiResult<T>;
  if (result.code !== "0" || result.data === undefined || result.data === null) {
    throw new Error(result.message || `${fallbackMessage}。`);
  }
  return result.data;
}

function App() {
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [logline, setLogline] = useState("");
  const [novelText, setNovelText] = useState(
    "第 1 章\n\n在这里粘贴小说正文...\n\n第 2 章\n\n...\n\n第 3 章\n\n..."
  );
  const [message, setMessage] = useState("输入至少 3 个章节后，先解析章节，再进入 AI 生成。");
  const [yamlMessage, setYamlMessage] = useState("生成后将在这里展示可编辑 YAML 初稿。");
  const [chapterCount, setChapterCount] = useState(0);
  const [schemaVersion, setSchemaVersion] = useState("");
  const [generatedTitle, setGeneratedTitle] = useState("");
  const [projectId, setProjectId] = useState("");
  const [draftId, setDraftId] = useState("");
  const [drafts, setDrafts] = useState<ScriptDraftListItem[]>([]);
  const [isValid, setIsValid] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [yamlLoading, setYamlLoading] = useState(false);
  const [saveLoading, setSaveLoading] = useState(false);
  const [draftLoading, setDraftLoading] = useState(false);
  const [yamlDraft, setYamlDraft] = useState("");

  const canPreview = useMemo(() => novelText.trim().length > 0 && !previewLoading, [novelText, previewLoading]);
  const canGenerate = useMemo(() => novelText.trim().length > 0 && !yamlLoading, [novelText, yamlLoading]);
  const canSave = useMemo(() => yamlDraft.trim().length > 0 && !saveLoading, [yamlDraft, saveLoading]);
  const canRefreshDrafts = useMemo(() => projectId.length > 0 && !draftLoading, [projectId, draftLoading]);
  const yamlPreview = yamlDraft || placeholderYaml;

  function handleNovelTextChange(value: string) {
    setNovelText(value);
    setProjectId("");
    setDraftId("");
    setDrafts([]);
  }

  async function previewChapters() {
    if (!canPreview) return;
    setPreviewLoading(true);
    try {
      const response = await fetch("/api/novels/chapters/preview", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: novelText })
      });
      if (!response.ok) {
        throw new Error(`章节解析失败：${response.status}`);
      }
      const data = await readApiData<ChapterPreviewData>(response, "章节解析失败");
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
    setYamlMessage("正在生成 YAML 初稿...");
    try {
      const response = await fetch("/api/script/drafts/yaml/from-text", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title,
          author,
          logline,
          text: novelText
        })
      });
      if (!response.ok) {
        throw new Error(`YAML 生成失败：${response.status}`);
      }
      const data = await readApiData<ScriptYamlData>(response, "YAML 生成失败");
      setYamlDraft(data.yaml);
      setSchemaVersion(data.schemaVersion);
      setGeneratedTitle(data.title);
      setDraftId("");
      setChapterCount(data.chapterCount);
      setIsValid(true);
      setMessage(`已识别 ${data.chapterCount} 个章节。`);
      setYamlMessage("YAML 初稿生成成功，可继续复制或编辑。");
    } catch (error) {
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
    const response = await fetch("/api/script/projects/from-text", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: generatedTitle || title,
        description: logline,
        text: novelText
      })
    });
    const data = await readApiData<ScriptProjectData>(response, "项目创建失败");
    setProjectId(data.projectId);
    setGeneratedTitle((current) => current || data.title);
    setChapterCount(data.chapterCount);
    return data.projectId;
  }

  async function loadDrafts(nextProjectId = projectId) {
    if (!nextProjectId) return;
    setDraftLoading(true);
    try {
      const response = await fetch(`/api/script/drafts?projectId=${encodeURIComponent(nextProjectId)}`);
      const data = await readApiData<ScriptDraftListItem[]>(response, "草稿列表加载失败");
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
      const response = await fetch("/api/script/drafts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          projectId: activeProjectId,
          title: generatedTitle || title || "未命名剧本",
          schemaVersion: schemaVersion || "1.0",
          yaml: yamlDraft
        })
      });
      const data = await readApiData<ScriptDraftSaveData>(response, "草稿保存失败");
      setDraftId(data.draftId);
      setProjectId(data.projectId);
      setGeneratedTitle(data.title);
      setSchemaVersion(data.schemaVersion);
      setYamlMessage(`草稿已保存，状态：${data.validationStatus}。`);
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
      const response = await fetch(`/api/script/drafts/${encodeURIComponent(nextDraftId)}`);
      const data = await readApiData<ScriptDraftDetail>(response, "草稿加载失败");
      setYamlDraft(data.yaml);
      setDraftId(data.draftId);
      setProjectId(data.projectId);
      setGeneratedTitle(data.title);
      setSchemaVersion(data.schemaVersion);
      setYamlMessage(`已加载草稿，状态：${data.validationStatus}。`);
    } catch (error) {
      setYamlMessage(error instanceof Error ? error.message : "草稿加载失败。");
    } finally {
      setDraftLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="workspace">
        <header className="masthead">
          <div>
            <p className="eyebrow">Act One</p>
            <h1>AI 小说转剧本工作台</h1>
          </div>
          <div className="masthead-actions">
            <button className="secondary-action" type="button" onClick={previewChapters} disabled={!canPreview}>
              {previewLoading ? (
                <Loader2 aria-hidden="true" className="spin" size={18} />
              ) : (
                <Play aria-hidden="true" size={18} />
              )}
              解析章节
            </button>
            <button className="primary-action" type="button" onClick={generateYaml} disabled={!canGenerate}>
              {yamlLoading ? (
                <Loader2 aria-hidden="true" className="spin" size={18} />
              ) : (
                <Wand2 aria-hidden="true" size={18} />
              )}
              生成 YAML
            </button>
          </div>
        </header>

        <section className="metadata-row" aria-label="剧本基础信息">
          <label className="meta-field">
            <span>剧本标题</span>
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="未填写时使用第一章标题"
              maxLength={128}
            />
          </label>
          <label className="meta-field compact">
            <span>作者</span>
            <input value={author} onChange={(event) => setAuthor(event.target.value)} placeholder="可选" maxLength={64} />
          </label>
          <label className="meta-field wide">
            <span>一句话梗概</span>
            <input
              value={logline}
              onChange={(event) => setLogline(event.target.value)}
              placeholder="主角目标、冲突和转变"
              maxLength={512}
            />
          </label>
        </section>

        <div className="editor-grid">
          <section className="panel input-panel" aria-labelledby="novel-input-title">
            <div className="panel-title">
              <FileText aria-hidden="true" size={20} />
              <h2 id="novel-input-title">小说章节</h2>
            </div>
            <textarea
              aria-label="小说章节输入"
              value={novelText}
              onChange={(event) => handleNovelTextChange(event.target.value)}
              spellCheck={false}
            />
            <div className="status-line" data-valid={isValid}>
              <CheckCircle2 aria-hidden="true" size={16} />
              <span>{message}</span>
              {chapterCount > 0 ? <strong>{chapterCount} 章</strong> : null}
            </div>
          </section>

          <section className="panel yaml-panel" aria-labelledby="yaml-preview-title">
            <div className="panel-title">
              <div className="panel-title-main">
                <ScrollText aria-hidden="true" size={20} />
                <h2 id="yaml-preview-title">剧本 YAML</h2>
              </div>
              <div className="panel-title-actions">
                <button className="save-action" type="button" onClick={saveDraft} disabled={!canSave}>
                  {saveLoading ? (
                    <Loader2 aria-hidden="true" className="spin" size={16} />
                  ) : (
                    <Save aria-hidden="true" size={16} />
                  )}
                  保存
                </button>
                <button
                  className="icon-action"
                  type="button"
                  onClick={() => loadDrafts()}
                  disabled={!canRefreshDrafts}
                  aria-label="刷新草稿列表"
                >
                  {draftLoading ? (
                    <Loader2 aria-hidden="true" className="spin" size={17} />
                  ) : (
                    <RefreshCcw aria-hidden="true" size={17} />
                  )}
                </button>
                <button className="icon-action" type="button" onClick={copyYaml} disabled={!yamlDraft} aria-label="复制 YAML">
                  <ClipboardCopy aria-hidden="true" size={17} />
                </button>
              </div>
            </div>
            <div className="yaml-meta">
              <span>{schemaVersion ? `Schema ${schemaVersion}` : "Schema 1.0"}</span>
              <span>{draftId ? `Draft ${draftId}` : generatedTitle || "待生成"}</span>
            </div>
            <pre>{yamlPreview}</pre>
            <div className="status-line yaml-status" data-valid={Boolean(yamlDraft)}>
              <CheckCircle2 aria-hidden="true" size={16} />
              <span>{yamlMessage}</span>
            </div>
            <div className="draft-history" aria-label="已保存草稿">
              <div className="draft-history-title">
                <span>保存记录</span>
                {projectId ? <strong>{drafts.length} 条</strong> : null}
              </div>
              <div className="draft-list">
                {drafts.length > 0 ? (
                  drafts.map((draft) => (
                    <button
                      className="draft-item"
                      type="button"
                      key={draft.draftId}
                      data-active={draft.draftId === draftId}
                      onClick={() => loadDraftDetail(draft.draftId)}
                    >
                      <span>{draft.title}</span>
                      <small>{draft.schemaVersion} / {draft.validationStatus}</small>
                    </button>
                  ))
                ) : (
                  <span className="draft-empty">保存后可在此重新加载草稿。</span>
                )}
              </div>
            </div>
          </section>
        </div>
      </section>
    </main>
  );
}

export default App;
