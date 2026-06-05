import type { ChangeEvent, ReactNode } from "react";
import {
  BookOpen,
  CheckCircle2,
  ChevronRight,
  ClipboardCopy,
  Code2,
  FileDown,
  FileInput,
  FileText,
  FolderOpen,
  History,
  Layers3,
  ListChecks,
  RefreshCcw,
  Save,
  ScrollText,
  ShieldCheck,
  Sparkles,
  UploadCloud
} from "lucide-react";

import { ActionButton } from "./ActionButton";
import { EmptyState } from "./EmptyState";
import { StatusBadge, type StatusTone } from "./StatusBadge";

export type ChapterOutline = {
  excerpt: string;
  id: string;
  index: number;
  title: string;
  wordCount: number;
};

export type DraftListItem = {
  draftId: string;
  schemaVersion: string;
  title: string;
  validationStatus: string;
};

export type ProjectListItem = {
  description?: string | null;
  projectId: string;
  status: string;
  title: string;
};

export type ProgressStep = {
  detail: string;
  label: string;
  status: "active" | "complete" | "error" | "idle" | "warning";
};

export type ValidationCheck = {
  detail: string;
  label: string;
  state: "fail" | "pass" | "pending" | "warn";
};

type AppShellProps = {
  children: ReactNode;
  scriptPanel: ReactNode;
  sidebar: ReactNode;
  topBar: ReactNode;
};

export function AppShell({ children, scriptPanel, sidebar, topBar }: AppShellProps) {
  return (
    <main className="app-shell">
      <div className="studio-frame">
        {topBar}
        <div className="workbench-grid">
          {sidebar}
          <section className="center-workbench" aria-label="小说输入与章节预览">
            {children}
          </section>
          {scriptPanel}
        </div>
      </div>
    </main>
  );
}

type TopBarProps = {
  actions: ReactNode;
  status: ReactNode;
};

export function TopBar({ actions, status }: TopBarProps) {
  return (
    <header className="top-bar">
      <div className="top-bar__brand">
        <span className="product-mark" aria-hidden="true">
          <Sparkles size={18} />
        </span>
        <div>
          <p className="top-bar__eyebrow">Act One</p>
          <h1>AI 小说转剧本工作台</h1>
        </div>
      </div>
      <div className="top-bar__meta">
        {status}
        <div className="top-bar__actions">{actions}</div>
      </div>
    </header>
  );
}

type SidebarProps = {
  author: string;
  chapterCount: number;
  children: ReactNode;
  isValid: boolean;
  projectId: string;
  title: string;
};

export function Sidebar({ author, chapterCount, children, isValid, projectId, title }: SidebarProps) {
  return (
    <aside className="sidebar" aria-label="项目导航">
      <div className="sidebar__section sidebar__project">
        <div className="sidebar__section-title">
          <FolderOpen size={16} aria-hidden="true" />
          <span>当前项目</span>
        </div>
        <h2>{title || "未命名剧本"}</h2>
        <p>{author || "作者未填写"}</p>
        <div className="sidebar__badges">
          <StatusBadge label={projectId ? "已建项目" : "本地草稿"} tone={projectId ? "info" : "neutral"} />
          <StatusBadge
            label={chapterCount > 0 ? `${chapterCount} 章` : "待解析"}
            tone={isValid ? "success" : chapterCount > 0 ? "warning" : "neutral"}
          />
        </div>
      </div>

      <nav className="sidebar__section sidebar__nav" aria-label="工作流">
        <div className="sidebar__section-title">
          <Layers3 size={16} aria-hidden="true" />
          <span>工作流</span>
        </div>
        <a className="sidebar__nav-item" href="#novel-input">
          <BookOpen size={16} aria-hidden="true" />
          <span>小说输入</span>
        </a>
        <a className="sidebar__nav-item" href="#chapter-preview">
          <ListChecks size={16} aria-hidden="true" />
          <span>章节结构</span>
        </a>
        <a className="sidebar__nav-item" href="#yaml-output">
          <Code2 size={16} aria-hidden="true" />
          <span>YAML 剧本</span>
        </a>
      </nav>

      {children}

      <div className="sidebar__section sidebar__future">
        <div className="sidebar__section-title">
          <Sparkles size={16} aria-hidden="true" />
          <span>AI 扩展槽</span>
        </div>
        <div className="future-list">
          <span>人物表抽取</span>
          <span>对白精修</span>
          <span>多版本对比</span>
        </div>
      </div>
    </aside>
  );
}

type ChapterListProps = {
  activeChapterIndex: number;
  chapters: ChapterOutline[];
  chapterCount: number;
  onSelectChapter: (index: number) => void;
};

export function ChapterList({ activeChapterIndex, chapterCount, chapters, onSelectChapter }: ChapterListProps) {
  return (
    <section className="sidebar__section chapter-list" id="chapter-preview" aria-label="章节管理">
      <div className="sidebar__section-title">
        <FileText size={16} aria-hidden="true" />
        <span>章节管理</span>
        {chapterCount > 0 ? <strong>{chapterCount}</strong> : null}
      </div>
      {chapters.length > 0 ? (
        <div className="chapter-list__items">
          {chapters.map((chapter) => (
            <button
              className="chapter-list__item"
              data-active={chapter.index === activeChapterIndex}
              key={chapter.id}
              onClick={() => onSelectChapter(chapter.index)}
              type="button"
            >
              <span className="chapter-list__index">{String(chapter.index).padStart(2, "0")}</span>
              <span className="chapter-list__body">
                <span>{chapter.title}</span>
                <small>{chapter.wordCount} 字</small>
              </span>
              <ChevronRight size={15} aria-hidden="true" />
            </button>
          ))}
        </div>
      ) : (
        <EmptyState
          description="粘贴或导入包含章节标题的小说文本后，这里会形成可跳转的章节索引。"
          icon={<BookOpen size={18} />}
          size="sm"
          title="暂无章节"
        />
      )}
    </section>
  );
}

type ProjectListProps = {
  activeProjectId: string;
  loading: boolean;
  onRefresh: () => void;
  onSelectProject: (projectId: string) => void;
  projects: ProjectListItem[];
};

export function ProjectList({ activeProjectId, loading, onRefresh, onSelectProject, projects }: ProjectListProps) {
  return (
    <section className="sidebar__section project-list" aria-label="我的项目">
      <div className="sidebar__section-title">
        <FolderOpen size={16} aria-hidden="true" />
        <span>我的项目</span>
        <button aria-label="刷新我的项目" className="sidebar-icon-button" disabled={loading} onClick={onRefresh} type="button">
          <RefreshCcw aria-hidden="true" className={loading ? "spin" : ""} size={14} />
        </button>
      </div>
      {projects.length > 0 ? (
        <div className="project-list__items">
          {projects.map((project) => (
            <button
              className="project-list__item"
              data-active={project.projectId === activeProjectId}
              key={project.projectId}
              onClick={() => onSelectProject(project.projectId)}
              type="button"
            >
              <span>{project.title}</span>
              <small>{project.status || "draft"}</small>
            </button>
          ))}
        </div>
      ) : (
        <EmptyState description="保存草稿后会在这里形成项目入口。" icon={<FolderOpen size={18} />} size="sm" title="暂无项目" />
      )}
    </section>
  );
}

type InputPanelProps = {
  author: string;
  chapterCount: number;
  characterCount: number;
  isValid: boolean;
  logline: string;
  message: string;
  novelText: string;
  onAuthorChange: (value: string) => void;
  onFileImport: (file: File) => Promise<void> | void;
  onLoglineChange: (value: string) => void;
  onNovelTextChange: (value: string) => void;
  onTitleChange: (value: string) => void;
  selectedChapter?: ChapterOutline;
  title: string;
};

export function InputPanel({
  author,
  chapterCount,
  characterCount,
  isValid,
  logline,
  message,
  novelText,
  onAuthorChange,
  onFileImport,
  onLoglineChange,
  onNovelTextChange,
  onTitleChange,
  selectedChapter,
  title
}: InputPanelProps) {
  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (file) {
      await onFileImport(file);
    }
    input.value = "";
  }

  return (
    <section className="studio-panel input-panel" id="novel-input" aria-labelledby="novel-input-title">
      <div className="panel-heading">
        <div>
          <p className="panel-kicker">Novel Source</p>
          <h2 id="novel-input-title">小说输入</h2>
        </div>
        <label className="file-import-button">
          <UploadCloud size={16} aria-hidden="true" />
          <span>导入文本</span>
          <input
            accept=".txt,.md,.markdown,text/plain,text/markdown"
            className="visually-hidden"
            onChange={handleFileChange}
            type="file"
          />
        </label>
      </div>

      <div className="metadata-grid" aria-label="剧本基础信息">
        <label className="field-control">
          <span>剧本标题</span>
          <input
            maxLength={128}
            onChange={(event) => onTitleChange(event.target.value)}
            placeholder="未填写时使用第一章标题"
            value={title}
          />
        </label>
        <label className="field-control">
          <span>作者</span>
          <input maxLength={64} onChange={(event) => onAuthorChange(event.target.value)} placeholder="可选" value={author} />
        </label>
        <label className="field-control field-control--wide">
          <span>一句话梗概</span>
          <input
            maxLength={512}
            onChange={(event) => onLoglineChange(event.target.value)}
            placeholder="主角目标、冲突和转变"
            value={logline}
          />
        </label>
      </div>

      {!novelText.trim() ? (
        <EmptyState
          action={
            <label className="empty-upload">
              <FileInput size={15} aria-hidden="true" />
              <span>选择文本文件</span>
              <input
                accept=".txt,.md,.markdown,text/plain,text/markdown"
                className="visually-hidden"
                onChange={handleFileChange}
                type="file"
              />
            </label>
          }
          description="至少准备 3 个章节，章节标题建议使用“第 1 章 / 第一章”等格式。"
          icon={<BookOpen size={22} />}
          title="导入或粘贴小说正文"
        />
      ) : null}

      <textarea
        aria-label="小说章节输入"
        className="novel-editor"
        onChange={(event) => onNovelTextChange(event.target.value)}
        placeholder={"第 1 章\n\n在这里粘贴小说正文...\n\n第 2 章\n\n...\n\n第 3 章\n\n..."}
        spellCheck={false}
        value={novelText}
      />

      <div className="input-panel__footer">
        <div className="status-line" data-valid={isValid}>
          <CheckCircle2 aria-hidden="true" size={16} />
          <span>{message}</span>
        </div>
        <div className="editor-stats">
          <span>{characterCount} 字符</span>
          <span>{chapterCount > 0 ? `${chapterCount} 章` : "未解析"}</span>
        </div>
      </div>

      <section className="chapter-excerpt" aria-label="章节预览">
        <div className="chapter-excerpt__heading">
          <span>当前章节预览</span>
          {selectedChapter ? <strong>{selectedChapter.wordCount} 字</strong> : null}
        </div>
        {selectedChapter ? (
          <>
            <h3>{selectedChapter.title}</h3>
            <p>{selectedChapter.excerpt || "该章节暂无正文摘录。"}</p>
          </>
        ) : (
          <p className="muted-copy">解析章节后会显示当前章节的前段内容，便于对照生成结果。</p>
        )}
      </section>
    </section>
  );
}

type ScriptPreviewPanelProps = {
  activeDraftId: string;
  canExport: boolean;
  canRefreshDrafts: boolean;
  canSave: boolean;
  draftLoading: boolean;
  drafts: DraftListItem[];
  draftId: string;
  exportLoading: boolean;
  generatedTitle: string;
  onCopyYaml: () => void;
  onExportYaml: () => void;
  onLoadDraft: (draftId: string) => void;
  onRefreshDrafts: () => void;
  onSaveDraft: () => void;
  onYamlChange: (value: string) => void;
  projectId: string;
  saveLoading: boolean;
  schemaChecks: ValidationCheck[];
  schemaVersion: string;
  validationError: string;
  validationStatus: string;
  yaml: string;
  yamlMessage: string;
  yamlPlaceholder: string;
};

export function ScriptPreviewPanel({
  activeDraftId,
  canExport,
  canRefreshDrafts,
  canSave,
  draftLoading,
  drafts,
  draftId,
  exportLoading,
  generatedTitle,
  onCopyYaml,
  onExportYaml,
  onLoadDraft,
  onRefreshDrafts,
  onSaveDraft,
  onYamlChange,
  projectId,
  saveLoading,
  schemaChecks,
  schemaVersion,
  validationError,
  validationStatus,
  yaml,
  yamlMessage,
  yamlPlaceholder
}: ScriptPreviewPanelProps) {
  return (
    <aside className="studio-panel script-panel" id="yaml-output" aria-labelledby="yaml-output-title">
      <div className="panel-heading">
        <div>
          <p className="panel-kicker">Structured Draft</p>
          <h2 id="yaml-output-title">剧本 YAML</h2>
        </div>
        <div className="panel-actions">
          <ActionButton
            aria-label="保存 YAML 草稿"
            disabled={!canSave}
            icon={<Save aria-hidden="true" className={saveLoading ? "spin" : ""} size={15} />}
            onClick={onSaveDraft}
            size="sm"
            variant="secondary"
          >
            保存
          </ActionButton>
          <ActionButton
            aria-label="导出 YAML 文件"
            disabled={!canExport}
            icon={<FileDown aria-hidden="true" className={exportLoading ? "spin" : ""} size={15} />}
            iconOnly
            onClick={onExportYaml}
            size="sm"
            variant="ghost"
          />
          <ActionButton
            aria-label="刷新草稿列表"
            disabled={!canRefreshDrafts}
            icon={<RefreshCcw aria-hidden="true" className={draftLoading ? "spin" : ""} size={15} />}
            iconOnly
            onClick={onRefreshDrafts}
            size="sm"
            variant="ghost"
          />
          <ActionButton
            aria-label="复制 YAML"
            disabled={!yaml.trim()}
            icon={<ClipboardCopy aria-hidden="true" size={15} />}
            iconOnly
            onClick={onCopyYaml}
            size="sm"
            variant="ghost"
          />
        </div>
      </div>

      <div className="yaml-meta">
        <StatusBadge label={schemaVersion ? `Schema ${schemaVersion}` : "Schema 1.0"} tone="info" />
        <span>{draftId ? `Draft ${draftId}` : generatedTitle || "待生成"}</span>
      </div>

      <YamlEditor onChange={onYamlChange} placeholder={yamlPlaceholder} value={yaml} />

      <div className="status-line yaml-status" data-valid={Boolean(yaml.trim())}>
        <ScrollText aria-hidden="true" size={16} />
        <span>{yamlMessage}</span>
      </div>

      <SchemaValidationPanel checks={schemaChecks} error={validationError} status={validationStatus} yamlReady={Boolean(yaml.trim())} />

      <section className="draft-history" aria-label="已保存草稿">
        <div className="draft-history__heading">
          <span>
            <History size={15} aria-hidden="true" />
            保存记录
          </span>
          {projectId ? <strong>{drafts.length} 条</strong> : null}
        </div>
        <div className="draft-list">
          {drafts.length > 0 ? (
            drafts.map((draft) => (
              <button
                className="draft-item"
                data-active={draft.draftId === activeDraftId}
                key={draft.draftId}
                onClick={() => onLoadDraft(draft.draftId)}
                type="button"
              >
                <span>{draft.title}</span>
                <small>
                  {draft.schemaVersion} / {draft.validationStatus}
                </small>
              </button>
            ))
          ) : (
            <EmptyState
              description="保存后可在此重新加载草稿版本。"
              icon={<History size={18} />}
              size="sm"
              title="暂无保存记录"
            />
          )}
        </div>
      </section>
    </aside>
  );
}

type YamlEditorProps = {
  onChange: (value: string) => void;
  placeholder: string;
  value: string;
};

export function YamlEditor({ onChange, placeholder, value }: YamlEditorProps) {
  return (
    <textarea
      aria-label="可编辑剧本 YAML"
      className="yaml-editor"
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      spellCheck={false}
      value={value}
    />
  );
}

type ConversionProgressProps = {
  steps: ProgressStep[];
};

export function ConversionProgress({ steps }: ConversionProgressProps) {
  return (
    <section className="sidebar__section conversion-progress" aria-label="转换进度">
      <div className="sidebar__section-title">
        <ShieldCheck size={16} aria-hidden="true" />
        <span>转换状态</span>
      </div>
      <ol>
        {steps.map((step) => (
          <li data-status={step.status} key={step.label}>
            <span className="progress-dot" aria-hidden="true" />
            <div>
              <strong>{step.label}</strong>
              <small>{step.detail}</small>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

type SchemaValidationPanelProps = {
  checks: ValidationCheck[];
  error: string;
  status: string;
  yamlReady: boolean;
};

export function SchemaValidationPanel({ checks, error, status, yamlReady }: SchemaValidationPanelProps) {
  return (
    <section className="schema-panel" aria-label="Schema 校验结果">
      <div className="schema-panel__heading">
        <span>
          <ListChecks size={15} aria-hidden="true" />
          Schema 校验
        </span>
        <StatusBadge label={yamlReady ? status || "local" : "pending"} tone={validationTone(status, yamlReady)} />
      </div>
      {error ? <p className="schema-panel__error">{error}</p> : null}
      <div className="schema-checks">
        {checks.map((check) => (
          <div className="schema-check" data-state={check.state} key={check.label}>
            <span className="schema-check__mark" aria-hidden="true" />
            <div>
              <strong>{check.label}</strong>
              <small>{check.detail}</small>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function validationTone(status: string, yamlReady: boolean): StatusTone {
  if (!yamlReady) {
    return "neutral";
  }
  if (status === "valid") {
    return "success";
  }
  if (status === "invalid") {
    return "danger";
  }
  if (status === "edited") {
    return "warning";
  }
  return "info";
}
