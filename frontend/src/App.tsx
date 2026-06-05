import { useMemo, useState } from "react";
import { CheckCircle2, ClipboardCopy, FileText, Loader2, Play, ScrollText, Wand2 } from "lucide-react";

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
  const [isValid, setIsValid] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [yamlLoading, setYamlLoading] = useState(false);
  const [yamlDraft, setYamlDraft] = useState("");

  const canPreview = useMemo(() => novelText.trim().length > 0 && !previewLoading, [novelText, previewLoading]);
  const canGenerate = useMemo(() => novelText.trim().length > 0 && !yamlLoading, [novelText, yamlLoading]);
  const yamlPreview = yamlDraft || placeholderYaml;

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
      const result = (await response.json()) as ApiResult<ChapterPreviewData>;
      if (result.code !== "0" || !result.data) {
        throw new Error(result.message || "章节解析失败。");
      }
      const data = result.data;
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
      const result = (await response.json()) as ApiResult<ScriptYamlData>;
      if (result.code !== "0" || !result.data) {
        throw new Error(result.message || "YAML 生成失败。");
      }
      const data = result.data;
      setYamlDraft(data.yaml);
      setSchemaVersion(data.schemaVersion);
      setGeneratedTitle(data.title);
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
    await navigator.clipboard.writeText(yamlDraft);
    setYamlMessage("YAML 已复制到剪贴板。");
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
              onChange={(event) => setNovelText(event.target.value)}
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
              <button className="icon-action" type="button" onClick={copyYaml} disabled={!yamlDraft} aria-label="复制 YAML">
                <ClipboardCopy aria-hidden="true" size={17} />
              </button>
            </div>
            <div className="yaml-meta">
              <span>{schemaVersion ? `Schema ${schemaVersion}` : "Schema 1.0"}</span>
              <span>{generatedTitle || "待生成"}</span>
            </div>
            <pre>{yamlPreview}</pre>
            <div className="status-line yaml-status" data-valid={Boolean(yamlDraft)}>
              <CheckCircle2 aria-hidden="true" size={16} />
              <span>{yamlMessage}</span>
            </div>
          </section>
        </div>
      </section>
    </main>
  );
}

export default App;
