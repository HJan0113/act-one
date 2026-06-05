import { FileText, Play, ScrollText } from "lucide-react";

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
  return (
    <main className="app-shell">
      <section className="workspace">
        <header className="masthead">
          <div>
            <p className="eyebrow">Act One</p>
            <h1>AI 小说转剧本工作台</h1>
          </div>
          <button className="primary-action" type="button">
            <Play aria-hidden="true" size={18} />
            开始生成 YAML
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
              defaultValue={"第 1 章\n\n在这里粘贴小说正文...\n\n第 2 章\n\n...\n\n第 3 章\n\n..."}
              spellCheck={false}
            />
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

