import { useMemo, useState } from "react";
import { CheckCircle2, FileText, Loader2, Play, ScrollText } from "lucide-react";

const sampleYaml = `schema_version: "1.0"
metadata:
  title: "未命名剧本"
  format: "screenplay"
source:
  chapter_count: 3
characters: []
acts:
  - id: "act_1"
    title: "第一幕"
    scenes: []`;

function App() {
  const [novelText, setNovelText] = useState(
    "第 1 章\n\n在这里粘贴小说正文...\n\n第 2 章\n\n...\n\n第 3 章\n\n..."
  );
  const [message, setMessage] = useState("输入至少 3 个章节后，先解析章节，再进入 AI 生成。");
  const [chapterCount, setChapterCount] = useState(0);
  const [isValid, setIsValid] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const canPreview = useMemo(() => novelText.trim().length > 0 && !isLoading, [novelText, isLoading]);

  async function previewChapters() {
    if (!canPreview) return;
    setIsLoading(true);
    try {
      const response = await fetch("/api/novels/chapters/preview", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: novelText })
      });
      if (!response.ok) {
        throw new Error(`章节解析失败：${response.status}`);
      }
      const result = (await response.json()) as {
        code: string;
        message?: string;
        data?: {
          valid: boolean;
          chapterCount: number;
          message: string;
        };
      };
      if (result.code !== "0" || !result.data) {
        throw new Error(result.message || "章节解析失败。");
      }
      const data = result.data as {
        valid: boolean;
        chapterCount: number;
        message: string;
      };
      setIsValid(data.valid);
      setChapterCount(data.chapterCount);
      setMessage(data.message);
    } catch (error) {
      setIsValid(false);
      setMessage(error instanceof Error ? error.message : "章节解析失败。");
    } finally {
      setIsLoading(false);
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
          <button className="primary-action" type="button" onClick={previewChapters} disabled={!canPreview}>
            {isLoading ? <Loader2 aria-hidden="true" className="spin" size={18} /> : <Play aria-hidden="true" size={18} />}
            解析章节
          </button>
        </header>

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
              <ScrollText aria-hidden="true" size={20} />
              <h2 id="yaml-preview-title">剧本 YAML</h2>
            </div>
            <pre>{sampleYaml}</pre>
          </section>
        </div>
      </section>
    </main>
  );
}

export default App;
