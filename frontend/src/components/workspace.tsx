import type {ChangeEvent, ReactNode} from "react";
import {
    Activity,
    BookOpen,
    CheckCircle2,
    ChevronRight,
    CircleStop,
    ClipboardCopy,
    Code2,
    FileDown,
    FileInput,
    FileText,
    FolderOpen,
    History,
    ListChecks,
    PanelLeftClose,
    PanelLeftOpen,
    RefreshCcw,
    Save,
    ScrollText,
    ShieldCheck,
    Sparkles,
    UploadCloud
} from "lucide-react";

import {ActionButton} from "./ActionButton";
import {EmptyState} from "./EmptyState";
import {StatusBadge, type StatusTone} from "./StatusBadge";
import type {ScriptTraceScene} from "../utils/scriptYamlTrace";

export type ChapterOutline = {
  content: string;
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

export type TaskStatus = "canceled" | "failed" | "idle" | "ready" | "running" | "success";

export type TaskSummary = {
    canCancel: boolean;
    canRetry: boolean;
    currentStage: string;
    errorMessage: string;
    errorType?: string;
    failedStage?: string;
    message: string;
    progress: number;
    resumeStage?: string;
    retryable?: boolean | null;
    status: TaskStatus;
    taskId: string;
    usageSummary?: {
        callCount: number;
        estimated: boolean;
        totalTokens: number;
    } | null;
};

export type ValidationCheck = {
  detail: string;
  label: string;
  state: "fail" | "pass" | "pending" | "warn";
};

export type WorkspaceView = "archive" | "pipeline" | "result" | "source" | "trace";

type AppShellProps = {
    activeView: WorkspaceView;
  children: ReactNode;
  sidebar: ReactNode;
    sidebarCollapsed: boolean;
  topBar: ReactNode;
    onNavigate: (view: WorkspaceView) => void;
    onToggleSidebar: () => void;
};

export function AppShell({
                             activeView,
                             children,
                             onNavigate,
                             onToggleSidebar,
                             sidebar,
                             sidebarCollapsed,
                             topBar
                         }: AppShellProps) {
  return (
      <main className="app-shell" data-sidebar-collapsed={sidebarCollapsed}>
      <div className="studio-frame">
        {topBar}
          <div className="workspace-layout">
              <nav className="workspace-rail" aria-label="工作台页面">
                  <button
                      aria-label={sidebarCollapsed ? "展开项目栏" : "压缩项目栏"}
                      className="rail-toggle"
                      onClick={onToggleSidebar}
                      type="button"
                  >
                      {sidebarCollapsed ? <PanelLeftOpen size={18} aria-hidden="true"/> :
                          <PanelLeftClose size={18} aria-hidden="true"/>}
                  </button>
                  <div className="workspace-nav">
                      {workspaceNavItems.map((item) => (
                          <button
                              aria-current={activeView === item.id ? "page" : undefined}
                              className="workspace-nav__item"
                              data-active={activeView === item.id}
                              key={item.id}
                              onClick={() => onNavigate(item.id)}
                              type="button"
                          >
                              <span className="workspace-nav__icon">{item.icon}</span>
                              <span className="workspace-nav__text">
                    <strong>{item.label}</strong>
                    <small>{item.detail}</small>
                  </span>
                          </button>
                      ))}
                  </div>
              </nav>
              <div className="workspace-sidebar">{sidebar}</div>
              <section className="workspace-page" data-view={activeView} aria-live="polite">
                  <div className="workspace-page__inner" key={activeView}>
                      {children}
                  </div>
          </section>
        </div>
      </div>
    </main>
  );
}

const workspaceNavItems: Array<{
    detail: string;
    icon: ReactNode;
    id: WorkspaceView;
    label: string;
}> = [
    {
        detail: "导入与解析",
        icon: <BookOpen size={18} aria-hidden="true"/>,
        id: "source",
        label: "原文"
    },
    {
        detail: "任务与阶段",
        icon: <Activity size={18} aria-hidden="true"/>,
        id: "pipeline",
        label: "转换"
    },
    {
        detail: "YAML 与校验",
        icon: <Code2 size={18} aria-hidden="true"/>,
        id: "result",
        label: "结果"
    },
    {
        detail: "源文对照",
        icon: <ScrollText size={18} aria-hidden="true"/>,
        id: "trace",
        label: "追溯"
    },
    {
        detail: "项目与草稿",
        icon: <History size={18} aria-hidden="true"/>,
        id: "archive",
        label: "归档"
    }
];

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

type TaskConsoleProps = {
    summary: TaskSummary;
    onCancel: () => void;
    onRetry: () => void;
};

export function TaskConsole({onCancel, onRetry, summary}: TaskConsoleProps) {
    return (
        <section className="sidebar__section task-console" data-status={summary.status} aria-label="AI 转换任务控制台">
            <div className="sidebar__section-title">
                <Activity size={16} aria-hidden="true"/>
                <span>任务控制台</span>
                <StatusBadge label={taskStatusLabel(summary.status)} tone={taskStatusTone(summary.status)}/>
            </div>
            <div className="task-console__body">
                <div className="task-console__metric">
                    <strong>{summary.progress}%</strong>
                    <span>{summary.currentStage}</span>
                </div>
                <progress aria-label="AI 转换进度" className="task-meter" max={100} value={summary.progress}/>
                <p>{summary.errorMessage || summary.message}</p>
                {summary.status === "failed" ? (
                    <div className="task-console__recovery">
                        <span>{summary.failedStage ? stageDisplayName(summary.failedStage) : "失败阶段未知"}</span>
                        <span>{summary.errorType || "RuntimeException"}</span>
                        <span>
                            {summary.retryable === false
                                ? "不可重试"
                                : `可从 ${summary.resumeStage ? stageDisplayName(summary.resumeStage) : "安全阶段"} 继续`}
                        </span>
                    </div>
                ) : null}
                <div className="task-console__meta">
                    <span>{summary.taskId || "未创建任务"}</span>
                    <div className="task-console__actions">
                        <ActionButton
                            disabled={!summary.canRetry}
                            icon={<RefreshCcw aria-hidden="true" size={14}/>}
                            onClick={onRetry}
                            size="sm"
                            variant="secondary"
                        >
                            重试
                        </ActionButton>
                        <ActionButton
                            disabled={!summary.canCancel}
                            icon={<CircleStop aria-hidden="true" size={14}/>}
                            onClick={onCancel}
                            size="sm"
                            variant="ghost"
                        >
                            停止
                        </ActionButton>
                    </div>
                </div>
            </div>
        </section>
    );
}

function stageDisplayName(stage: string) {
    return stageLabels[stage] || stage;
}

function usageSummaryLabel(summary: TaskSummary["usageSummary"]) {
    if (!summary || summary.callCount <= 0) {
        return "待统计";
    }
    const tokenPrefix = summary.estimated ? "~" : "";
    return `${summary.callCount} 次 / ${tokenPrefix}${summary.totalTokens} tokens`;
}

const stageLabels: Record<string, string> = {
    CHAPTER_SUMMARY: "章节摘要",
    COMPLETED: "转换完成",
    CONSISTENCY_CHECK: "一致性校验",
    DIALOGUE_GENERATION: "对白生成",
    FACT_CONSTRAINT_CHECK: "事实约束",
    GLOBAL_STORY_EXTRACTION: "全局故事抽取",
    INIT: "任务初始化",
    PSYCHOLOGICAL_VISUALIZATION: "心理影视化",
    SCENE_PLANNING: "场景规划",
    SCRIPT_YAML_GENERATION: "YAML 生成",
    SOURCE_TRACE: "源文追溯",
    YAML_REPAIR: "YAML 自动修复",
    YAML_VALIDATION: "Schema 校验"
};

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
    locked: boolean;
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
                               locked,
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
            disabled={locked}
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
            disabled={locked}
            onChange={(event) => onTitleChange(event.target.value)}
            placeholder="未填写时使用第一章标题"
            value={title}
          />
        </label>
        <label className="field-control">
          <span>作者</span>
            <input
                disabled={locked}
                maxLength={64}
                onChange={(event) => onAuthorChange(event.target.value)}
                placeholder="可选"
                value={author}
            />
        </label>
        <label className="field-control field-control--wide">
          <span>一句话梗概</span>
          <input
              disabled={locked}
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
                disabled={locked}
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
        disabled={locked}
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

type SourceTraceWorkbenchProps = {
  activeSceneId: string;
  chapters: ChapterOutline[];
  scenes: ScriptTraceScene[];
  onSelectScene: (sceneId: string) => void;
  yamlReady: boolean;
};

export function SourceTraceWorkbench({
  activeSceneId,
  chapters,
  onSelectScene,
  scenes,
  yamlReady
}: SourceTraceWorkbenchProps) {
  const selectedScene = scenes.find((scene) => scene.id === activeSceneId) || scenes[0];
  const selectedChapter = selectedScene
    ? chapters.find(
      (chapter) => chapter.id === selectedScene.sourceChapterId || chapter.index === selectedScene.chapterIndex
    )
    : undefined;
  const sourceExcerpt = selectedScene?.sourceExcerpt.trim() || "";
  const hasExactExcerpt = Boolean(selectedChapter?.content && sourceExcerpt && selectedChapter.content.includes(sourceExcerpt));

  return (
    <div className="trace-workbench" aria-label="源文追溯三栏对照">
      <section className="studio-panel trace-panel trace-panel--source" aria-label="小说原文">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Novel Source</p>
            <h2>小说原文</h2>
          </div>
          <StatusBadge label={hasExactExcerpt ? "摘录命中" : selectedChapter ? "章节定位" : "待定位"} tone={hasExactExcerpt ? "success" : selectedChapter ? "warning" : "neutral"} />
        </div>

        <div className="trace-column-body">
          {selectedChapter ? (
            <>
              <div className="trace-source-card">
                <span>{sourceChapterLabel(selectedChapter)}</span>
                <strong>{selectedChapter.title}</strong>
                <small>{selectedScene?.sourceChapterId || selectedChapter.id}</small>
              </div>
              {sourceExcerpt ? (
                <blockquote className="trace-source-excerpt">
                  <span>scene.source_excerpt</span>
                  <p>{sourceExcerpt}</p>
                </blockquote>
              ) : null}
              <article className="trace-source-text" aria-label="当前来源章节正文">
                {renderHighlightedSource(selectedChapter.content, sourceExcerpt)}
              </article>
            </>
          ) : (
            <EmptyState
              description="先导入小说并解析章节，或加载包含 chapter_index / source_chapter_id 的草稿。"
              icon={<BookOpen size={18} />}
              size="sm"
              title="暂无可定位原文"
            />
          )}
        </div>
      </section>

      <section className="studio-panel trace-panel trace-panel--yaml" aria-label="YAML 场景索引">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Structured Scenes</p>
            <h2>YAML 场景</h2>
          </div>
          <StatusBadge label={scenes.length > 0 ? `${scenes.length} 场` : yamlReady ? "无场景" : "待生成"} tone={scenes.length > 0 ? "info" : "neutral"} />
        </div>

        <div className="trace-column-body">
          {scenes.length > 0 ? (
            <div className="trace-scene-list">
              {scenes.map((scene) => (
                <button
                  className="trace-scene-card"
                  data-active={scene.id === selectedScene?.id}
                  key={scene.id}
                  onClick={() => onSelectScene(scene.id)}
                  type="button"
                >
                  <span className="trace-scene-card__index">{scene.id.replace(/^scene_?/, "") || scene.id}</span>
                  <span className="trace-scene-card__body">
                    <strong>{scene.title}</strong>
                    <small>
                      第 {scene.chapterIndex || "-"} 章 / {scene.location || "待补充"} / {scene.time || "待补充"}
                    </small>
                    <em>{scene.scenePurpose || "未写入场景目的"}</em>
                  </span>
                  <ChevronRight size={15} aria-hidden="true" />
                </button>
              ))}
            </div>
          ) : (
            <EmptyState
              description={yamlReady ? "当前 YAML 未解析到 scenes 列表，请检查缩进或字段名。" : "生成或加载 YAML 草稿后，这里会显示可点击 scene。"}
              icon={<Code2 size={18} />}
              size="sm"
              title="暂无 YAML 场景"
            />
          )}
        </div>
      </section>

      <section className="studio-panel trace-panel trace-panel--script" aria-label="可读剧本">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Readable Script</p>
            <h2>可读剧本</h2>
          </div>
          <StatusBadge label={selectedScene?.aiExpansion === true ? "AI 扩写" : selectedScene ? "原文支撑" : "待选择"} tone={selectedScene?.aiExpansion === true ? "warning" : selectedScene ? "success" : "neutral"} />
        </div>

        <div className="trace-column-body">
          {selectedScene ? (
            <article className="trace-screenplay">
              <header>
                <span>{selectedScene.id}</span>
                <h3>{selectedScene.title}</h3>
                <p>
                  {selectedScene.location || "待补充"} · {selectedScene.time || "待补充"}
                </p>
              </header>

              {selectedScene.scenePurpose ? (
                <section className="trace-script-note" aria-label="场景目的">
                  <span>场景目的</span>
                  <p>{selectedScene.scenePurpose}</p>
                </section>
              ) : null}

              <section className="trace-script-action" aria-label="动作描述">
                <span>Action</span>
                <p>{selectedScene.action || "该场景暂无动作描述。"}</p>
              </section>

              <section className="trace-dialogues" aria-label="对白">
                <div className="trace-dialogues__heading">
                  <span>Dialogues</span>
                  <strong>{selectedScene.dialogues.length}</strong>
                </div>
                {selectedScene.dialogues.length > 0 ? (
                  selectedScene.dialogues.map((dialogue, index) => (
                    <div className="trace-dialogue" key={`${dialogue.speaker}-${index}`}>
                      <strong>{dialogue.speaker || "未知说话人"}</strong>
                      <p>{dialogue.line || "未填写对白"}</p>
                      {dialogue.emotion ? <small>{dialogue.emotion}</small> : null}
                    </div>
                  ))
                ) : (
                  <p className="muted-copy">该场景暂无对白，可在后续对白精修阶段补齐。</p>
                )}
              </section>

              <footer className="trace-script-footer">
                <span>角色</span>
                <strong>{sceneCharactersLabel(selectedScene)}</strong>
              </footer>
            </article>
          ) : (
            <EmptyState
              description="选择一个 YAML scene 后，会在这里渲染成可读剧本格式。"
              icon={<ScrollText size={18} />}
              size="sm"
              title="暂无可读场景"
            />
          )}
        </div>
      </section>
    </div>
  );
}

function sourceChapterLabel(chapter: ChapterOutline) {
  return `第 ${String(chapter.index).padStart(2, "0")} 章 / ${chapter.wordCount} 字`;
}

function sceneCharactersLabel(scene: ScriptTraceScene) {
  if (scene.characters.length > 0) {
    return scene.characters.join("、");
  }
  const speakers = Array.from(new Set(scene.dialogues.map((dialogue) => dialogue.speaker).filter(Boolean)));
  return speakers.length > 0 ? speakers.join("、") : "待补充";
}

function renderHighlightedSource(content: string, excerpt: string): ReactNode {
  const body = content.trim();
  if (!body) {
    return <p className="muted-copy">当前章节暂无正文内容。</p>;
  }

  const target = excerpt.trim();
  const start = target ? body.indexOf(target) : -1;
  if (start < 0) {
    return <p>{body}</p>;
  }

  const before = body.slice(0, start);
  const after = body.slice(start + target.length);
  return (
    <p>
      {before}
      <mark>{target}</mark>
      {after}
    </p>
  );
}

type ScriptPreviewPanelProps = {
  activeDraftId: string;
  canExport: boolean;
  canRefreshDrafts: boolean;
  canSave: boolean;
    taskSummary: TaskSummary;
  draftLoading: boolean;
  drafts: DraftListItem[];
  draftId: string;
  exportLoading: boolean;
  fallbackReason: string;
  generationMode: string;
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
                                       taskSummary,
  draftLoading,
  drafts,
  draftId,
  exportLoading,
  fallbackReason,
  generationMode,
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
        <div className="yaml-meta__badges">
          <StatusBadge label={schemaVersion ? `Schema ${schemaVersion}` : "Schema 1.0"} tone="info" />
          <StatusBadge label={generationModeLabel(generationMode)} tone={generationModeTone(generationMode, fallbackReason)} />
        </div>
        <span>{draftId ? `Draft ${draftId}` : generatedTitle || "待生成"}</span>
      </div>

      {fallbackReason ? (
        <div className="generation-fallback">
          <ShieldCheck aria-hidden="true" size={15} />
          <span>{fallbackReason}</span>
        </div>
      ) : null}

        <section className="result-summary" data-status={taskSummary.status} aria-label="任务结果摘要">
            <div>
                <span>转换任务</span>
                <strong>{taskSummary.currentStage}</strong>
            </div>
            <div>
                <span>进度</span>
                <strong>{taskSummary.progress}%</strong>
            </div>
            <div>
                <span>状态</span>
                <strong>{taskStatusLabel(taskSummary.status)}</strong>
            </div>
            <div>
                <span>AI 用量</span>
                <strong>{usageSummaryLabel(taskSummary.usageSummary)}</strong>
            </div>
        </section>

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
  return "待生成";
}

function generationModeTone(generationMode: string, fallbackReason: string): StatusTone {
  if (fallbackReason || generationMode === "rule_based_fallback") {
    return "warning";
  }
  if (generationMode === "ai_pipeline") {
    return "success";
  }
  if (generationMode === "rule_based_scaffold") {
    return "neutral";
  }
  return "neutral";
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

function taskStatusLabel(status: TaskStatus) {
    if (status === "ready") {
        return "可生成";
    }
    if (status === "running") {
        return "运行中";
    }
    if (status === "success") {
        return "已完成";
    }
    if (status === "failed") {
        return "失败";
    }
    if (status === "canceled") {
        return "已停止";
    }
    return "待输入";
}

function taskStatusTone(status: TaskStatus): StatusTone {
    if (status === "running") {
        return "active";
    }
    if (status === "success") {
        return "success";
    }
    if (status === "failed") {
        return "danger";
    }
    if (status === "canceled" || status === "ready") {
        return "warning";
    }
    return "neutral";
}
